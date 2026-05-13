package com.back.mozu.domain.reservation.dto;

import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class ReservationDto {

    // 예약 신청 요청 (POST /api/v1/reservations)
    public record Request(
            @NotNull(message = "예약 날짜를 선택해주세요.")
            LocalDate date,

            @NotNull(message = "예약 시간을 선택해주세요.")
            LocalTime time,

            @Min(value = 1, message = "최소 1명 이상 예약 가능합니다.")
            @Max(value = 8, message = "최대 8명까지 예약 가능합니다.")
            int guestCount
    ) {}
    public record CancelRequest(
            String cancelReason
    ){}


    // 예약 결과 응답 및 상세 조회용
    public record Response(
            UUID reservationId,
            ReservationStatus status,
            int guestCount,
            LocalDate date,
            LocalTime time,
            LocalDateTime createdAt,
            String cancelReason
    ) {
        // 엔티티를 DTO로 변환하는 정적 팩토리 메서드
        public static Response from(Reservation reservation) {
            return new Response(
                    reservation.getId(),
                    reservation.getStatus(),
                    reservation.getGuestCount(),
                    reservation.getTimeSlot().getDate(),
                    reservation.getTimeSlot().getTime(),
                    reservation.getCreatedAt(),
                    reservation.getCancelReason()
            );
        }
    }
}