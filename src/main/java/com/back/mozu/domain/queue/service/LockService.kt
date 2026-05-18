package com.back.mozu.domain.queue.service

import com.back.mozu.global.redis.RedisUtil
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class LockService(
    private val redissonClient: RedissonClient,
) {
    // 분산 락 설정
    fun acquireLock(timeSlotId: String, token: String): Boolean {
        val lockKey = RedisUtil.lockKey(timeSlotId)
        val lock = redissonClient.getLock(lockKey)
        val isAcquired = lock.tryLock(MAX_LOCK_TIME, TimeUnit.MILLISECONDS)

        if (isAcquired) {
            log.info("[Lock 획득 성공] timeSlotId: {}, token: {}", timeSlotId, token)
        } else {
            log.warn("[Lock 획득 실패 - 이미 점유됨] timeSlotId: {}, token: {}", timeSlotId, token)
        }

        return isAcquired
    }

    // 분산 락 해제
    fun releaseLock(timeSlotId: String, token: String) {
        val lockKey = RedisUtil.lockKey(timeSlotId)
        val lock = redissonClient.getLock(lockKey)

        if (lock.isHeldByCurrentThread) {
            lock.unlock()
            log.info("[Lock 해제 완료] timeSlotId: {}, token: {}", timeSlotId, token)
            return
        }

        log.warn("[Lock 해제 실패 - 현재 스레드가 소유하지 않음] timeSlotId: {}, token: {}", timeSlotId, token)
    }

    companion object {
        private val log = LoggerFactory.getLogger(LockService::class.java)

        // 락의 최대 유지 시간: 3초
        // 최대 유지 시간 내에 작업 처리 미완료 시 데드락 방지를 위해 자동으로 락이 해제
        private const val MAX_LOCK_TIME = 3_000L
    }
}