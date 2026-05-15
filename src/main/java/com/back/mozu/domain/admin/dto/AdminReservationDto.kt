package com.back.mozu.domain.admin.dto

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.reservation.entity.Reservation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AdminReservationDto(
    val reservationId: String,
    val userId: String,
    val userName: String?,
    val userEmail: String?,
    val date: LocalDate?,
    val time: LocalTime?,
    val guestCount: Int,
    val status: String,
    val cancelReason: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(reservation: Reservation, customer: Customer?): AdminReservationDto {
            val reservationId = reservation.id ?: throw IllegalStateException("예약 ID가 존재하지 않습니다.")
            val userId = reservation.userId ?: throw IllegalStateException("사용자 ID가 존재하지 않습니다.")
            val status = reservation.status ?: throw IllegalStateException("예약 상태가 존재하지 않습니다.")

            return AdminReservationDto(
                reservationId = reservationId.toString(),
                userId = userId.toString(),
                userName = customer?.name,
                userEmail = customer?.email,
                date = reservation.timeSlot?.date,
                time = reservation.timeSlot?.time,
                guestCount = reservation.guestCount,
                status = status.name,
                cancelReason = reservation.cancelReason,
                createdAt = reservation.createdAt,
            )
        }
    }
}