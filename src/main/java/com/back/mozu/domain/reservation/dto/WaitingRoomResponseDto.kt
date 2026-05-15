package com.back.mozu.domain.reservation.dto

import java.util.UUID

data class WaitingRoomResponseDto(
    val reservationId: UUID? = null,
    val status: String? = null,
    val queueNumber: Int? = null,
    val estimatedWaitMinutes: Int? = null,
)
