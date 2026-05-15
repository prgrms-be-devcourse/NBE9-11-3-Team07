package com.back.mozu.domain.queue.service

import com.back.mozu.global.redis.RedisUtil
import com.back.mozu.global.redis.RedisUtil.Companion.lockKey
import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service

@Slf4j
@Service
@RequiredArgsConstructor
class LockService {
    private val redisUtil: RedisUtil? = null

    // 분산 락 설정
    fun acquireLock(timeSlotId: String, token: String): Boolean {
        val lockKey = lockKey(timeSlotId)
        val isAcquired = redisUtil!!.lockAcquire(lockKey, token, MAX_LOCK_TIME.toLong())

        if (isAcquired) {
            LockService.log.info("[Lock 획득 성공] timeSlotId: {}, token: {}", timeSlotId, token)
        } else {
            LockService.log.warn("[Lock 획득 실패 - 이미 점유됨] timeSlotId: {}, token: {}", timeSlotId, token)
        }

        return isAcquired
    }

    // 분산 락 해제
    fun releaseLock(timeSlotId: String, token: String) {
        val lockKey = lockKey(timeSlotId)

        // RedisUtil 내부의 Lua 스크립트를 통해 내 토큰일 경우 삭제
        redisUtil!!.lockRelease(lockKey, token)

        LockService.log.info("[Lock 해제 완료] timeSlotId: {}, token: {}", timeSlotId, token)
    }

    companion object {
        // 락의 최대 유지 시간: 3초
        // 최대 유지 시간 내에 작업 처리 미완료 시 데드락 방지를 위해 자동으로 락이 해제
        private const val MAX_LOCK_TIME = 3000
    }
}