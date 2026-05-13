package com.back.mozu.global.config;

import com.back.mozu.global.redis.RedisUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String userId = oAuth2User.getAttribute("userId").toString();
        String role = oAuth2User.getAttribute("role");
        Boolean isNewUser = oAuth2User.getAttribute("isNewUser");

        String token = jwtProvider.createToken(userId, role);

        String refreshToken = jwtProvider.createRefreshToken(userId, role);
        redisUtil.set("refresh:" + userId, refreshToken, Duration.ofDays(7));

        // Refresh Token 쿠키로 전달
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60);  // 7일
        response.addCookie(refreshCookie);

        String redirectUrl = frontendUrl + "/auth/callback"
                + "?token=" + token
                + "&isNewUser=" + isNewUser;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
