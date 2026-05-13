package com.back.mozu.domain.setting.controller;


import com.back.mozu.domain.setting.dto.SettingUpdateRequestDto;
import com.back.mozu.domain.setting.dto.SettingUpdateResponseDto;
import com.back.mozu.domain.setting.service.RestaurantSettingService;
import com.back.mozu.global.response.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class RestaurantSettingController {

    private final RestaurantSettingService restaurantSettingService;

    @PatchMapping("/settings")
    public ResponseEntity<RsData<SettingUpdateResponseDto>> updateSettings(
            @RequestBody SettingUpdateRequestDto dto){

        SettingUpdateResponseDto response = restaurantSettingService.updateSettings(dto);

        return ResponseEntity.ok(new RsData<>("설정이 저장되었습니다.","200",response));
    }
}
