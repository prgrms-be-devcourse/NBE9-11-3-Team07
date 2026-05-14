package com.back.mozu.domain.queue.service

import com.back.mozu.global.redis.RedisUtil
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class LockServiceTest {

    @MockK lateinit var redisUtil: RedisUtil

    @InjectMockKs lateinit var lockService: LockService

    @Test
    fun `락 획득 성공 후 해제하면 동일 슬롯에 재획득이 가능하다`() {
        // given
        val timeSlotId = "test-slot-1"
        val token = UUID.randomUUID().toString()
        val lockKey = RedisUtil.lockKey(timeSlotId)

        every { redisUtil.lockAcquire(lockKey, token, 3000L) } returns true
        justRun { redisUtil.lockRelease(lockKey, token) }

        // when
        val firstAcquire = lockService.acquireLock(timeSlotId, token)
        lockService.releaseLock(timeSlotId, token)
        val secondAcquire = lockService.acquireLock(timeSlotId, token)

        // then
        assertThat(firstAcquire).isTrue()
        assertThat(secondAcquire).isTrue()
        verify(exactly = 2) { redisUtil.lockAcquire(lockKey, token, 3000L) }
        verify(exactly = 1) { redisUtil.lockRelease(lockKey, token) }
    }

    @Test
    fun `락을 보유한 동안 다른 사용자의 획득 시도는 실패한다`() {
        // given
        val timeSlotId = "test-slot-2"
        val token = UUID.randomUUID().toString()
        val otherToken = UUID.randomUUID().toString()
        val lockKey = RedisUtil.lockKey(timeSlotId)

        every { redisUtil.lockAcquire(lockKey, token, 3000L) } returns true
        every { redisUtil.lockAcquire(lockKey, otherToken, 3000L) } returns false

        // when
        val result = lockService.acquireLock(timeSlotId, token)
        val otherResult = lockService.acquireLock(timeSlotId, otherToken)

        // then
        assertThat(result).isTrue()
        assertThat(otherResult).isFalse()
    }

    @Test
    fun `잘못된 토큰으로 락 해제를 시도해도 원래 소유자의 락은 유지된다`() {
        // given
        val timeSlotId = "test-slot-3"
        val token = UUID.randomUUID().toString()
        val otherToken = UUID.randomUUID().toString()
        val lockKey = RedisUtil.lockKey(timeSlotId)

        every { redisUtil.lockAcquire(lockKey, token, 3000L) } returns true
        every { redisUtil.lockAcquire(lockKey, otherToken, 3000L) } returnsMany listOf(false, true)
        justRun { redisUtil.lockRelease(lockKey, any()) }

        // when
        lockService.acquireLock(timeSlotId, token)
        lockService.releaseLock(timeSlotId, otherToken)                    // 잘못된 토큰으로 해제 시도
        val stillLocked = lockService.acquireLock(timeSlotId, otherToken)  // 락 아직 유지 중 → 실패
        lockService.releaseLock(timeSlotId, token)                         // 올바른 토큰으로 해제
        val nowUnlocked = lockService.acquireLock(timeSlotId, otherToken)  // 해제 후 → 성공

        // then
        assertThat(stillLocked).isFalse()
        assertThat(nowUnlocked).isTrue()
    }
}
