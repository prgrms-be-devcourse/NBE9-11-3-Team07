package com.back.mozu.domain.reservation.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingRoomResponseDto {
    private UUID reservationId;
    private String status;
    private Integer queueNumber;
    private Integer estimatedWaitMinutes;
}
