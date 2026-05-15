package com.back.mozu.domain.customer.controller

import com.back.mozu.domain.customer.dto.CustomerDto.MeResponse
import com.back.mozu.global.config.JwtProvider
import com.back.mozu.global.redis.RedisUtil
import com.back.mozu.global.response.Rq
import com.back.mozu.global.response.RsData
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.IOException

@RestController
@RequestMapping("/api/v1/auth")
class ApiV1AuthController(
    private val rq: Rq,
    private val jwtProvider: JwtProvider,
    private val redisUtil: RedisUtil,
) {
    @GetMapping("/google")
    @Throws(IOException::class)
    fun googleLogin(response: HttpServletResponse) {
        response.sendRedirect("/api/v1/auth/oauth2/authorization/google")
    }

    @GetMapping("/me")
    fun getMe(): ResponseEntity<RsData<MeResponse>> {
        val actor = rq.getActor()
            ?: return ResponseEntity
                .status(401)
                .body(RsData("인증된 유저 정보가 없습니다.", "401", null))

        val response = MeResponse(actor)

        return ResponseEntity.ok(
            RsData.of("200", "유저 정보 조회에 성공했습니다.", response),
        )
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

        val cookie = Cookie("refreshToken", null).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 0
        }

        response.addCookie(cookie)

        return ResponseEntity.ok(
            RsData("로그아웃 되었습니다.", "200", null),
        )
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(value = "refreshToken", required = false) refreshToken: String?,
    ): ResponseEntity<RsData<String>> {
        if (refreshToken == null) {
            return ResponseEntity
                .status(401)
                .body(RsData("refreshToken이 없습니다.", "401", null))
        }

        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            return ResponseEntity
                .status(401)
                .body(RsData("유효하지 않은 refreshToken입니다.", "401", null))
        }

        val userId = jwtProvider.getUserId(refreshToken)
        val savedToken = redisUtil.get("refresh:$userId")

        if (refreshToken != savedToken) {
            return ResponseEntity
                .status(401)
                .body(RsData("만료된 refreshToken입니다.", "401", null))
        }

        val role = jwtProvider.getRole(refreshToken)
        val newAccessToken = jwtProvider.createToken(userId, role)

        return ResponseEntity.ok(
            RsData.of("200", "토큰이 재발급되었습니다.", newAccessToken),
        )
    }
}

