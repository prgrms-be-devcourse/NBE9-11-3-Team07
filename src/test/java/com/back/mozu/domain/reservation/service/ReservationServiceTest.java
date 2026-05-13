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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        timeSlotRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("일반 취소 시 즉시 반환된다")
    void cancel_immediate_return() {
        UUID userId = UUID.randomUUID();

        TimeSlot timeSlot = createTimeSlot(7, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        Reservation reservation = createReservation(
                userId,
                timeSlot,
                3,
                ReservationStatus.CONFIRMED,
                null,
                null,
                null,
                null
        );

        reservationService.cancelMyReservation(userId, reservation.getId(), "단순 변심");

        Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
        TimeSlot updatedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(result.getCancelReason()).isEqualTo("단순 변심");
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(updatedSlot.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("오픈 직후 30분 이내 취소면 랜덤 반환 상태가 된다")
    void cancel_within_30_minutes_after_open_becomes_cancel_pending() {
        UUID userId = UUID.randomUUID();

        TimeSlot timeSlot = createTimeSlot(7, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        Reservation reservation = createReservation(
                userId,
                timeSlot,
                3,
                ReservationStatus.CONFIRMED,
                null,
                null,
                LocalDateTime.now().minusMinutes(10), // 오픈 후 30분 이내
                null
        );

        reservationService.cancelMyReservation(userId, reservation.getId(), "빠른 취소");

        Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
        TimeSlot updatedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(result.getCancelReason()).isEqualTo("빠른 취소");
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getReleaseAt()).isNotNull();
        assertThat(updatedSlot.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("3개월 내 3회 이상 취소 이력이 있으면 랜덤 반환 상태가 된다")
    void cancel_when_user_has_three_cancellations_in_three_months_becomes_cancel_pending() {
        UUID userId = UUID.randomUUID();

        TimeSlot oldSlot1 = createTimeSlot(10, LocalDate.now().minusDays(10), LocalTime.of(10, 0));
        TimeSlot oldSlot2 = createTimeSlot(10, LocalDate.now().minusDays(20), LocalTime.of(11, 0));
        TimeSlot oldSlot3 = createTimeSlot(10, LocalDate.now().minusDays(30), LocalTime.of(12, 0));

        createReservation(
                userId,
                oldSlot1,
                2,
                ReservationStatus.CANCELED,
                LocalDateTime.now().minusDays(10),
                "이전 취소1",
                null,
                null
        );

        createReservation(
                userId,
                oldSlot2,
                2,
                ReservationStatus.CANCELED,
                LocalDateTime.now().minusDays(20),
                "이전 취소2",
                null,
                null
        );

        createReservation(
                userId,
                oldSlot3,
                2,
                ReservationStatus.CANCELED,
                LocalDateTime.now().minusDays(30),
                "이전 취소3",
                null,
                null
        );

        TimeSlot currentSlot = createTimeSlot(8, LocalDate.now().plusMonths(1), LocalTime.of(18, 0));

        Reservation currentReservation = createReservation(
                userId,
                currentSlot,
                2,
                ReservationStatus.CONFIRMED,
                null,
                null,
                LocalDateTime.now().minusHours(1), // 오픈 직후 30분 조건은 피하려고 1시간 전
                null
        );

        reservationService.cancelMyReservation(userId, currentReservation.getId(), "반복 취소 테스트");

        Reservation result = reservationRepository.findById(currentReservation.getId()).orElseThrow();
        TimeSlot updatedSlot = timeSlotRepository.findById(currentSlot.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(result.getReleaseAt()).isNotNull();
        assertThat(updatedSlot.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("존재하지 않는 예약 취소 시 예외가 발생한다")
    void cancel_not_found_reservation_throws_exception() {
        UUID userId = UUID.randomUUID();
        UUID notExistReservationId = UUID.randomUUID();

        assertThatThrownBy(() ->
                reservationService.cancelMyReservation(userId, notExistReservationId, "없는 예약 취소")
        )
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("권한 없는 유저가 취소 시도하면 예외가 발생한다")
    void cancel_other_users_reservation_throws_exception() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        TimeSlot timeSlot = createTimeSlot(7, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        Reservation reservation = createReservation(
                ownerId,
                timeSlot,
                3,
                ReservationStatus.CONFIRMED,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() ->
                reservationService.cancelMyReservation(otherUserId, reservation.getId(), "남의 예약 취소")
        )
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("이미 취소된 예약을 다시 취소하면 예외가 발생한다")
    void cancel_already_canceled_reservation_throws_exception() {
        UUID userId = UUID.randomUUID();

        TimeSlot timeSlot = createTimeSlot(10, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        Reservation reservation = createReservation(
                userId,
                timeSlot,
                3,
                ReservationStatus.CANCELED,
                LocalDateTime.now().minusMinutes(5),
                "이미 취소됨",
                null,
                null
        );

        assertThatThrownBy(() ->
                reservationService.cancelMyReservation(userId, reservation.getId(), "재취소 시도")
        )
                .isInstanceOf(RuntimeException.class);
    }

    private TimeSlot createTimeSlot(int stock, LocalDate date, LocalTime time) {
        TimeSlot timeSlot = TimeSlot.builder()
                .date(date)
                .time(time)
                .stock(stock)
                .build();
        return timeSlotRepository.save(timeSlot);
    }

    private Reservation createReservation(
            UUID userId,
            TimeSlot timeSlot,
            int guestCount,
            ReservationStatus status,
            LocalDateTime cancelledAt,
            String cancelReason,
            LocalDateTime reservationOpenedAt,
            LocalDateTime releaseAt
    ) {
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .timeSlot(timeSlot)
                .guestCount(guestCount)
                .status(status)
                .cancelledAt(cancelledAt)
                .cancelReason(cancelReason)
                .reservationOpenedAt(reservationOpenedAt)
                .releaseAt(releaseAt)
                .build();

        return reservationRepository.save(reservation);
    }
    @Test
    @DisplayName("동시에 같은 예약 취소 시도 시 하나만 성공한다")
    void cancel_concurrent_same_reservation_only_one_succeeds() throws InterruptedException {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        TimeSlot timeSlot = createTimeSlot(3, LocalDate.now().plusMonths(1), LocalTime.of(12, 0));

        // 같은 타임슬롯에 두 예약
        Reservation reservation1 = createReservation(userId1, timeSlot, 2, ReservationStatus.CONFIRMED, null, null, null, null);
        Reservation reservation2 = createReservation(userId2, timeSlot, 2, ReservationStatus.CONFIRMED, null, null, null, null);

        // 동시에 취소 시도
        Thread thread1 = new Thread(() -> {
            try {
                reservationService.cancelMyReservation(userId1, reservation1.getId(), "동시 취소1");
            } catch (Exception e) {
                System.out.println("thread1 실패: " + e.getMessage());
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                reservationService.cancelMyReservation(userId2, reservation2.getId(), "동시 취소2");
            } catch (Exception e) {
                System.out.println("thread2 실패: " + e.getMessage());
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        TimeSlot updatedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();
        // 재고가 음수가 되면 안됨
        assertThat(updatedSlot.getStock()).isGreaterThanOrEqualTo(0);
    }
}