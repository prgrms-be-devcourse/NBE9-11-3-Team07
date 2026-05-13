package com.back.mozu.domain.reservation.service;

import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Component
@RequiredArgsConstructor
public class StaticReleaseScheduler implements ReleaseScheduler {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Override
    public void schedule(Reservation reservation) {
        // 나중에 구현
    }

    // 1분마다 CANCEL_PENDING 상태 예약 처리
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processRandomRelease() {
        // CANCEL_PENDING + releaseAt 지난 예약 조회
        List<Reservation> pendingList = reservationRepository
                .findAllByStatusAndReleaseAtBefore(
                        ReservationStatus.CANCEL_PENDING,
                        LocalDateTime.now()
                );

        // 재고 반환 후 CANCELED로 변경
        for (Reservation reservation : pendingList) {
            TimeSlot lockedTimeSlot = timeSlotRepository.findByIdWithLock(
                    reservation.getTimeSlot().getId()
            ).orElseThrow();
            lockedTimeSlot.release(reservation.getGuestCount());
            reservation.cancelReservation(reservation.getCancelReason());
        }
    }

    // 15초마다 좀비 PENDING 처리
    @Scheduled(fixedRate = 15000)
    @Transactional
    public void processZombiePending() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(15);

        List<Reservation> zombies = reservationRepository
                .findAllByStatusAndCreatedAtBefore(
                        ReservationStatus.PENDING,
                        threshold
                );

        // 재고 점유 전 실패한 건이므로 상태만 변경 (재고 복구 X)
        for (Reservation reservation : zombies) {
            reservation.cancelReservation("TIMEOUT");
        }
    }
}