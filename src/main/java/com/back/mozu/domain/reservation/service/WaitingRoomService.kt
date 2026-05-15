package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.dto.WaitingRoomResponseDto
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WaitingRoomService(
    private val reservationRepository: ReservationRepository,
) {

    fun getMyWaiting(userId: UUID): WaitingRoomResponseDto? {
        val reservation = reservationRepository.findByUserIdAndStatus(
            userId, ReservationStatus.PENDING
        ) ?: return null

        return WaitingRoomResponseDto(
            reservationId = reservation.id,
            status = reservation.status?.name,
            queueNumber = null,
            estimatedWaitMinutes = null,
        )
    }
}
