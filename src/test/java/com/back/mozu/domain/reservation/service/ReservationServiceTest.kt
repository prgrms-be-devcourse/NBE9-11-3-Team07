package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.service.CustomerService
import com.back.mozu.domain.reservation.dto.ReservationDto
import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.service.spi.ServiceException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
@DisplayName("ReservationService 단위 테스트")
class ReservationServiceTest {
    private lateinit var reservationRepository: ReservationRepository
    private lateinit var timeSlotRepository: TimeSlotRepository
    private lateinit var releaseScheduler: ReleaseScheduler
    private lateinit var customerService: CustomerService
    private lateinit var reservationService: ReservationService

    @BeforeEach
    fun setUp() {
        reservationRepository = mockk()
        timeSlotRepository = mockk()
        releaseScheduler = mockk()
        customerService = mockk()

        reservationService = ReservationService(
            reservationRepository,
            timeSlotRepository,
            releaseScheduler,
            customerService,
        )
    }

    @Test
    fun `일반 취소 시 즉시 반환되어 CANCELED 상태가 되어야 한다`() {
        // given
        val userId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val timeSlot = createTimeSlotEntity(LocalDate.now().plusMonths(1), LocalTime.NOON, 7)
        val reservation = createReservationEntity(
            reservationId = reservationId,
            userId = userId,
            timeSlot = timeSlot,
            guestCount = 3,
            status = ReservationStatus.CONFIRMED,
            reservationOpenedAt = null,
            createdAt = LocalDateTime.now().minusDays(1),
            releaseAt = null,
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
        every { reservationRepository.countByUserIdAndStatusAndCancelledAtAfter(any(), any(), any()) } returns 0
        every { timeSlotRepository.findByIdWithLock(timeSlot.id ?: error("timeSlot id is null")) } returns timeSlot

        // when
        val result = reservationService.cancelMyReservation(userId, reservationId, "단순 변심")

        // then
        assertThat(result.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(timeSlot.stock).isEqualTo(10)
        verify(exactly = 0) { releaseScheduler.schedule(any()) }
    }

    @Test
    fun `오픈 직후 30분 이내 취소면 CANCEL_PENDING 상태가 되어야 한다`() {
        // given
        val userId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val timeSlot = createTimeSlotEntity(LocalDate.now().plusMonths(1), LocalTime.NOON, 7)
        val reservation = createReservationEntity(
            reservationId = reservationId,
            userId = userId,
            timeSlot = timeSlot,
            guestCount = 3,
            status = ReservationStatus.CONFIRMED,
            reservationOpenedAt = LocalDateTime.now().minusMinutes(10),
            createdAt = LocalDateTime.now().minusDays(1),
            releaseAt = null,
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
        every { reservationRepository.countByUserIdAndStatusAndCancelledAtAfter(any(), any(), any()) } returns 0
        every { releaseScheduler.schedule(any()) } just runs

        // when
        val result = reservationService.cancelMyReservation(userId, reservationId, "빠른 취소")

        // then
        assertThat(result.status).isEqualTo(ReservationStatus.CANCEL_PENDING)
        assertThat(reservation.releaseAt).isNotNull()
        verify(exactly = 1) { releaseScheduler.schedule(reservation) }
    }

    @Test
    fun `예약이 존재하지 않으면 예외가 발생해야 한다`() {
        // given
        val userId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        every { reservationRepository.findById(reservationId) } returns Optional.empty()

        // when & then
        assertThatThrownBy {
            reservationService.cancelMyReservation(userId, reservationId, "없는 예약")
        }.isInstanceOf(ServiceException::class.java)
    }

    @Test
    fun `예약 소유자가 아니면 예외가 발생해야 한다`() {
        // given
        val ownerId = UUID.randomUUID()
        val anotherUserId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val timeSlot = createTimeSlotEntity(LocalDate.now().plusMonths(1), LocalTime.NOON, 7)
        val reservation = createReservationEntity(
            reservationId = reservationId,
            userId = ownerId,
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CONFIRMED,
            reservationOpenedAt = null,
            createdAt = LocalDateTime.now(),
            releaseAt = null,
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)

        // when & then
        assertThatThrownBy {
            reservationService.cancelMyReservation(anotherUserId, reservationId, "남의 예약 취소")
        }.isInstanceOf(ServiceException::class.java)
    }

    @Test
    fun `24시간 이내 취소면 패널티가 적용되고 사유가 LATE_CANCEL 이어야 한다`() {
        // given
        val userId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val timeSlot = createTimeSlotEntity(LocalDate.now().plusMonths(1), LocalTime.NOON, 7)
        val reservation = createReservationEntity(
            reservationId = reservationId,
            userId = userId,
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CONFIRMED,
            reservationOpenedAt = null,
            createdAt = LocalDateTime.now(),
            releaseAt = null,
        )
        val customer = mockk<Customer>(relaxed = true)

        every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
        every { reservationRepository.countByUserIdAndStatusAndCancelledAtAfter(any(), any(), any()) } returns 0
        every { timeSlotRepository.findByIdWithLock(timeSlot.id ?: error("timeSlot id is null")) } returns timeSlot
        every { customerService.findById(userId) } returns Optional.of(customer)
        every { customer.applyPenaltyUntil(any()) } just runs

        // when
        val result: ReservationDto.Response = reservationService.cancelMyReservation(userId, reservationId, "개인사유")

        // then
        assertThat(result.cancelReason).isEqualTo("LATE_CANCEL")
        verify(exactly = 1) { customer.applyPenaltyUntil(any()) }
    }

    private fun createTimeSlotEntity(date: LocalDate, time: LocalTime, stock: Int): TimeSlot =
        TimeSlot(
            id = UUID.randomUUID(),
            date = date,
            time = time,
            stock = stock,
            version = 0
        )

    private fun createReservationEntity(
        reservationId: UUID,
        userId: UUID,
        timeSlot: TimeSlot,
        guestCount: Int,
        status: ReservationStatus,
        reservationOpenedAt: LocalDateTime?,
        createdAt: LocalDateTime,
        releaseAt: LocalDateTime?,
    ): Reservation =
        Reservation(
            id = reservationId,
            userId = userId,
            timeSlot = timeSlot,
            guestCount = guestCount,
            status = status,
            reservationOpenedAt = reservationOpenedAt,
            createdAt = createdAt,
            releaseAt = releaseAt
        )
}