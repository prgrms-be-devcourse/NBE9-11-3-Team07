package com.back.mozu.domain.queue.controller

import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse
import com.back.mozu.domain.queue.service.QueueService
import com.back.mozu.global.response.Rq
import com.back.mozu.global.response.RsData
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 외부 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/v1/reservations/attempts")
class ApiV1QueueController(
    private val queueService: QueueService,
    private val rq: Rq,
) {
    // 예약 시도 생성 및 대기열 진입
    @PostMapping
    fun createAttempt(@RequestBody request: AttemptRequest): RsData<AttemptResponse> {
        val actor = rq.getActor()
            ?: return RsData("로그인이 필요한 서비스입니다.", "401", null)

        val customerId = requireNotNull(actor.id) {
            "로그인 사용자 ID가 존재하지 않습니다."
        }

        val response = queueService.enqueueAttempt(customerId, request)

        return RsData.of("202", "대기열 진입 성공", response)
    }

    // 예약 처리 상태 조회
    @GetMapping("/{attemptId}")
    fun checkStatus(@PathVariable attemptId: UUID): RsData<StatusResponse> {
        val response = queueService.getAttemptStatus(attemptId)

        return RsData.of("200", "상태 조회 성공", response)
    }
}