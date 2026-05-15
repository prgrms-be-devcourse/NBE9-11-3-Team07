package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
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
@DisplayName("StaticReleaseScheduler 단위 테스트")
class StaticReleaseSchedulerTest {
    private lateinit var reservationRepository: ReservationRepository
    private lateinit var timeSlotRepository: TimeSlotRepository
    private lateinit var staticReleaseScheduler: StaticReleaseScheduler

    @BeforeEach
    fun setUp() {
        reservationRepository = mockk()
        timeSlotRepository = mockk()
        staticReleaseScheduler = StaticReleaseScheduler(reservationRepository, timeSlotRepository)
    }

    @Test
    fun `releaseAt 이후 CANCEL_PENDING 예약은 재고 복구 후 CANCELED 상태가 되어야 한다`() {
        // given
        val timeSlot = createTimeSlotEntity(LocalDate.now(), LocalTime.NOON, 8)
        val reservation = createReservationEntity(
            userId = UUID.randomUUID(),
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CANCEL_PENDING,
            cancelledAt = LocalDateTime.now().minusMinutes(2),
            cancelReason = "테스트취소",
            reservationOpenedAt = LocalDateTime.now().minusHours(1),
            createdAt = LocalDateTime.now().minusDays(1),
            releaseAt = LocalDateTime.now().minusMinutes(1),
        )

        every {
            reservationRepository.findAllByStatusAndReleaseAtBefore(
                ReservationStatus.CANCEL_PENDING,
                any(),
            )
        } returns listOf(reservation)
        every { timeSlotRepository.findByIdWithLock(timeSlot.id) } returns Optional.of(timeSlot)

        // when
        staticReleaseScheduler.processRandomRelease()

        // then
        assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(timeSlot.stock).isEqualTo(10)
        verify(exactly = 1) { timeSlotRepository.findByIdWithLock(timeSlot.id) }
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
        userId: UUID,
        timeSlot: TimeSlot,
        guestCount: Int,
        status: ReservationStatus,
        cancelledAt: LocalDateTime?,
        cancelReason: String?,
        reservationOpenedAt: LocalDateTime?,
        createdAt: LocalDateTime,
        releaseAt: LocalDateTime?,
    ): Reservation =
        Reservation(
            id = UUID.randomUUID(),
            userId = userId,
            timeSlot = timeSlot,
            guestCount = guestCount,
            status = status,
            cancelledAt = cancelledAt,
            cancelReason = cancelReason,
            reservationOpenedAt = reservationOpenedAt,
            createdAt = createdAt,
            releaseAt = releaseAt
        )
}