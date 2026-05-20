package com.back.mozu.domain.queue.dto

import com.back.mozu.domain.reservation.entity.ReservationStatus
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class QueueDto {
    data class AttemptRequest(
        val date: LocalDate,
        val time: LocalTime,
        val guestCount: Int,
    )

    data class AttemptResponse(
        val attemptId: UUID,
    )

    data class StatusResponse(
        val status: ReservationStatus,
        val rank: Long?,
        val estimatedWaitMinutes: Long?,
    )
}