package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
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
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
@DisplayName("ReservationAsyncProcessor 단위 테스트")
class ReservationAsyncProcessorTest {

    @MockK
    lateinit var reservationRepository: ReservationRepository

    @MockK
    lateinit var timeSlotRepository: TimeSlotRepository

    @MockK
    lateinit var lockService: LockService

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
    private val lockToken = reservationId.toString()
    private val timeSlotIdStr = timeSlotId.toString()
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear()
        }

        every { redisTemplate.opsForValue() } returns valueOps
        every { reservationStatusService.cancelReservationSafely(any(), any()) } just Runs

        TransactionSynchronizationManager.initSynchronization()
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear()
        }
    }

    @Nested
    @DisplayName("Redis Fail-Fast")
    inner class FailFast {

        @Test
        @DisplayName("isOccupied가 true이면 락 획득 없이 예약을 즉시 취소 요청한다")
        fun `isOccupied true이면 즉시 취소 요청`() {
            every { valueOps.get(occupiedKey) } returns "true"

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify {
                reservationStatusService.cancelReservationSafely(
                    reservationId,
                    "ALREADY_OCCUPIED_FAIL_FAST",
                )
            }
            verify(exactly = 0) { lockService.acquireLock(any(), any()) }
        }

        @Test
        @DisplayName("isOccupied가 null이면 Fail-Fast를 통과하고 락 획득을 시도한다")
        fun `isOccupied null이면 락 획득 시도`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify { lockService.acquireLock(timeSlotIdStr, lockToken) }
        }
    }

    @Nested
    @DisplayName("락 획득 실패")
    inner class LockFail {

        @Test
        @DisplayName("락 획득 실패 시 LOCK_ACQUIRE_FAIL로 취소 요청한다")
        fun `락 획득 실패 시 예약 취소 요청`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify {
                reservationStatusService.cancelReservationSafely(
                    reservationId,
                    "LOCK_ACQUIRE_FAIL",
                )
            }
        }

        @Test
        @DisplayName("락 획득 실패 시 타임슬롯은 조회하지 않는다")
        fun `락 획득 실패 시 타임슬롯 조회 없음`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { timeSlotRepository.findByIdWithLock(timeSlotId) }
        }
    }

    @Nested
    @DisplayName("정상 예약 처리")
    inner class HappyPath {

        @Test
        @DisplayName("정상 흐름에서는 예약 상태가 CONFIRMED로 변경된다")
        fun `정상 흐름 예약 확정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = 10)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(timeSlot.stock).isEqualTo(8)
        }

        @Test
        @DisplayName("재고가 모두 소진되면 Redis에 isOccupied=true를 설정한다")
        fun `재고 소진 시 만석 플래그 설정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = guestCount)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { valueOps.set(occupiedKey, "true") } returns Unit

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(0)
            verify { valueOps.set(occupiedKey, "true") }
        }

        @Test
        @DisplayName("재고가 남아 있으면 Redis 만석 플래그를 설정하지 않는다")
        fun `재고 남으면 만석 플래그 미설정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = 10)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { valueOps.set(occupiedKey, "true") }
        }

        @Test
        @DisplayName("재고 부족 시 INSUFFICIENT_STOCK으로 예약 취소 처리한다")
        fun `재고 부족 시 예약 취소`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = 0)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { valueOps.set(occupiedKey, "true") } returns Unit

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("INSUFFICIENT_STOCK")
            assertThat(timeSlot.stock).isEqualTo(0)
        }

        @Test
        @DisplayName("이미 처리된 예약이면 ALREADY_PROCESSED로 취소 처리한다")
        fun `이미 처리된 예약이면 취소 처리`() {
            val reservation = makeReservation(status = ReservationStatus.CONFIRMED)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("ALREADY_PROCESSED")
            verify(exactly = 0) { timeSlotRepository.findByIdWithLock(timeSlotId) }
        }
    }

    @Nested
    @DisplayName("예외 처리")
    inner class ExceptionHandling {

        @Test
        @DisplayName("ObjectOptimisticLockingFailureException 발생 시 OPTIMISTIC_LOCK_FAIL로 취소한다")
        fun `낙관적 락 충돌 시 예약 취소`() {
            val reservation = makeReservation()

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every {
                timeSlotRepository.findByIdWithLock(timeSlotId)
            } throws ObjectOptimisticLockingFailureException(TimeSlot::class.java, timeSlotId)

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("OPTIMISTIC_LOCK_FAIL")
        }

        @Test
        @DisplayName("예약 Entity가 없으면 RESERVATION_FAILED로 취소 요청한다")
        fun `예약 Entity 없음 시 취소 요청`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns null

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify {
                reservationStatusService.cancelReservationSafely(
                    reservationId,
                    "RESERVATION_FAILED",
                )
            }
        }

        @Test
        @DisplayName("occupy 이후 IllegalStateException 발생 시 release를 호출하여 재고를 복구한다")
        fun `재고 점유 후 IllegalStateException 시 재고 복구`() {
            val reservation = mockk<Reservation>(relaxed = true)
            val timeSlot = makeTimeSlot(stock = 10)

            every { reservation.status } returns ReservationStatus.PENDING
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { reservation.confirmReservation() } throws IllegalStateException()
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(10)
            verify { reservation.cancelReservation("RESERVATION_FAILED") }
        }

        @Test
        @DisplayName("시스템 예외 발생 시 SYSTEM_ERROR로 취소하고 점유된 재고를 복구한다")
        fun `시스템 예외 시 예약 취소 및 재고 복구`() {
            val reservation = mockk<Reservation>(relaxed = true)
            val timeSlot = makeTimeSlot(stock = 10)

            every { reservation.status } returns ReservationStatus.PENDING
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { reservation.confirmReservation() } throws RuntimeException("DB 연결 오류")
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(10)
            verify { reservation.cancelReservation("SYSTEM_ERROR") }
        }
    }

    @Nested
    @DisplayName("락 해제")
    inner class LockRelease {

        @Test
        @DisplayName("락 획득 성공 시 트랜잭션 완료 후 releaseLock을 호출한다")
        fun `정상 흐름에서 락 해제 콜백 동작`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot()

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdWithLock(reservationId) } returns reservation
            every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot
            every { lockService.releaseLock(any(), any()) } just Runs

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { lockService.releaseLock(any(), any()) }

            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            }

            verify(exactly = 1) { lockService.releaseLock(timeSlotIdStr, lockToken) }
        }

        @Test
        @DisplayName("락 획득 실패 시 releaseLock 콜백을 등록하지 않는다")
        fun `락 획득 실패 시 releaseLock 미호출 및 미등록`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
            verify(exactly = 0) { lockService.releaseLock(any(), any()) }
        }
    }

    @Nested
    @DisplayName("cancelReservationSafely")
    inner class CancelReservationSafely {

        @Test
        @DisplayName("Fail-Fast 취소 요청 시 예약이 없어도 예외 없이 종료된다")
        fun `예약 Entity 없어도 예외 없이 종료`() {
            every { valueOps.get(occupiedKey) } returns "true"

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify {
                reservationStatusService.cancelReservationSafely(
                    reservationId,
                    "ALREADY_OCCUPIED_FAIL_FAST",
                )
            }
        }
    }
}