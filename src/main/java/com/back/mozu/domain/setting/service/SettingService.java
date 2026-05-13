package com.back.mozu.domain.setting.service;

import com.back.mozu.domain.setting.dto.SettingDto;
import com.back.mozu.domain.setting.entity.RestaurantSettings;
import com.back.mozu.domain.setting.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

    private final SettingRepository settingRepository;

    public SettingDto.GetSettingResponse getSetting() {
        RestaurantSettings setting = settingRepository.findById(1)
                .orElseThrow(() -> new IllegalArgumentException("설정 정보를 찾을 수 없습니다."));

        return new SettingDto.GetSettingResponse(
                setting.getTotalTables(),
                setting.getOpeningTime(),
                setting.getClosingTime()
        );
    }
}
