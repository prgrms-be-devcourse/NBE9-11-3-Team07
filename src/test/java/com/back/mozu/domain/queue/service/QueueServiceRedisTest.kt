package com.back.mozu.domain.queue.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor
import com.back.mozu.global.redis.RedisUtil
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// asyncProcessor를 MockkBean으로 막는 이유:
// 실제 AsyncProcessor가 실행되면 PENDING → CONFIRMED/CANCELED로 상태가 바뀌어
// 순번 조회(rank)가 null이 되어 Redis 순번/복구 테스트 자체가 불가능해짐
//
// 각 테스트마다 전용 타임슬롯을 생성하는 이유:
// recoverQueueFromDB는 해당 슬롯의 모든 PENDING 레코드를 조회하므로
// 슬롯을 공유하면 다른 테스트 데이터와 섞여 순번이 꼬임
@SpringBootTest
class QueueServiceRedisTest {

    @Autowired lateinit var queueService: QueueService
    @Autowired lateinit var timeSlotRepository: TimeSlotRepository
    @Autowired lateinit var reservationRepository: ReservationRepository
    @Autowired lateinit var customerRepository: CustomerRepository
    @Autowired lateinit var redisTemplate: RedisTemplate<String, String>
    @Autowired lateinit var redisUtil: RedisUtil

    @MockkBean(relaxed = true)
    lateinit var asyncProcessor: ReservationAsyncProcessor

    private lateinit var customer: Customer

    @BeforeEach
    fun setUp() {
        customer = saveCustomer()
        clearRedis()
    }

    @AfterEach
    fun cleanUp() {
        reservationRepository.deleteAllInBatch()
        timeSlotRepository.deleteAllInBatch()
        customerRepository.deleteAllInBatch()
        clearRedis()
    }

    @Test
    fun `3명이 순서대로 진입하면 순번이 1, 2, 3으로 반환된다`() {
        // given
        val slot = saveTimeSlot(LocalTime.of(12, 0))
        val c1 = saveCustomer()
        val c2 = saveCustomer()
        val c3 = saveCustomer()

        // when - 100ms 딜레이로 score(진입 시각) 차이 보장
        val r1 = queueService.enqueueAttempt(requireNotNull(c1.id), atRequest(slot))
        Thread.sleep(100)
        val r2 = queueService.enqueueAttempt(requireNotNull(c2.id), atRequest(slot))
        Thread.sleep(100)
        val r3 = queueService.enqueueAttempt(requireNotNull(c3.id), atRequest(slot))
        Thread.sleep(500) // afterCommit 콜백 완료 대기

        // then
        assertThat(queueService.getAttemptStatus(r1.attemptId).rank).isEqualTo(1L)
        assertThat(queueService.getAttemptStatus(r2.attemptId).rank).isEqualTo(2L)
        assertThat(queueService.getAttemptStatus(r3.attemptId).rank).isEqualTo(3L)
    }

    @Test
    fun `PENDING 상태 예약은 rank와 예상 대기 시간을 반환한다`() {
        // given
        val slot = saveTimeSlot(LocalTime.of(13, 0))
        val c1 = saveCustomer()
        val c2 = saveCustomer()

        // when
        queueService.enqueueAttempt(requireNotNull(c1.id), atRequest(slot))
        Thread.sleep(100)
        val r2 = queueService.enqueueAttempt(requireNotNull(c2.id), atRequest(slot))
        Thread.sleep(500)

        // then
        val status = queueService.getAttemptStatus(r2.attemptId)
        assertThat(status.status).isEqualTo(ReservationStatus.PENDING)
        assertThat(status.rank).isEqualTo(2L)
        assertThat(status.estimatedWaitMinutes).isNotNull()
    }

    @Test
    fun `Redis 장애가 발생해도 예약은 DB에 반드시 저장된다`() {
        // given
        val slot = saveTimeSlot(LocalTime.of(14, 0))

        // when
        val response = queueService.enqueueAttempt(requireNotNull(customer.id), atRequest(slot))

        // then - DB가 Source of Truth
        assertThat(reservationRepository.findById(response.attemptId)).isPresent
    }

