package com.back.mozu.domain.queue.dto;

import com.back.mozu.domain.reservation.entity.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class QueueDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptRequest {
        private LocalDate date;
        private LocalTime time;
        private int guestCount;
    }

    @Getter
    @AllArgsConstructor
    public static class AttemptResponse {
        private UUID attemptId;
    }

    @Getter
    @AllArgsConstructor
    public static class StatusResponse {
        private ReservationStatus status;
        private Long rank;                  // 현재 대기 순번 (1-based), null이면 처리 완료
        private Long estimatedWaitMinutes;  // 예상 대기 시간 (분)
    }
}