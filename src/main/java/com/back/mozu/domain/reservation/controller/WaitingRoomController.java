package com.back.mozu.domain.reservation.controller;


import com.back.mozu.domain.reservation.dto.WaitingRoomResponseDto;
import com.back.mozu.domain.reservation.service.WaitingRoomService;
import com.back.mozu.global.response.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {
    private final WaitingRoomService waitingRoomService;

    @GetMapping("/me")
    public RsData<WaitingRoomResponseDto> getMyWaiting(@RequestParam UUID customerId) {
        WaitingRoomResponseDto result = waitingRoomService.getMyWaiting(customerId);

        if (result != null) {
            return new RsData<>("진행 중인 예약을 조회했습니다.", "200", result);
        }
        return new RsData<>("진행 중인 예약이 없습니다.", "200");
    }
}
