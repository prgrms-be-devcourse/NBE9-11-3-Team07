package com.back.mozu.domain.setting.controller

import com.back.mozu.domain.setting.dto.SettingUpdateRequestDto
import com.back.mozu.domain.setting.dto.SettingUpdateResponseDto
import com.back.mozu.domain.setting.service.RestaurantSettingService
import com.back.mozu.global.response.RsData
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
// @RequiredArgsConstructor 제거 → 코틀린 생성자 주입으로 대체
class RestaurantSettingController(
    private val restaurantSettingService: RestaurantSettingService
) {
    @PatchMapping("/settings")
    fun updateSettings(
        @RequestBody dto: SettingUpdateRequestDto
    ): ResponseEntity<RsData<SettingUpdateResponseDto>> {
        // !! 제거 → 생성자 주입으로 non-null 보장
        // nullable 제거 → RsData<SettingUpdateResponseDto?>? → RsData<SettingUpdateResponseDto>
        // RsData.of()로 통일 → 다른 Controller와 일관성 유지
        val response = restaurantSettingService.updateSettings(dto)
        return ResponseEntity.ok(RsData.of("200", "설정이 저장되었습니다.", response))
    }
}