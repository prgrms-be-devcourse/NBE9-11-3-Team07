package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReleaseStockService(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
) {
    @Transactional
    fun releaseStock(reservationId: UUID) {
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw NoSuchElementException("예약을 찾을 수 없습니다.")

        // CANCEL_PENDING 상태인 경우에만 처리
        if (reservation.status != ReservationStatus.CANCEL_PENDING) {
            return
        }

        val timeSlotId = reservation.timeSlot?.id
            ?: throw NoSuchElementException("타임슬롯 정보가 없습니다.")
        val lockedTimeSlot = timeSlotRepository.findByIdWithLock(timeSlotId)
            ?: throw NoSuchElementException("타임슬롯을 찾을 수 없습니다.")
        lockedTimeSlot.release(reservation.guestCount)
        reservation.cancelReservation(reservation.cancelReason)
    }
}