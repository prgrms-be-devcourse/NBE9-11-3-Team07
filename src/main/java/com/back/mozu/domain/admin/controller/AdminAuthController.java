package com.back.mozu.domain.admin.controller;

import com.back.mozu.domain.admin.dto.AdminLoginRequestDto;
import com.back.mozu.domain.admin.dto.AdminLoginResponseDto;
import com.back.mozu.domain.admin.service.AdminAuthService;
import com.back.mozu.global.redis.RedisUtil;
import com.back.mozu.global.response.RsData;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.back.mozu.global.config.JwtProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;


@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final RedisUtil redisUtil;
    private final JwtProvider jwtProvider;

    @PostMapping("/login")
    public ResponseEntity<RsData<AdminLoginResponseDto>> login(
            @RequestBody AdminLoginRequestDto request,
            HttpServletResponse response) {  // 추가

        AdminLoginResponseDto loginResponse = adminAuthService.login(request);

        // Refresh Token 쿠키로 전달
        Cookie refreshCookie = new Cookie("refreshToken", loginResponse.getRefrestToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60);  // 7일
        response.addCookie(refreshCookie);

        return ResponseEntity.ok(new RsData<>("로그인에 성공했습니다.", "200", loginResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<RsData<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {  // 추가

        // Redis에서 Refresh Token 삭제
        if (refreshToken != null) {
            String userId = jwtProvider.getUserId(refreshToken);
            redisUtil.delete("refresh:" + userId);
        }

        // 쿠키 삭제
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);  // 즉시 만료
        response.addCookie(cookie);

        return ResponseEntity.ok(new RsData<>("로그아웃 되었습니다.", "200", null));
    }
}