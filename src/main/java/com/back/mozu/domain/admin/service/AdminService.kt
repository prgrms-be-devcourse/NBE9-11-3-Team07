package com.back.mozu.domain.admin.service

import com.back.mozu.domain.admin.dto.AdminDto
import com.back.mozu.domain.admin.dto.AdminReservationDto
import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AdminService(
    private val reservationRepository: ReservationRepository,
    private val customerRepository: CustomerRepository,
) {
    fun getReservations(
        date: LocalDate?,
        time: LocalTime?,
        status: String?,
        pageable: Pageable,
    ): Page<AdminReservationDto> {
        val reservations = reservationRepository.findAllWithFilters(date, time, status, pageable)
        val reservationList = reservations.content

        val userIds = reservationList.mapNotNull { it.userId }.toSet()

        val customerMap: Map<UUID, Customer> = customerRepository.findAllById(userIds)
            .associateBy { customer ->
                customer.id ?: throw IllegalStateException("고객 ID가 존재하지 않습니다.")
            }

        val dtoList = reservationList.map { reservation ->
            val customer = reservation.userId?.let { customerMap[it] }
            AdminReservationDto.from(reservation, customer)
        }

        return PageImpl(dtoList, pageable, reservations.totalElements)
    }

    @Transactional
    fun cancelReservation(
        reservationId: UUID,
        request: AdminDto.CancelReservationRequest,
    ): AdminDto.CancelReservationResponse {
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw IllegalArgumentException("예약을 찾을 수 없습니다.")

        if (reservation.status == ReservationStatus.CANCELED) {
            throw IllegalStateException("이미 취소된 예약입니다.")
        }

        reservation.cancelReservation("ADMIN_CANCEL")

        return AdminDto.CancelReservationResponse(
            reservationId = reservation.id ?: throw IllegalStateException("예약 ID가 존재하지 않습니다."),
            status = (reservation.status ?: throw IllegalStateException("예약 상태가 존재하지 않습니다.")).name,
            reason = request.reason,
            canceledAt = LocalDateTime.now(),
        )
    }
}