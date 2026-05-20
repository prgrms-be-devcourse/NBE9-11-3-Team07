package com.back.mozu.domain.reservation.dto

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class ReservationDto {
    data class Request(
        val date: LocalDate,
        val time: LocalTime,
        @field:Min(value = 1, message = "최소 1명 이상 예약 가능합니다.")
        @field:Max(value = 8, message = "최대 8명까지 예약 가능합니다.")
        val guestCount: Int
    )

    data class CancelRequest(
        val cancelReason: String?
    )

    data class Response(
        val reservationId: UUID?,
        val status: ReservationStatus?,
        val guestCount: Int,
        val date: LocalDate,
        val time: LocalTime,
        val createdAt: LocalDateTime?,
        val cancelReason: String?
    ) {
        companion object {
            fun from(reservation: Reservation): Response {
                return Response(
                    reservationId = reservation.id,
                    status = reservation.status,
                    guestCount = reservation.guestCount,
                    date = reservation.timeSlot?.date ?: throw NoSuchElementException("타임슬롯이 없습니다."),
                    time = reservation.timeSlot?.time ?: throw NoSuchElementException("타임슬롯이 없습니다."),
                    createdAt = reservation.createdAt,
                    cancelReason = reservation.cancelReason
                )
            }
        }
    }
}