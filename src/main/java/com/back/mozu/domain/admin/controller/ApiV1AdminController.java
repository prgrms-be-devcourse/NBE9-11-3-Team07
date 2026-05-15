package com.back.mozu.domain.admin.controller;

import com.back.mozu.domain.admin.dto.AdminDto;
import com.back.mozu.domain.admin.dto.AdminReservationDto;
import com.back.mozu.domain.admin.service.AdminService;
import com.back.mozu.global.response.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class ApiV1AdminController {

    private final AdminService adminService;

    @GetMapping("/reservations")
    public RsData<Page<AdminReservationDto>> getReservations(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalTime time,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AdminReservationDto> result = adminService.getReservations(date, time, status, pageable);

        return new RsData<>("예약 현황 조회에 성공했습니다.", "200", result);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<RsData<AdminDto.CancelReservationResponse>> cancelReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody AdminDto.CancelReservationRequest request
    ) {
        AdminDto.CancelReservationResponse response = adminService.cancelReservation(reservationId, request);

        return ResponseEntity.ok(
                RsData.of("200", "예약이 강제 취소되었습니다.", response)
        );
    }
}
