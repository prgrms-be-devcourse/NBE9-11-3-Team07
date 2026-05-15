package com.back.mozu.domain.setting.dto

import java.time.LocalTime

class SettingDto {
    data class GetSettingResponse(
        val totalTables: Int,
        val openingTime: LocalTime,
        val closingTime: LocalTime
    )
}
