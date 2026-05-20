package com.back.mozu.domain.setting.dto


import java.time.LocalTime

data class SettingUpdateResponseDto (
    val totalTables: Int,   // ResponseDTO는 불변, 응답값이 항상 있어야 함
    val openingTime: LocalTime,
    val closingTime: LocalTime
)