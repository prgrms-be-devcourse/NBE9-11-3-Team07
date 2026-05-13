package com.back.mozu.domain.setting.controller;

import com.back.mozu.domain.setting.dto.HolidayDto;
import com.back.mozu.domain.setting.dto.SettingDto;
import com.back.mozu.domain.setting.service.HolidayService;
import com.back.mozu.domain.setting.service.SettingService;
import com.back.mozu.global.response.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class ApiV1SettingController {

    private final SettingService settingService;
    private final HolidayService holidayService;

    @GetMapping("/settings")
    public ResponseEntity<RsData<SettingDto.GetSettingResponse>> getSetting() {
        SettingDto.GetSettingResponse response = settingService.getSetting();

        return ResponseEntity.ok(
                RsData.of("200", "설정 조회에 성공했습니다.", response));
    }

    @GetMapping("/holidays")
    public ResponseEntity<RsData<HolidayDto.GetHolidaysResponse>> getHolidays(
            @RequestParam(required = false) String month
    ) {
        HolidayDto.GetHolidaysResponse response = holidayService.getHolidays(month);

        return ResponseEntity.ok(
                RsData.of("200", "휴무일 목록 조회에 성공했습니다.", response)
        );
    }

    @PostMapping("/holidays")
    public ResponseEntity<RsData<HolidayDto.CreateHolidayResponse>> createHoliday(
            @Valid @RequestBody HolidayDto.CreateHolidayRequest request
    ) {
        HolidayDto.CreateHolidayResponse response = holidayService.createHoliday(request);

        return ResponseEntity.ok(
                RsData.of("201", "휴무일이 추가되었습니다.", response)
        );
    }
}