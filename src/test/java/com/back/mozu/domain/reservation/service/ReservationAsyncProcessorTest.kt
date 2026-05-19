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
import org.springframework.data.repository.findByIdOrNull
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
        every { redisTemplate.opsForValue() } returns valueOps
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
            val reservation = makeReservation()
            every { valueOps.get(occupiedKey) } returns "true"
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("ALREADY_OCCUPIED_FAIL_FAST")
            verify(exactly = 0) { lockService.acquireLock(any(), any()) }
        }

        @Test
        @DisplayName("isOccupied가 null일 경우 Fail-Fast를 통과")
        fun `만석 플래그 없을 때 Fail-Fast 통과`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false
            every { reservationRepository.findByIdOrNull(reservationId) } returns makeReservation()

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify { lockService.acquireLock(timeSlotIdStr, lockToken) }
        }
    }

    @Nested
    @DisplayName("Lock 획득 실패")
    inner class LockFail {

        @Test
        @DisplayName("락 획득 실패 시 예약 상태를 CANCELED로 변경")
        fun `락 획득 실패 시 예약 취소`() {
            val reservation = makeReservation()
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("LOCK_ACQUIRE_FAIL")
        }

        @Test
        @DisplayName("락 획득 실패 시 재고 변경 X")
        fun `락 획득 실패 시 재고 변경 없음`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false
            every { reservationRepository.findByIdOrNull(reservationId) } returns makeReservation()

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { timeSlotRepository.findByIdOrNull(timeSlotId) }
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
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot

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
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot
            every { valueOps.set(occupiedKey, "true") } returns Unit

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(0)
            verify { valueOps.set(occupiedKey, "true") }
        }

        @Test
        @DisplayName("재고가 남아 있으면 Redis 만석 플래그를 설정하지 않음")
        fun `재고 잔여 시 만석 플래그 미설정`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot(stock = 10)
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { valueOps.set(occupiedKey, "true") }
        }
    }

    @Nested
    @DisplayName("예외 처리")
    inner class ExceptionHandling {

        @Test
        @DisplayName("ObjectOptimisticLockingFailureException 발생할 경우 OPTIMISTIC_LOCK_FAIL로 취소")
        fun `낙관적 락 충돌 시 예약 취소`() {
            val reservation = makeReservation()
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } throws ObjectOptimisticLockingFailureException(TimeSlot::class.java, timeSlotId)

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("OPTIMISTIC_LOCK_FAIL")
        }

        @Test
        @DisplayName("예약 Entity가 없을 경우 IllegalArgumentException으로 RESERVATION_FAILED 처리")
        fun `예약 Entity 없음 시 취소`() {
            val reservation = makeReservation()
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returnsMany listOf(null, reservation)

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(reservation.cancelReason).isEqualTo("RESERVATION_FAILED")
        }

        @Test
        @DisplayName("재고 점유(occupy) 이후 IllegalStateException 발생 시 release를 호출하여 재고를 복구")
        fun `재고 점유 후 IllegalStateException 시 재고 복구`() {
            val reservation = mockk<Reservation>(relaxed = true)
            val timeSlot = makeTimeSlot(stock = 10)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returnsMany listOf(reservation, reservation)
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot
            every { reservation.confirmReservation() } throws IllegalStateException()
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(10)
            verify { reservation.cancelReservation("RESERVATION_FAILED") }
        }

        @Test
        @DisplayName("시스템 예외가 발생할 경우 SYSTEM_ERROR로 취소되고 점유된 재고를 복구")
        fun `시스템 예외 시 예약 취소 및 재고 복구`() {
            val reservation = mockk<Reservation>(relaxed = true)
            val timeSlot = makeTimeSlot(stock = 10)

            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returnsMany listOf(reservation, reservation)
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot
            every { reservation.confirmReservation() } throws RuntimeException("DB 연결 오류")
            every { redisTemplate.delete(occupiedKey) } returns true

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(timeSlot.stock).isEqualTo(10)
            verify { reservation.cancelReservation("SYSTEM_ERROR") }
        }
    }

    @Nested
    @DisplayName("Lock 해제")
    inner class LockRelease {

        @Test
        @DisplayName("락 획득에 성공할 경우 트랜잭션 콜백을 통해 releaseLock이 호출됨")
        fun `정상 흐름에서 락 해제 콜백 동작`() {
            val reservation = makeReservation()
            val timeSlot = makeTimeSlot()
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns true
            every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
            every { timeSlotRepository.findByIdOrNull(timeSlotId) } returns timeSlot
            every { lockService.releaseLock(any(), any()) } just Runs

            processor.processReservation(reservationId, timeSlotId, guestCount)

            verify(exactly = 0) { lockService.releaseLock(any(), any()) }

            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            }

            verify(exactly = 1) { lockService.releaseLock(timeSlotIdStr, lockToken) }
        }

        @Test
        @DisplayName("락 획득 실패 시 releaseLock 동기화 콜백이 등록되지 않음")
        fun `락 획득 실패 시 releaseLock 미호출 및 미등록`() {
            every { valueOps.get(occupiedKey) } returns null
            every { lockService.acquireLock(timeSlotIdStr, lockToken) } returns false
            every { reservationRepository.findByIdOrNull(reservationId) } returns makeReservation()

            processor.processReservation(reservationId, timeSlotId, guestCount)

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
            verify(exactly = 0) { lockService.releaseLock(any(), any()) }
        }
    }

    @Nested
    @DisplayName("cancelReservationSafely()")
    inner class CancelReservationSafely {

        @Test
        @DisplayName("cancelReservationSafely 호출할 경우 예약이 없으면 예외 없이 종료")
        fun `예약 Entity 없어도 예외 없이 종료`() {
            every { valueOps.get(occupiedKey) } returns "true"
            every { reservationRepository.findByIdOrNull(reservationId) } returns null

            processor.processReservation(reservationId, timeSlotId, guestCount)
        }
    }
}