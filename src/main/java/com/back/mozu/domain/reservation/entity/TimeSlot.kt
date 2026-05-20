package com.back.mozu.domain.reservation.entity

import jakarta.persistence.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

@Entity
@Table(name = "time_slots")
class TimeSlot(
    var date: LocalDate? = null,
    var time: LocalTime? = null,
    var stock: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID? = null,

    @Version // 낙관적 락 관리 필드
    var version: Int = 0,
) {
    fun decreaseStock(count: Int) {
        require(this.stock >= count) { "해당 시간대의 예약이 불가능합니다." }
        this.stock -= count
    }

    fun occupy(@Min(1) @Max(8) guestCount: Int) {
        require(guestCount in 1..8) { "예약 인원은 최소 1명, 최대 8명까지 가능합니다." }
        require(this.stock >= guestCount) { "해당 시간대의 예약 가능 인원이 부족합니다. (현재 잔여: ${this.stock})" }
        this.stock -= guestCount
    }

    fun release(guestCount: Int) {
        require(guestCount >= 1) { "반환할 인원 수는 1명 이상이어야 합니다." }
        this.stock += guestCount
    }
}