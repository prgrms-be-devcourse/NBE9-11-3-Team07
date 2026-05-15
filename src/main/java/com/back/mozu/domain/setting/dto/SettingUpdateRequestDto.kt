package com.back.mozu.domain.setting.dto

import java.time.LocalTime

// PATCH 요청이라 일부 필드만 올 수 있어서 nullable 유지
// data class = 자바의 record
data class SettingUpdateRequestDto(
    val totalTables: Int?,
    val openingTime: LocalTime?,
    val closingTime: LocalTime?
)