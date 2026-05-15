package com.back.mozu.domain.admin.controller

import com.back.mozu.domain.admin.dto.AdminLoginRequestDto
import com.back.mozu.domain.admin.dto.AdminLoginResponseDto
import com.back.mozu.domain.admin.service.AdminAuthService
import com.back.mozu.global.config.JwtProvider
import com.back.mozu.global.redis.RedisUtil
import com.back.mozu.global.response.RsData
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(
    private val adminAuthService: AdminAuthService,
    private val redisUtil: RedisUtil,
    private val jwtProvider: JwtProvider,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: AdminLoginRequestDto,
        response: HttpServletResponse,
    ): ResponseEntity<RsData<AdminLoginResponseDto>> {
        val loginResponse = adminAuthService.login(request)

        val refreshCookie = Cookie("refreshToken", loginResponse.refreshToken)
        refreshCookie.isHttpOnly = true
        refreshCookie.path = "/"
        refreshCookie.maxAge = 7 * 24 * 60 * 60
        response.addCookie(refreshCookie)

        return ResponseEntity.ok(RsData("로그인에 성공했습니다.", "200", loginResponse))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(value = "refreshToken", required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ResponseEntity<RsData<Void>> {
        if (refreshToken != null) {
            val userId = jwtProvider.getUserId(refreshToken)
            redisUtil.delete("refresh:$userId")
        }

        val cookie = Cookie("refreshToken", null)
        cookie.isHttpOnly = true
        cookie.path = "/"
        cookie.maxAge = 0
        response.addCookie(cookie)

        return ResponseEntity.ok(RsData("로그아웃 되었습니다.", "200", null))
    }
}