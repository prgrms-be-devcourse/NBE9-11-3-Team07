package com.back.mozu.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

public class AdminDto {

    public record CancelReservationRequest(
            @NotBlank(message = "취소 사유를 입력해주세요")
            String reason
    ) {
    }

    public record CancelReservationResponse(
            UUID reservationId,
            String status,
            String reason,
            LocalDateTime canceledAt
    ) {
    }
}