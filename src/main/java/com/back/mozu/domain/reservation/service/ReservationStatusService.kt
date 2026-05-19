package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.repository.ReservationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReservationStatusService(
    private val reservationRepository: ReservationRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cancelReservationSafely(reservationId: UUID, reason: String) {
        val reservation = reservationRepository.findByIdOrNull(reservationId) ?: return
        reservation.cancelReservation(reason)
    }
}