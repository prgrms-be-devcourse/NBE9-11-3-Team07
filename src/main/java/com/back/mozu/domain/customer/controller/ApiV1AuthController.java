package com.back.mozu.domain.customer.controller;

import com.back.mozu.domain.customer.dto.CustomerDto;
import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.service.AuthService;
import com.back.mozu.global.config.JwtProvider;
import com.back.mozu.global.redis.RedisUtil;
import com.back.mozu.global.response.Rq;
import com.back.mozu.global.response.RsData;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import org.springframework.web.bind.annotation.CookieValue;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ApiV1AuthController {

    private final AuthService authService;
    private final Rq rq;
    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;

    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect("/api/v1/auth/oauth2/authorization/google");
    }

    @GetMapping("/me")
    public ResponseEntity<RsData<CustomerDto.MeResponse>> getMe() {
        Customer actor = rq.getActor();
        if (actor == null) {
            return ResponseEntity
                    .status(401)
                    .body(RsData.of("401", "인증된 유저 정보가 없습니다.", null));
        }
        CustomerDto.MeResponse response = new CustomerDto.MeResponse(actor);
        return ResponseEntity.ok(
                RsData.of("200", "유저 정보 조회에 성공했습니다.", response)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<RsData<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // Redis에서 삭제
        if (refreshToken != null) {
            String userId = jwtProvider.getUserId(refreshToken);
            redisUtil.delete("refresh:" + userId);
        }

        // 쿠키 삭제
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(new RsData<>("로그아웃 되었습니다.", "200", null));
    }
    @PostMapping("/refresh")
    public ResponseEntity<RsData<String>> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null) {
            return ResponseEntity.status(401)
                    .body(RsData.of("401", "refreshToken이 없습니다.", null));
        }

        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(401)
                    .body(RsData.of("401", "유효하지 않은 refreshToken입니다.", null));
        }

        String userId = jwtProvider.getUserId(refreshToken);
        String savedToken = redisUtil.get("refresh:" + userId);

        if (!refreshToken.equals(savedToken)) {
            return ResponseEntity.status(401)
                    .body(RsData.of("401", "만료된 refreshToken입니다.", null));
        }

        String role = jwtProvider.getRole(refreshToken);
        String newAccessToken = jwtProvider.createToken(userId, role);

        return ResponseEntity.ok(RsData.of("200", "토큰이 재발급되었습니다.", newAccessToken));
    }
}