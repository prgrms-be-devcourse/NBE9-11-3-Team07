package com.back.mozu.domain.admin.controller

import com.back.mozu.domain.admin.dto.AdminDto
import com.back.mozu.domain.admin.dto.AdminReservationDto
import com.back.mozu.domain.admin.service.AdminService
import com.back.mozu.global.response.RsData
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
class ApiV1AdminController(
    private val adminService: AdminService,
) {
    @GetMapping("/reservations")
    fun getReservations(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) time: LocalTime?,
        @RequestParam(required = false) status: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): RsData<Page<AdminReservationDto>> {
        val result = adminService.getReservations(date, time, status, pageable)

        return RsData("예약 현황 조회에 성공했습니다.", "200", result)
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    fun cancelReservation(
        @PathVariable reservationId: UUID,
        @Valid @RequestBody request: AdminDto.CancelReservationRequest,
    ): ResponseEntity<RsData<AdminDto.CancelReservationResponse>> {
        val response = adminService.cancelReservation(reservationId, request)

        return ResponseEntity.ok(
            RsData.of("200", "예약이 강제 취소되었습니다.", response),
        )
    }
}