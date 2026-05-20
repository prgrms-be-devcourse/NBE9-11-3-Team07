package com.back.mozu.domain.queue.service


import io.mockk.every
import org.springframework.data.redis.core.ValueOperations
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.springframework.data.redis.core.StringRedisTemplate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class LockServiceTest {

    @MockK
    lateinit var redisTemplate: StringRedisTemplate

    @InjectMockKs
    lateinit var lockService: LockService

    @Test
    fun `락 획득 성공 후 해제하면 동일 슬롯에 재획득이 가능하다`() {
        // given
        val timeSlotId = "test-slot-1"
        val token = UUID.randomUUID().toString()
        val valueOps = mockk<ValueOperations<String, String>>()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.setIfAbsent(any(), any(), any()) } returns true
        every { valueOps.get(any()) } returns token
        every { redisTemplate.delete(any<String>()) } returns true

        // when
        val firstAcquire = lockService.acquireLock(timeSlotId, token)
        lockService.releaseLock(timeSlotId, token)
        val secondAcquire = lockService.acquireLock(timeSlotId, token)

        // then
        assertThat(firstAcquire).isTrue()
        assertThat(secondAcquire).isTrue()
    }

    @Test
    fun `락을 보유한 동안 다른 사용자의 획득 시도는 실패한다`() {
        // given
        val timeSlotId = "test-slot-2"
        val token = UUID.randomUUID().toString()
        val otherToken = UUID.randomUUID().toString()
        val valueOps = mockk<ValueOperations<String, String>>()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.setIfAbsent(any(), eq(token), any()) } returns true
        every { valueOps.setIfAbsent(any(), eq(otherToken), any()) } returns false

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
        val valueOps = mockk<ValueOperations<String, String>>()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.setIfAbsent(any(), eq(token), any()) } returns true
        every { valueOps.setIfAbsent(any(), eq(otherToken), any()) } returnsMany listOf(false, true)
        every { valueOps.get(any()) } returnsMany listOf(token, token, null)
        every { redisTemplate.delete(any<String>()) } returns true

        // when
        lockService.acquireLock(timeSlotId, token)
        lockService.releaseLock(timeSlotId, otherToken)
        val stillLocked = lockService.acquireLock(timeSlotId, otherToken)
        lockService.releaseLock(timeSlotId, token)
        val nowUnlocked = lockService.acquireLock(timeSlotId, otherToken)

        // then
        assertThat(stillLocked).isFalse()
        assertThat(nowUnlocked).isTrue()
    }
}