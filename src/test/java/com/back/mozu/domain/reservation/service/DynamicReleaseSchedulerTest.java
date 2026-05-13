package com.back.mozu.domain.reservation.service;

import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DynamicReleaseSchedulerTest {

    @Autowired
    private DynamicReleaseScheduler dynamicReleaseScheduler;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    // 정적 스케줄러 자동실행 막기
    @com.back.mozu.domain.reservation.service.MockBean
    private StaticReleaseScheduler staticReleaseScheduler;

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        timeSlotRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동적 스케줄러 - releaseAt 이후 재고 복구")
    void dynamic_scheduler_releases_stock_after_releaseAt() {
        UUID userId = UUID.randomUUID();
        TimeSlot timeSlot = createTimeSlot(8, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        Reservation reservation = createReservation(
                userId, timeSlot, 2,
                ReservationStatus.CANCEL_PENDING,
                null, "테스트취소",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusMinutes(1)
        );

        dynamicReleaseScheduler.releaseStock(reservation.getId());

        Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
        TimeSlot updatedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(updatedSlot.getStock()).isEqualTo(10); // 8 + 2 = 10
    }

    private TimeSlot createTimeSlot(int stock, LocalDate date, LocalTime time) {
        return timeSlotRepository.save(TimeSlot.builder()
                .date(date).time(time).stock(stock).build());
    }

    private Reservation createReservation(
            UUID userId, TimeSlot timeSlot, int guestCount,
            ReservationStatus status, LocalDateTime cancelledAt,
            String cancelReason, LocalDateTime reservationOpenedAt,
            LocalDateTime releaseAt) {
        return reservationRepository.save(Reservation.builder()
                .userId(userId).timeSlot(timeSlot).guestCount(guestCount)
                .status(status).cancelledAt(cancelledAt).cancelReason(cancelReason)
                .reservationOpenedAt(reservationOpenedAt).releaseAt(releaseAt).build());
    }
}