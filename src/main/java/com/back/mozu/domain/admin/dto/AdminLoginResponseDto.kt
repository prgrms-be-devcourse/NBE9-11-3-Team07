package com.back.mozu.domain.admin.dto

data class AdminLoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val adminUser: AdminUserDto,
) {
    data class AdminUserDto(
        val adminId: String,
        val loginId: String,
        val name: String,
    )
}