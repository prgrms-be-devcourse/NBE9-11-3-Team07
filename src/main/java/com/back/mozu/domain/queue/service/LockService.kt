package com.back.mozu.domain.queue.service

import com.back.mozu.global.redis.RedisUtil
import com.back.mozu.global.redis.RedisUtil.Companion.lockKey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LockService(private val redisUtil: RedisUtil) {

    fun acquireLock(timeSlotId: String, token: String): Boolean {
        val lockKey = lockKey(timeSlotId)
        val isAcquired = redisUtil.lockAcquire(lockKey, token, MAX_LOCK_TIME.toLong())

        if (isAcquired) {
            log.info("[Lock 획득 성공] timeSlotId: {}, token: {}", timeSlotId, token)
        } else {
            log.warn("[Lock 획득 실패 - 이미 점유됨] timeSlotId: {}, token: {}", timeSlotId, token)
        }

        return isAcquired
    }

    fun releaseLock(timeSlotId: String, token: String) {
        val lockKey = lockKey(timeSlotId)
        redisUtil.lockRelease(lockKey, token)
        log.info("[Lock 해제 완료] timeSlotId: {}, token: {}", timeSlotId, token)
    }

    companion object {
        private val log = LoggerFactory.getLogger(LockService::class.java)
        private const val MAX_LOCK_TIME = 3000
    }
}
