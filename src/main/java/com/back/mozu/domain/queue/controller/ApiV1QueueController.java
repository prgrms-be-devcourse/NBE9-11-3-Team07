package com.back.mozu.domain.queue.controller;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest;
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse;
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse;
import com.back.mozu.domain.queue.service.QueueService;
import com.back.mozu.global.response.Rq;
import com.back.mozu.global.response.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 외부 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/v1/reservations/attempts")
@RequiredArgsConstructor
public class ApiV1QueueController {

    private final QueueService queueService;
    private final Rq rq;

    // 예약 시도 생성 및 대기열 진입
    @PostMapping
    public RsData<AttemptResponse> createAttempt(@RequestBody AttemptRequest request) {
        Customer actor = rq.getActor(); // 실제 로그인한 유저를 rq.getActor()로 가져와서 예약제한상태인지 체크

        if (actor == null) {
            return RsData.of("401", "로그인이 필요한 서비스입니다.", null);
        }

        // 테스트용 임시 UUID -> Rq 코드와 병합했을 때 로그인한 유저의 ID 호출
        UUID customerId = actor.getId();
        AttemptResponse response = queueService.enqueueAttempt(customerId, request);
        return RsData.of("202", "대기열 진입 성공", response);
    }

    // 예약 처리 상태 조회
    @GetMapping("/{attemptId}")
    public RsData<StatusResponse> checkStatus(@PathVariable UUID attemptId) {
        StatusResponse response = queueService.getAttemptStatus(attemptId);
        return RsData.of("200", "상태 조회 성공", response);
    }
}