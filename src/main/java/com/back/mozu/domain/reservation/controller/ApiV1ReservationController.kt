package com.back.mozu.domain.reservation.controller

import com.back.mozu.domain.reservation.dto.ReservationDto
import com.back.mozu.domain.reservation.dto.ReservationDto.CancelRequest
import com.back.mozu.domain.reservation.service.ReservationService
import com.back.mozu.global.response.Rq
import com.back.mozu.global.response.RsData
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/my/reservations")
class ApiV1ReservationController(
    private val reservationService: ReservationService,
    private val rq: Rq,
) {

    @GetMapping("/")
    fun getMyReservations(): RsData<List<ReservationDto.Response>> {
        val actor = rq.getActor()
            ?: return RsData("로그인이 필요한 서비스입니다.", "401")
        val actorId = actor.id
            ?: return RsData("사용자 정보를 찾을 수 없습니다.", "401")
        val myReservations = reservationService.getMyReservation(actorId)
        return RsData("예약 목록 조회에 성공했습니다.", "200", myReservations)
    }

    @PatchMapping("/{reservationId}")
    fun modifyMyReservation(
        @PathVariable reservationId: UUID,
        @Valid @RequestBody request: ReservationDto.Request,
    ): RsData<ReservationDto.Response> {
        val actor = rq.getActor()
            ?: return RsData("로그인이 필요한 서비스입니다.", "401")
        val modifiedReservation = reservationService.modifyMyReservation(reservationId, actor.id, request)
        return RsData("예약 수정에 성공했습니다.", "200", modifiedReservation)
    }

    @PostMapping("/{reservationId}/cancel")
    fun cancelMyReservation(
        @PathVariable reservationId: UUID,
        @RequestBody request: CancelRequest,
    ): RsData<ReservationDto.Response> {
        val actor = rq.getActor()
            ?: return RsData("로그인이 필요한 서비스입니다.", "401")
        val cancelledReservation = reservationService.cancelMyReservation(actor.id, reservationId, request.cancelReason)
        return RsData("예약 취소에 성공했습니다.", "200", cancelledReservation)
    }
}
