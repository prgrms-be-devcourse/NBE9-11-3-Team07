package com.back.mozu.domain.admin.dto

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.reservation.entity.Reservation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AdminReservationDto(
    reservation: Reservation,
    customer: Customer?,
) {
    val reservationId: String = reservation.id.toString()
    val userId: String = reservation.userId.toString()
    val userName: String? = customer?.name
    val userEmail: String? = customer?.email
    val date: LocalDate? = reservation.timeSlot?.date
    val time: LocalTime? = reservation.timeSlot?.time
    val guestCount: Int = reservation.guestCount
    val status: String = reservation.status.name
    val cancelReason: String? = reservation.cancelReason
    val createdAt: LocalDateTime = reservation.createdAt
}