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
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ReleaseStockServiceTest {

    @MockK
    lateinit var reservationRepository: ReservationRepository

    @MockK
    lateinit var timeSlotRepository: TimeSlotRepository

    @InjectMockKs
    lateinit var releaseStockService: ReleaseStockService

    @Test
    fun `releaseStock 실행 시 재고 반환 및 예약 취소가 되어야 한다`() {
        // given
        val reservationId = UUID.randomUUID()
        val timeSlotId = UUID.randomUUID()

        val timeSlot = TimeSlot(
            date = LocalDate.now().plusMonths(1),
            time = LocalTime.of(12, 0),
            stock = 8,
            id = timeSlotId,
        )

        val reservation = Reservation(
            id = reservationId,
            userId = UUID.randomUUID(),
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CANCEL_PENDING,
            cancelledAt = null,
            cancelReason = "테스트취소",
            reservationOpenedAt = LocalDateTime.now().minusHours(1),
            releaseAt = LocalDateTime.now().minusMinutes(1),
        )

        every { reservationRepository.findByIdOrNull(reservationId) } returns reservation
        every { timeSlotRepository.findByIdWithLock(timeSlotId) } returns timeSlot

        // when
        releaseStockService.releaseStock(reservationId)

        // then
        assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(timeSlot.stock).isEqualTo(10)

        verify(exactly = 1) { reservationRepository.findByIdOrNull(reservationId) }
        verify(exactly = 1) { timeSlotRepository.findByIdWithLock(timeSlotId) }
    }
}