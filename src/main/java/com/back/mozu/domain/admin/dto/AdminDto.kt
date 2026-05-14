package com.back.mozu.domain.admin.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

class AdminDto {
    data class CancelReservationRequest(
        @field:NotBlank(message = "취소 사유를 입력해주세요")
        val reason: String,
    )

    data class CancelReservationResponse(
        val reservationId: UUID,
        val status: String,
        val reason: String,
        val canceledAt: LocalDateTime,
    )
}