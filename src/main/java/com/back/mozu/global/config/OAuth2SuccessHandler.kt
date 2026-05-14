package com.back.mozu.global.config

import com.back.mozu.global.redis.RedisUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Duration

@Component
class OAuth2SuccessHandler (
    private val jwtProvider: JwtProvider,   // @RequiredArgsConstructor 제거 → 생성자 주입으로 변경
    private val redisUtil: RedisUtil        // @RequiredArgsConstructor 제거 → 생성자 주입으로 변경
) : SimpleUrlAuthenticationSuccessHandler() {

    @Value("\${frontend.url:http://localhost:3000}")
    private lateinit var frontendUrl: String    // @Value 주입 + 참조타입 → lateinit var 사용

    @Throws(IOException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as OAuth2User    // 게터 → principal 프로퍼티 접근으로 변경

        val userId: String = oAuth2User.getAttribute<Any>("userId").toString()
        val role = oAuth2User.getAttribute<String>("role")
        val isNewUser = oAuth2User.getAttribute<Boolean>("isNewUser")

        val token = jwtProvider.createToken(userId, role ?: "user")

        val refreshToken = jwtProvider.createRefreshToken(userId, role ?: "user")
        redisUtil.set("refresh:$userId", refreshToken, Duration.ofDays(7))  // 문자열 템플릿으로 변경

        // Refresh Token 쿠키로 전달
        val refreshCookie = Cookie("refreshToken", refreshToken).apply {    // apply 스코프 함수로 객체 설정 묶기
            isHttpOnly = true   // setHttpOnly() → 프로퍼티 직접 접근
            path = "/"          // setPath() → 프로퍼티 직접 접근
            maxAge = 7 * 24 * 60 * 60   // setMaxAge() → 프로퍼티 직접 접근
        }
        response.addCookie(refreshCookie)

        val redirectUrl = "$frontendUrl/auth/callback?token=$token&isNewUser=$isNewUser"
        redirectStrategy.sendRedirect(request, response, redirectUrl)   // 게터 -> 프로퍼티 접근으로 변경
    }
}