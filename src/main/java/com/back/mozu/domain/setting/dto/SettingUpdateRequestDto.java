package com.back.mozu.domain.setting.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettingUpdateRequestDto {
    private Integer totalTables;
    private LocalTime openingTime;
    private LocalTime closingTime;
}
