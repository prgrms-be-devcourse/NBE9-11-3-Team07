package com.back.mozu.domain.queue.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val LOCK_TTL_SECONDS = 10L
private const val WATCHDOG_INTERVAL_MS = 3_000L
private const val MAX_WATCHDOG_EXTENSIONS = 5

@Service
class LockService(
    private val redisTemplate: StringRedisTemplate,
) {
    private val activeLocks = ConcurrentHashMap<String, Pair<String, Int>>()

    fun acquireLock(lockKey: String, lockToken: String): Boolean {
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockToken, Duration.ofSeconds(LOCK_TTL_SECONDS))
            ?: false

        if (acquired) {
            activeLocks[lockKey] = Pair(lockToken, 0)
        }
        return acquired
    }

    fun releaseLock(lockKey: String, lockToken: String) {
        val currentToken = redisTemplate.opsForValue().get(lockKey)
        if (currentToken == lockToken) {
            redisTemplate.delete(lockKey)
        }
        activeLocks.remove(lockKey)
    }

    @Scheduled(fixedDelay = WATCHDOG_INTERVAL_MS)
    fun renewActiveLocks() {
        activeLocks.forEach { (lockKey, tokenAndCount) ->
            val (lockToken, extensionCount) = tokenAndCount

            if (extensionCount >= MAX_WATCHDOG_EXTENSIONS) {
                log.warn(
                    "Watchdog 최대 연장 횟수 초과. Lock 강제 만료 예정: lockKey={}, extensionCount={}",
                    lockKey, extensionCount,
                )
                activeLocks.remove(lockKey)
                return@forEach
            }

            val currentToken = redisTemplate.opsForValue().get(lockKey)
            if (currentToken == lockToken) {
                redisTemplate.expire(lockKey, Duration.ofSeconds(LOCK_TTL_SECONDS))
                activeLocks[lockKey] = Pair(lockToken, extensionCount + 1)
                log.debug(
                    "Lock TTL 연장: lockKey={}, extensionCount={}/{}",
                    lockKey, extensionCount + 1, MAX_WATCHDOG_EXTENSIONS,
                )
            } else {
                activeLocks.remove(lockKey)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LockService::class.java)
    }
}