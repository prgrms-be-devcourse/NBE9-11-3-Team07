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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
class DynamicReleaseSchedulerTest {

    @MockK
    lateinit var taskScheduler: TaskScheduler

    @MockK
    lateinit var reservationRepository: ReservationRepository

    @MockK
    lateinit var timeSlotRepository: TimeSlotRepository

    @MockK
    private lateinit var releaseStockService: ReleaseStockService

    @InjectMockKs
    lateinit var dynamicReleaseScheduler: DynamicReleaseScheduler

    @Test
    fun `동적 스케줄러는 releaseAt 이후 재고를 복구해야 한다`() {
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

        every {
            reservationRepository.findAllByStatus(ReservationStatus.CANCEL_PENDING)
        } returns listOf(reservation)
        every {
            taskScheduler.schedule(any(), any<Instant>())
        } returns mockk()
        every {
            releaseStockService.releaseStock(any())
        } just Runs

        // when
        dynamicReleaseScheduler.run(mockk())

        // then
        verify(exactly = 1) { taskScheduler.schedule(any(), any<Instant>()) }
    }
}