package com.back.mozu.global.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

// RedisUtil: Redis 명령어를 얇게 감싸는 인프라 계층. 비즈니스 로직 없음.
// - LockService: lockAcquire/lockRelease 조합으로 분산 락 처리
// - QueueService: zAdd/zRank/zPopMin 조합으로 대기열 처리
// 키 네이밍: "queue:{timeSlotId}" | "lock:{timeSlotId}" | "waiting:{userId}"
@Component
class RedisUtil(
    private val redisTemplate: RedisTemplate<String, String>
) {
    // score = System.currentTimeMillis() → 낮을수록 앞 순번, 동일 ms에도 UUID로 충돌 없음
    fun zAdd(key: String, value: String, score: Double) {
        redisTemplate.opsForZSet().add(key, value, score)
    }

    fun zRank(key: String, value: String): Long? =
        redisTemplate.opsForZSet().rank(key, value)

    fun zPopMin(key: String): String? =
        redisTemplate.opsForZSet().popMin(key, 1)?.firstOrNull()?.value

    fun zSize(key: String): Long? =
        redisTemplate.opsForZSet().size(key)

    fun zRemove(key: String, value: String) {
        redisTemplate.opsForZSet().remove(key, value)
    }

    fun lockAcquire(lockKey: String, token: String, ttlMillis: Long): Boolean =
        redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofMillis(ttlMillis)) == true

    // get → compare → delete를 Lua로 원자적 처리 (비원자적이면 타 서버의 락을 삭제할 위험)
    fun lockRelease(lockKey: String, token: String) {
        val luaScript =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end"
        redisTemplate.execute(
            DefaultRedisScript(luaScript, Long::class.java),
            listOf(lockKey),
            token
        )
    }

    fun set(key: String, value: String, ttl: Duration) {
        redisTemplate.opsForValue().set(key, value, ttl)
    }

    fun get(key: String): String? =
        redisTemplate.opsForValue().get(key)

    fun delete(key: String) {
        redisTemplate.delete(key)
    }

    companion object {
       fun queueKey(timeSlotId: String): String = "queue:$timeSlotId"
       fun lockKey(timeSlotId: String): String = "lock:$timeSlotId"
        fun waitingKey(userId: String): String = "waiting:$userId"
        fun generateToken(): String = UUID.randomUUID().toString()
    }
}
