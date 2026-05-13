package com.back.mozu.domain.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingUpdateResponseDto {
    private Integer totalTables;
    private LocalTime openingTime;
    private LocalTime closingTime;
}
