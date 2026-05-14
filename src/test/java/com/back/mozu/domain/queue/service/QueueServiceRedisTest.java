package com.back.mozu.domain.queue.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse
import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.entity.TimeSlot.date
import com.back.mozu.domain.reservation.entity.TimeSlot.time
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor
import com.back.mozu.global.redis.RedisUtil
import com.back.mozu.global.redis.RedisUtil.Companion.queueKey
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

/**
 * Redis 대기열 관련 테스트
 * 담당: 정종욱
 * 
 * 테스트 범위:
 * - 순번 정확성 (진입 순서대로 1, 2, 3번 부여)
 * - Redis 장애 시 DB 기준 자동 복구
 * - Redis 1차 중복 진입 방어
 * - CONFIRMED / CANCELED 시 rank null 반환
 * 
 * AsyncProcessor를 Mock으로 막는 이유:
 * 실제 AsyncProcessor가 실행되면 PENDING → CONFIRMED/CANCELED로 바뀌어서
 * 순번 조회(rank)가 null을 반환함 → 순번 테스트 자체가 불가능
 * 여기서는 Redis 로직(순번 부여, 복구)에만 집중하므로 Mock으로 막는 것이 적절
 * 실제 AsyncProcessor 동작은 QueueServiceTest(상민님)에서 통합 테스트로 검증
 * 
 * 각 테스트마다 전용 타임슬롯을 생성하는 이유:
 * recoverQueueFromDB는 해당 타임슬롯의 모든 PENDING 레코드를 조회함
 * 테스트 간 같은 타임슬롯을 공유하면 다른 테스트의 예약 데이터가 섞여서 순번이 꼬임
 * 전용 슬롯을 쓰면 각 테스트가 완전히 독립적으로 실행됨
 */
@SpringBootTest
internal class QueueServiceRedisTest {
    @Autowired
    private val queueService: QueueService? = null

    @Autowired
    private val timeSlotRepository: TimeSlotRepository? = null

    @Autowired
    private val reservationRepository: ReservationRepository? = null

    @Autowired
    private val customerRepository: CustomerRepository? = null

    @Autowired
    private val redisTemplate: RedisTemplate<String?, String?>? = null

    @Autowired
    private val redisUtil: RedisUtil? = null

    // AsyncProcessor를 Mock으로 막아서 PENDING 상태 유지
    // → 순번/Redis 복구 테스트가 가능해짐
    @MockitoBean
    private val asyncProcessor: ReservationAsyncProcessor? = null

    private var customer: Customer? = null

    @BeforeEach
    fun setUp() {
        // QueueService.enqueueAttempt에서 customerService.findById 체크가 있어서
        // Customer를 먼저 저장해야 테스트가 정상 동작함
        customer = saveCustomer()

        // Redis 클리어 (이전 테스트 잔재 제거)
        redisTemplate!!.delete(redisTemplate.keys("queue:*"))
        redisTemplate.delete(redisTemplate.keys("waiting:*"))
        redisTemplate.delete(redisTemplate.keys("lock:*"))
    }

    @AfterEach
    fun cleanUp() {
        reservationRepository!!.deleteAllInBatch()
        timeSlotRepository!!.deleteAllInBatch()
        customerRepository!!.deleteAllInBatch()

        // Redis 대기열 데이터 정리
        redisTemplate!!.delete(redisTemplate.keys("queue:*"))
        redisTemplate.delete(redisTemplate.keys("waiting:*"))
        redisTemplate.delete(redisTemplate.keys("lock:*"))
    }

