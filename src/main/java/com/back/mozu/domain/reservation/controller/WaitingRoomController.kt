package com.back.mozu.domain.reservation.controller

import com.back.mozu.domain.reservation.dto.WaitingRoomResponseDto
import com.back.mozu.domain.reservation.service.WaitingRoomService
import com.back.mozu.global.response.RsData
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/waiting-room")
class WaitingRoomController(
    private val waitingRoomService: WaitingRoomService,
) {

    @GetMapping("/me")
    fun getMyWaiting(@RequestParam customerId: UUID): RsData<WaitingRoomResponseDto?> {
        val result = waitingRoomService.getMyWaiting(customerId)
        return if (result != null) {
            RsData("진행 중인 예약을 조회했습니다.", "200", result)
        } else {
            RsData("진행 중인 예약이 없습니다.", "200")
        }
    }
}
