package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
@DisplayName("ReservationAsyncProcessor 단위 테스트")
class ReservationAsyncProcessorTest {

    @MockK
    lateinit var reservationRepository: ReservationRepository

    @MockK
    lateinit var timeSlotRepository: TimeSlotRepository

    @MockK
    lateinit var redissonClient: RedissonClient

    @MockK
    lateinit var rLock: RLock

    @MockK
    lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK
    lateinit var valueOps: ValueOperations<String, String>

    @MockK
    lateinit var reservationStatusService: ReservationStatusService

    @InjectMockKs
    lateinit var processor: ReservationAsyncProcessor

    private val reservationId = UUID.randomUUID()
    private val timeSlotId = UUID.randomUUID()
    private val guestCount = 2
    private val timeSlotIdStr = timeSlotId.toString()
    private val lockKey = "lock:timeslot:$timeSlotIdStr"
    private val occupiedKey = "isOccupied:$timeSlotIdStr"

    private fun makeReservation(status: ReservationStatus = ReservationStatus.PENDING) =
        Reservation(
            id = reservationId,
            userId = UUID.randomUUID(),
            guestCount = guestCount,
            status = status,
            createdAt = LocalDateTime.now(),
        )

    private fun makeTimeSlot(stock: Int = 10) =
        TimeSlot(
            id = timeSlotId,
            date = LocalDate.now(),
            time = LocalTime.of(12, 0),
            stock = stock,
        )

    @BeforeEach
    fun setUp() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { redissonClient.getLock(lockKey) } returns rLock

        every { rLock.tryLock(any(), any(), any()) } returns true
        every { rLock.isLocked } returns true
        every { rLock.isHeldByCurrentThread } returns true
        every { rLock.unlock() } just Runs

        every { reservationStatusService.cancelReservationSafely(any(), any()) } just Runs

        TransactionSynchronizationManager.initSynchronization()
    }

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.clear()
    }

    @Nested
    @DisplayName("Redis Fail-Fast")
    inner class FailFast {

        @Test
        @DisplayName("isOccupied가 true일 경우 락 획득 없이 예약을 즉시 취소")
        fun `만석 플래그 존재 시 즉시 취소`() {
            every { valueOps.get(occupiedKey) } returns "true"

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) {
                reservationStatusService.cancelReservationSafely(reservationId, "ALREADY_OCCUPIED_FAIL_FAST")
            }
            verify(exactly = 0) { rLock.tryLock(any(), any(), any()) }
        }

        @Test
        @DisplayName("isOccupied가 false/null일 경우 Fail-Fast를 통과하여 락 획득 시도")
        fun `만석 플래그 없을 때 Fail-Fast 통과`() {
            every { valueOps.get(occupiedKey) } returns null
            every { rLock.tryLock(5, -1, TimeUnit.SECONDS) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify { rLock.tryLock(5, -1, TimeUnit.SECONDS) }
        }
    }

    @Nested
    @DisplayName("Lock 획득 실패")
    inner class LockFail {

        @Test
        @DisplayName("락 획득 실패 시 예약 취소 서비스 호출")
        fun `락 획득 실패 시 예약 취소`() {
            every { valueOps.get(occupiedKey) } returns null
            every { rLock.tryLock(5, -1, TimeUnit.SECONDS) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) {
                reservationStatusService.cancelReservationSafely(reservationId, "LOCK_ACQUIRE_FAIL")
            }
        }
    }

    @Nested
    @DisplayName("정상 예약 처리")
    inner class HappyPath {

        @Test
        @DisplayName("정상 흐름에서 예약 상태가 CONFIRMED로 변경")
        fun `정상 흐름 예약 확정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = 10)
            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(timeSlot.stock).isEqualTo(8)
        }

        @Test
        @DisplayName("재고 소진 시 Redis에 isOccupied=true를 설정")
        fun `재고 소진 시 만석 플래그 설정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = guestCount)
            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { valueOps.set(occupiedKey, "true") } returns Unit

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(0)
            verify(exactly = 1) { valueOps.set(occupiedKey, "true") }
        }
    }

    @Nested
    @DisplayName("예외 처리")
    inner class ExceptionHandling {

        @Test
        @DisplayName("ObjectOptimisticLockingFailureException 발생 시 OPTIMISTIC_LOCK_FAIL로 취소")
        fun `낙관적 락 충돌 시 예약 취소`() {
            val reservation = makeReservation()
            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } throws ObjectOptimisticLockingFailureException(TimeSlot::class.java, timeSlotId)

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) {
                reservationStatusService.cancelReservationSafely(reservationId, "OPTIMISTIC_LOCK_FAIL")
            }
        }

        @Test
        @DisplayName("예약 Entity가 없을 경우 IllegalArgumentException으로 RESERVATION_FAILED 처리")
        fun `예약 Entity 없음 시 취소`() {
            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns null

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) {
                reservationStatusService.cancelReservationSafely(reservationId, "RESERVATION_FAILED")
            }
        }

        @Test
        @DisplayName("재고 점유 이후 IllegalStateException 발생 시 release 호출 및 Redis 상태 초기화")
        fun `비즈니스 예외 시 롤백 및 Redis 복구`() {
            val reservation = mockk<Reservation>(relaxed = true) {
                every { status } returns ReservationStatus.PENDING
            }
            val timeSlot = mockk<TimeSlot>(relaxed = true)

            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            every { timeSlot.occupy(guestCount) } just Runs
            every { timeSlot.release(guestCount) } just Runs

            every { reservation.confirmReservation() } throws IllegalStateException()
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) { timeSlot.release(guestCount) }
            verify(exactly = 1) { reservationStatusService.cancelReservationSafely(reservationId, "RESERVATION_FAILED") }
            verify(exactly = 1) { redisTemplate.delete(occupiedKey) }
        }

        @Test
        @DisplayName("시스템 예외 발생 시 SYSTEM_ERROR 취소 및 재고 복구")
        fun `시스템 예외 시 예약 취소 및 재고 복구`() {
            val reservation = mockk<Reservation>(relaxed = true) {
                every { status } returns ReservationStatus.PENDING
            }
            val timeSlot = mockk<TimeSlot>(relaxed = true)

            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            every { timeSlot.occupy(guestCount) } just Runs
            every { timeSlot.release(guestCount) } just Runs

            every { reservation.confirmReservation() } throws RuntimeException("DB Connection Timeout")
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 1) { timeSlot.release(guestCount) }
            verify(exactly = 1) { reservationStatusService.cancelReservationSafely(reservationId, "SYSTEM_ERROR") }
        }
    }

    @Nested
    @DisplayName("Lock 해제")
    inner class LockRelease {

        @Test
        @DisplayName("락 획득에 성공한 경우 트랜잭션 콜백을 통해 Redisson unlock이 호출됨")
        fun `정상 흐름에서 락 해제 콜백 동작`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot()
            every { valueOps.get(occupiedKey) } returns null
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { rLock.unlock() }

            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            }

            verify(exactly = 1) { rLock.unlock() }
        }

        @Test
        @DisplayName("락 획득 실패 시 동기화 콜백이 등록되지 않으며 unlock이 호출되지 않음")
        fun `락 획득 실패 시 unlock 미호출 및 콜백 미등록`() {
            every { valueOps.get(occupiedKey) } returns null
            every { rLock.tryLock(any(), any(), any()) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
            verify(exactly = 0) { rLock.unlock() }
        }
    }
}