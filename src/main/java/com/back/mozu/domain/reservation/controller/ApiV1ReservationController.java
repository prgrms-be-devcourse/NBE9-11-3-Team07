package com.back.mozu.domain.reservation.controller;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.reservation.dto.ReservationDto;
import com.back.mozu.domain.reservation.service.ReservationService;
import com.back.mozu.global.response.Rq;
import com.back.mozu.global.response.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/my/reservations")
public class ApiV1ReservationController {

    private final ReservationService reservationService;
    private final Rq rq;

    @GetMapping("/")
    public RsData<List<ReservationDto.Response>> getMyReservations() {

        // Rq를 통해 현재 유저 확보
        Customer actor = rq.getActor();

        // 로그인 안 된 상태면 바로 쳐내기
        if (actor == null) {
            return RsData.of("401", "로그인이 필요한 서비스입니다.", null);
        }

        // 내 예약 목록 가져오기
        List<ReservationDto.Response> myReservations = reservationService.getMyReservation(actor.getId());

        // 유저들의 모든 예약을 리스트 형태로 RsData 담아서 전달
        return new RsData<>(
                "예약 목록 조회에 성공했습니다.",
                "200",
                myReservations
        );
    }

    @PatchMapping("/{reservationId}")
    public RsData<ReservationDto.Response> modifyMyReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationDto.Request request) {

        // Rq를 통해 현재 유저 확보
        Customer actor = rq.getActor();

        // 보안 방어: 로그인 여부 체크
        if (actor == null) {
            return RsData.of("401", "로그인이 필요한 서비스입니다.", null);
        }

        // 수정 서비스 로직 실행 후 결과물 받아오기
        ReservationDto.Response modifiedReservation =
                reservationService.modifyMyReservation(reservationId, actor.getId(), request);

        // RsData 담아서 전달
        return new RsData<>(
                "예약 수정에 성공했습니다.",
                "200",
                modifiedReservation
        );
    }

    @PostMapping("/{reservationId}/cancel")
    public RsData<ReservationDto.Response> cancelMyReservation(@PathVariable UUID reservationId,@RequestBody ReservationDto.CancelRequest request) {

        // Rq를 통해 현재 유저 확보
        Customer actor = rq.getActor();

        // 보안 방어: 로그인 여부 체크
        if (actor == null) {
            return RsData.of("401", "로그인이 필요한 서비스입니다.", null);
        }

        // 취소 서비스 로직 실행
        ReservationDto.Response cancelledReservation =
                reservationService.cancelMyReservation(actor.getId(), reservationId, request.cancelReason());

        // RsData 담아서 전달
        return new RsData<>(
                "예약 취소에 성공했습니다.",
                "200",
                cancelledReservation
        );
    }
}