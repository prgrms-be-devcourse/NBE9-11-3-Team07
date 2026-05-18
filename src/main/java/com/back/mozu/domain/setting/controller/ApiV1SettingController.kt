package com.back.mozu.domain.setting.controller

import com.back.mozu.domain.setting.dto.HolidayDto.*
import com.back.mozu.domain.setting.dto.SettingDto.GetSettingResponse
import com.back.mozu.domain.setting.service.HolidayService
import com.back.mozu.domain.setting.service.SettingService
import com.back.mozu.global.response.RsData
import com.back.mozu.global.response.RsData.Companion.of
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
// @RequiredArgsConstructor 제거 → 코틀린 생성자 주입으로 대체
class ApiV1SettingController(
    private val settingService: SettingService,
    private val holidayService: HolidayService
) {
    // @get:GetMapping + val getter → 일반 함수로 변환
    // !! 제거 → 생성자 주입으로 non-null 보장
    @GetMapping("/settings")
    fun getSetting(): ResponseEntity<RsData<GetSettingResponse>> {
        val response = settingService.getSetting()
        return ResponseEntity.ok(of("200", "설정 조회에 성공했습니다.", response))
    }

    @GetMapping("/holidays")
    fun getHolidays(
        @RequestParam(required = false) month: String?
    ): ResponseEntity<RsData<GetHolidaysResponse>> {
        val response = holidayService.getHolidays(month)
        return ResponseEntity.ok(of("200", "휴무일 목록 조회에 성공했습니다.", response))
    }

    @PostMapping("/holidays")
    fun createHoliday(
        @Valid @RequestBody request: CreateHolidayRequest
    ): ResponseEntity<RsData<CreateHolidayResponse>> {
        val response = holidayService.createHoliday(request)
        // RsData.of() 타입 명시 제거 → Kotlin 타입 추론으로 대체
        return ResponseEntity.ok(of("201", "휴무일이 추가되었습니다.", response))
    }
}