package com.back.mozu.global.config

import com.back.mozu.global.redis.RedisUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import lombok.RequiredArgsConstructor
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Duration

@Component
@RequiredArgsConstructor
class OAuth2SuccessHandler : SimpleUrlAuthenticationSuccessHandler() {
    private val jwtProvider: JwtProvider? = null
    private val redisUtil: RedisUtil? = null

    @Value("\${frontend.url:http://localhost:3000}")
    private val frontendUrl: String? = null

    @Throws(IOException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.getPrincipal() as OAuth2User?

        val userId: String? = oAuth2User!!.getAttribute<Any?>("userId").toString()
        val role = oAuth2User.getAttribute<String?>("role")
        val isNewUser = oAuth2User.getAttribute<Boolean?>("isNewUser")

        val token = jwtProvider!!.createToken(userId, role)

        val refreshToken = jwtProvider.createRefreshToken(userId, role)
        redisUtil!!.set("refresh:" + userId, refreshToken, Duration.ofDays(7))

        // Refresh Token 쿠키로 전달
        val refreshCookie = Cookie("refreshToken", refreshToken)
        refreshCookie.setHttpOnly(true)
        refreshCookie.setPath("/")
        refreshCookie.setMaxAge(7 * 24 * 60 * 60) // 7일
        response.addCookie(refreshCookie)

        val redirectUrl = (frontendUrl + "/auth/callback"
                + "?token=" + token
                + "&isNewUser=" + isNewUser)

        getRedirectStrategy().sendRedirect(request, response, redirectUrl)
    }
}
