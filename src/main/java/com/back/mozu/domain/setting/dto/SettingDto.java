package com.back.mozu.domain.setting.dto;

import java.time.LocalTime;

public class SettingDto {

    public record GetSettingResponse(
            Integer totalTables,
            LocalTime openingTime,
            LocalTime closingTime
    ) {
    }
}