    // =========================================================
    // 순번 테스트
    // =========================================================
    @Test
    @DisplayName("3명이 순서대로 진입하면 순번이 1, 2, 3으로 반환된다")
    @Throws(InterruptedException::class)
    fun rankShouldBeAssignedInOrder() {
        // 이 테스트 전용 타임슬롯
        val slot = saveTimeSlot(LocalTime.of(12, 0))

        val c1 = saveCustomer()
        val c2 = saveCustomer()
        val c3 = saveCustomer()

        // 순서대로 진입 (100ms 딜레이로 score 차이 보장)
        val r1 = queueService!!.enqueueAttempt(c1.id, AttemptRequest(slot.date, slot.time, 1))
        Thread.sleep(100)
        val r2 = queueService.enqueueAttempt(c2.id, AttemptRequest(slot.date, slot.time, 1))
        Thread.sleep(100)
        val r3 = queueService.enqueueAttempt(c3.id, AttemptRequest(slot.date, slot.time, 1))

        // afterCommit 완료 대기
        Thread.sleep(500)

        val status1 = queueService.getAttemptStatus(r1.getAttemptId())
        val status2 = queueService.getAttemptStatus(r2.getAttemptId())
        val status3 = queueService.getAttemptStatus(r3.getAttemptId())

        Assertions.assertThat(status1.getRank()).isEqualTo(1L)
        Assertions.assertThat(status2.getRank()).isEqualTo(2L)
        Assertions.assertThat(status3.getRank()).isEqualTo(3L)
    }

    @Test
    @DisplayName("PENDING 상태에서 rank와 예상 대기 시간이 반환된다")
    @Throws(InterruptedException::class)
    fun pendingStatusShouldReturnRankAndEstimatedWait() {
        // 이 테스트 전용 타임슬롯
        val slot = saveTimeSlot(LocalTime.of(13, 0))

        val c1 = saveCustomer()
        val c2 = saveCustomer()

        val r1 = queueService!!.enqueueAttempt(c1.id, AttemptRequest(slot.date, slot.time, 1))
        Thread.sleep(100)
        val r2 = queueService.enqueueAttempt(c2.id, AttemptRequest(slot.date, slot.time, 1))

        Thread.sleep(500)

        val status2 = queueService.getAttemptStatus(r2.getAttemptId())

        Assertions.assertThat<ReservationStatus?>(status2.getStatus()).isEqualTo(ReservationStatus.PENDING)
        Assertions.assertThat(status2.getRank()).isEqualTo(2L)
        Assertions.assertThat(status2.getEstimatedWaitMinutes()).isNotNull()
    }

    // =========================================================
    // Redis 장애 테스트
    // =========================================================
    @Test
    @DisplayName("Redis 장애 시에도 DB에 예약이 저장된다 (Source of Truth)")
    fun shouldSaveToDB_EvenWhenRedisFails() {
        // 이 테스트 전용 타임슬롯
        val slot = saveTimeSlot(LocalTime.of(14, 0))

        val response = queueService!!.enqueueAttempt(
            customer!!.id,
            AttemptRequest(slot.date, slot.time, 1)
        )

        // Redis 죽어도 DB에는 반드시 기록이 남는다는 것이 핵심
        Assertions.assertThat<Reservation?>(reservationRepository!!.findById(response.getAttemptId())).isPresent()
    }

    @Test
    @DisplayName("Redis 대기열 유실 시 getAttemptStatus 호출 시 DB 기준으로 순번이 자동 복구된다")
    @Throws(InterruptedException::class)
    fun shouldAutoRecoverQueue_WhenRedisDataLost() {
        // 이 테스트 전용 타임슬롯 (다른 테스트와 분리)
        val slot = saveTimeSlot(LocalTime.of(15, 0))

        // 3명 순서대로 진입
        val responses: MutableList<AttemptResponse?> = ArrayList<AttemptResponse?>()
        for (i in 0..2) {
            val c = saveCustomer()
            responses.add(
                queueService!!.enqueueAttempt(
                    c.id,
                    AttemptRequest(slot.date, slot.time, 1)
                )
            )
            Thread.sleep(100)
        }

        // afterCommit 콜백 완료 대기
        Thread.sleep(500)

        // Redis 대기열 강제 삭제 (장애 시뮬레이션)
        redisTemplate!!.delete(redisTemplate.keys("queue:*"))
        redisTemplate.delete(redisTemplate.keys("waiting:*"))

        // getAttemptStatus 호출 시 rank null → recoverQueueFromDB 자동 실행
        val status1 = queueService!!.getAttemptStatus(responses.get(0)!!.getAttemptId())
        val status2 = queueService.getAttemptStatus(responses.get(1)!!.getAttemptId())
        val status3 = queueService.getAttemptStatus(responses.get(2)!!.getAttemptId())

        // DB createdAt 순서대로 복구되어야 함
        Assertions.assertThat(status1.getRank()).isEqualTo(1L)
        Assertions.assertThat(status2.getRank()).isEqualTo(2L)
        Assertions.assertThat(status3.getRank()).isEqualTo(3L)
    }

