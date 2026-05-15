package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class DynamicReleaseSchedulerTest {

    @MockK
    lateinit var reservationRepository: ReservationRepository

    @MockK
    lateinit var timeSlotRepository: TimeSlotRepository

    @InjectMockKs
    lateinit var dynamicReleaseScheduler: DynamicReleaseScheduler

    @Test
    fun `동적 스케줄러는 releaseAt 이후 재고를 복구해야 한다`() {
        // given
        val reservationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val timeSlotId = UUID.randomUUID()

        val timeSlot = TimeSlot(
            date = LocalDate.now().plusMonths(1),
            time = LocalTime.of(12, 0),
            stock = 8,
            id = timeSlotId,
        )

        val reservation = Reservation(
            id = reservationId,
            userId = userId,
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CANCEL_PENDING,
            cancelledAt = null,
            cancelReason = "테스트취소",
            reservationOpenedAt = LocalDateTime.now().minusHours(1),
            releaseAt = LocalDateTime.now().minusMinutes(1),
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
        every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns Optional.of(timeSlot)

        // when
        dynamicReleaseScheduler.releaseStock(reservationId)

        // then
        assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(timeSlot.stock).isEqualTo(10)

        verify(exactly = 1) { reservationRepository.findById(reservationId) }
        verify(exactly = 1) { timeSlotRepository.findByIdWithLock(timeSlotId) }
    }
}
