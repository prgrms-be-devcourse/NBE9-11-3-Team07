package com.back.mozu.domain.admin.service;

import com.back.mozu.domain.admin.dto.AdminLoginRequestDto;
import com.back.mozu.domain.admin.dto.AdminLoginResponseDto;
import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import com.back.mozu.global.config.JwtProvider;
import com.back.mozu.global.redis.RedisUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;


    public AdminLoginResponseDto login(AdminLoginRequestDto request) {
        // email로 관리자 찾기 (loginId = email)
        Customer customer = customerRepository.findByEmail(request.getLoginId())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 계정입니다"));

        // ADMIN role 확인
        if (!customer.getRole().equals("ADMIN")) {
            throw new RuntimeException("관리자 권한이 없습니다");
        }

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new RuntimeException("비밀번호가 틀렸습니다");
        }

        // Access Token 발급
        String token = jwtProvider.createToken(customer.getId().toString(), customer.getRole());

        String refreshToken = jwtProvider.createRefreshToken(customer.getId().toString(), customer.getRole());
        redisUtil.set("refresh:" + customer.getId(),refreshToken, Duration.ofDays(7));

        return AdminLoginResponseDto.builder()
                .accessToken(token)
                .refrestToken(refreshToken)
                .adminUser(AdminLoginResponseDto.AdminUserDto.builder()
                        .adminId(customer.getId().toString())
                        .loginId(customer.getEmail())
                        .name(customer.getEmail()) // name 필드 없으면 email로 대체
                        .build())
                .build();

    }
}