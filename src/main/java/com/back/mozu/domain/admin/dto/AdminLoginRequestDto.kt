package com.back.mozu.domain.admin.dto

import jakarta.validation.constraints.NotBlank

data class AdminLoginRequestDto(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
)