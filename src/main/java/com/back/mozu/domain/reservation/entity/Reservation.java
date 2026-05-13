package com.back.mozu.domain.reservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations") // 테이블명은 복수형이 관례야
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", columnDefinition = "BINARY(16)")
    private TimeSlot timeSlot;

    @Column(nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status; // PENDING, CONFIRMED, CANCELED

    @Column(nullable = true)
    private LocalDateTime cancelledAt;

    @Column(nullable = true, length = 50)
    private String cancelReason;

    @Column(nullable = true)
    private LocalDateTime reservationOpenedAt;

    @Builder.Default
    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt = LocalDateTime.now();
    

    @Column(nullable = true)
    private LocalDateTime releaseAt;

    public void confirmReservation() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 예약만 확정할 수 있습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void modifyReservation(TimeSlot newTimeSlot, int guestCount) {
        this.timeSlot = newTimeSlot;
        this.guestCount = guestCount;
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancelReservation(String cancelReason) {
        this.status = ReservationStatus.CANCELED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = cancelReason;
    }

    public void pendingCancel(String cancelReason, LocalDateTime releaseAt){
        this.status = ReservationStatus.CANCEL_PENDING;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = cancelReason;
        this.releaseAt = releaseAt;
    }


}