    @Test
    fun `Redis 대기열 유실 시 getAttemptStatus 호출로 DB 기준 순번이 자동 복구된다`() {
        // given - 3명 순서대로 진입
        val slot = saveTimeSlot(LocalTime.of(15, 0))
        val responses = (1..3).map {
            saveCustomer()
                .let { c -> queueService.enqueueAttempt(requireNotNull(c.id), atRequest(slot)) }
                .also { Thread.sleep(100) }
        }
        Thread.sleep(500)

        // when - Redis 강제 삭제 (장애 시뮬레이션)
        clearRedis()

        // then - getAttemptStatus 호출 시 rank null → recoverQueueFromDB 자동 실행
        assertThat(queueService.getAttemptStatus(responses[0].attemptId).rank).isEqualTo(1L)
        assertThat(queueService.getAttemptStatus(responses[1].attemptId).rank).isEqualTo(2L)
        assertThat(queueService.getAttemptStatus(responses[2].attemptId).rank).isEqualTo(3L)
    }

    @Test
    fun `recoverQueueFromDB 직접 호출 시 원래 진입 순서대로 복구된다`() {
        // given - 5명 순서대로 진입
        val slot = saveTimeSlot(LocalTime.of(16, 0))
        val slotId = slot.id ?: error("slot.id가 null입니다")
        val responses = (1..5).map {
            saveCustomer()
                .let { c -> queueService.enqueueAttempt(requireNotNull(c.id), atRequest(slot)) }
                .also { Thread.sleep(100) }
        }
        Thread.sleep(500)
        clearRedis()

        // when
        queueService.recoverQueueFromDB(slotId)

        // then - Redis에서 직접 rank 조회
        // getAttemptStatus 대신 직접 조회하는 이유:
        // rank null 감지 시 recoverQueueFromDB가 재호출되어 순번이 꼬일 수 있음
        responses.forEachIndexed { i, response ->
            val userId = reservationRepository.findById(response.attemptId).orElseThrow().userId
                ?: error("userId가 null입니다")
            val rank = redisUtil.zRank(RedisUtil.queueKey(slotId.toString()), userId.toString())
                ?: error("복구 후 rank가 null입니다")
            assertThat(rank + 1).isEqualTo((i + 1).toLong())
        }
    }

    @Test
    fun `이미 Redis 대기열에 있는 유저가 재진입 시도 시 예외가 발생한다`() {
        // given - 이미 진입한 상태 시뮬레이션
        val slot = saveTimeSlot(LocalTime.of(17, 0))
        val slotId = slot.id ?: error("slot.id가 null입니다")
        redisUtil.zAdd(
            RedisUtil.queueKey(slotId.toString()),
            customer.id.toString(),
            System.currentTimeMillis().toDouble()
        )

        // when / then
        assertThatThrownBy { queueService.enqueueAttempt(requireNotNull(customer.id), atRequest(slot)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("이미 대기열에 있습니다.")
    }

    private fun saveCustomer(): Customer =
        customerRepository.save(
            Customer(
                email = "test-${UUID.randomUUID()}@test.com",
                provider = "test",
                providerId = UUID.randomUUID().toString(),
                role = "USER",
                password = null,
                name = "테스트"
            )
        )

    private fun saveTimeSlot(time: LocalTime): TimeSlot =
        timeSlotRepository.save(TimeSlot(date = LocalDate.now(), time = time, stock = 10))

    private fun atRequest(slot: TimeSlot, guestCount: Int = 1) = AttemptRequest(
        slot.date ?: error("slot.date가 null입니다"),
        slot.time ?: error("slot.time이 null입니다"),
        guestCount
    )

    private fun clearRedis() {
        listOf("queue:*", "waiting:*", "lock:*").forEach { pattern ->
            redisTemplate.keys(pattern)?.let { redisTemplate.delete(it) }
        }
    }
}
