package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@SpringBootTest
class DynamicReleaseSchedulerTest @Autowired constructor(
    private val dynamicReleaseScheduler: DynamicReleaseScheduler,
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
) {

    // 정적 스케줄러 자동실행 막기
    @MockBean
    private lateinit var staticReleaseScheduler: StaticReleaseScheduler

    @AfterEach
    fun cleanUp() {
        reservationRepository.deleteAllInBatch()
        timeSlotRepository.deleteAllInBatch()
    }

    @Test
    fun `동적 스케줄러는 releaseAt 이후 재고를 복구해야 한다`() {
        // given
        val userId = UUID.randomUUID()

        val timeSlot = createTimeSlot(
            stock = 8,
            date = LocalDate.now().plusMonths(1),
            time = LocalTime.of(12, 0),
        )

        val reservation = createReservation(
            userId = userId,
            timeSlot = timeSlot,
            guestCount = 2,
            status = ReservationStatus.CANCEL_PENDING,
            cancelledAt = null,
            cancelReason = "테스트취소",
            reservationOpenedAt = LocalDateTime.now().minusHours(1),
            releaseAt = LocalDateTime.now().minusMinutes(1),
        )

        // when
        dynamicReleaseScheduler.releaseStock(reservation.id)

        // then
        val result = reservationRepository.findById(reservation.id).orElseThrow()
        val updatedSlot = timeSlotRepository.findById(timeSlot.id).orElseThrow()

        assertThat(result.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(updatedSlot.stock).isEqualTo(10)
    }

    private fun createTimeSlot(
        stock: Int,
        date: LocalDate,
        time: LocalTime,
    ): TimeSlot {
        val timeSlot = TimeSlot.builder()
            .date(date)
            .time(time)
            .stock(stock)
            .build()

        return timeSlotRepository.save(timeSlot)
    }

    private fun createReservation(
        userId: UUID,
        timeSlot: TimeSlot,
        guestCount: Int,
        status: ReservationStatus,
        cancelledAt: LocalDateTime?,
        cancelReason: String,
        reservationOpenedAt: LocalDateTime,
        releaseAt: LocalDateTime,
    ): Reservation {
        val reservation = Reservation.builder()
            .userId(userId)
            .timeSlot(timeSlot)
            .guestCount(guestCount)
            .status(status)
            .cancelledAt(cancelledAt)
            .cancelReason(cancelReason)
            .reservationOpenedAt(reservationOpenedAt)
            .releaseAt(releaseAt)
            .build()

        return reservationRepository.save(reservation)
    }
}