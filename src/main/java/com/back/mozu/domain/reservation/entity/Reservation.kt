package com.back.mozu.domain.reservation.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "reservations")
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID? = null,

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", columnDefinition = "BINARY(16)")
    var timeSlot: TimeSlot? = null,

    @Column(nullable = false)
    var guestCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus? = null,

    @Column(nullable = true)
    var cancelledAt: LocalDateTime? = null,

    @Column(nullable = true, length = 50)
    var cancelReason: String? = null,

    @Column(nullable = true)
    var reservationOpenedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(3)")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = true)
    var releaseAt: LocalDateTime? = null,
) {
    fun confirmReservation() {
        check(this.status == ReservationStatus.PENDING) { "대기 중인 예약만 확정할 수 있습니다." }
        this.status = ReservationStatus.CONFIRMED
    }

    fun modifyReservation(newTimeSlot: TimeSlot?, guestCount: Int) {
        this.timeSlot = newTimeSlot
        this.guestCount = guestCount
        this.status = ReservationStatus.CONFIRMED
    }

    fun cancelReservation(cancelReason: String?) {
        this.status = ReservationStatus.CANCELED
        this.cancelledAt = LocalDateTime.now()
        this.cancelReason = cancelReason
    }

    fun pendingCancel(cancelReason: String?, releaseAt: LocalDateTime?) {
        this.status = ReservationStatus.CANCEL_PENDING
        this.cancelledAt = LocalDateTime.now()
        this.cancelReason = cancelReason
        this.releaseAt = releaseAt
    }
}