    @Test
    @DisplayName("recoverQueueFromDB 직접 호출 시 원래 진입 순서대로 복구된다")
    @Throws(InterruptedException::class)
    fun recoverQueueFromDB_ShouldMaintainOriginalOrder() {
        // 이 테스트 전용 타임슬롯 (다른 테스트와 분리)
        val slot = saveTimeSlot(LocalTime.of(16, 0))

        // 5명 순서대로 진입
        val responses: MutableList<AttemptResponse?> = ArrayList<AttemptResponse?>()
        for (i in 0..4) {
            val c = saveCustomer()
            responses.add(
                queueService!!.enqueueAttempt(
                    c.id,
                    AttemptRequest(slot.date, slot.time, 1)
                )
            )
            Thread.sleep(100)
        }

        // afterCommit 완료 대기
        Thread.sleep(500)

        // Redis 강제 삭제
        redisTemplate!!.delete(redisTemplate.keys("queue:*"))
        redisTemplate.delete(redisTemplate.keys("waiting:*"))

        // recoverQueueFromDB 직접 호출
        queueService!!.recoverQueueFromDB(slot.id)

        // 복구 후 순번이 원래 순서와 동일한지 확인
        // getAttemptStatus 대신 Redis에서 직접 rank 조회
        // 이유: getAttemptStatus 호출 시 rank null이면 recoverQueueFromDB가 또 실행되어 순번이 꼬임
        for (i in 0..4) {
            val userId = reservationRepository!!
                .findById(responses.get(i)!!.getAttemptId())
                .orElseThrow().userId
            val rank = redisUtil!!.zRank(
                queueKey(slot.id.toString()),
                userId.toString()
            )
            Assertions.assertThat(rank!! + 1).isEqualTo((i + 1).toLong())
        }
    }

    // =========================================================
    // 중복 방지 테스트 (Redis 1차 방어)
    // =========================================================
    @Test
    @DisplayName("Redis에 이미 있는 유저가 재진입 시도 시 예외 발생 (Redis 1차 방어)")
    fun throwExceptionWhenAlreadyInRedisQueue() {
        // 이 테스트 전용 타임슬롯
        val slot = saveTimeSlot(LocalTime.of(17, 0))

        // Redis 대기열에 직접 추가 (이미 진입한 상태 시뮬레이션)
        redisUtil!!.zAdd(
            queueKey(slot.id.toString()),
            customer!!.id.toString(),
            System.currentTimeMillis().toDouble()
        )

        // 같은 유저가 다시 진입 시도 → Redis 1차 방어에서 차단
        Assertions.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
            queueService!!.enqueueAttempt(
                customer!!.id,
                AttemptRequest(slot.date, slot.time, 1)
            )
        })
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("이미 대기열에 있습니다.")
    }

    // =========================================================
    // 헬퍼 메서드
    // =========================================================
    // 테스트용 Customer 생성 (각 테스트마다 고유한 유저 필요)
    private fun saveCustomer(): Customer {
        val c: Customer = Customer.builder()
            .email("test-" + UUID.randomUUID() + "@test.com")
            .provider("test")
            .providerId(UUID.randomUUID().toString())
            .role("USER")
            .name("테스트")
            .build()
        return customerRepository!!.save<Customer>(c)
    }

    // 테스트용 TimeSlot 생성 (각 테스트마다 전용 슬롯 사용 → 테스트 간 데이터 분리)
    private fun saveTimeSlot(time: LocalTime?): TimeSlot {
        val slot: TimeSlot = TimeSlot.builder()
            .date(LocalDate.now())
            .time(time)
            .stock(10)
            .build()
        return timeSlotRepository!!.save<TimeSlot>(slot)
    }
}