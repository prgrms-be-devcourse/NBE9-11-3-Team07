package com.back.mozu.global.response;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

@Component
@RequestScope // HTTP 요청마다 객체가 생성되고 소멸 (동시성 안전)
@RequiredArgsConstructor
public class Rq {

    private final CustomerService customerService;
    private final HttpServletRequest req;
    private final HttpServletResponse resp;

    private Customer actor; // 현재 로그인한 유저 캐싱
    private boolean isActorLoaded = false;

    // 현재 로그인한 유저(Actor)를 안전하게 가져옴
    public Customer getActor() {
        if (isActorLoaded) return actor;

        // 1. SecurityContext에서 인증 정보 추출
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            isActorLoaded = true;
            return null;
        }

        // 2. 인증된 유저의 식별자(ID 또는 Email) 추출
        String identifier = authentication.getName();

        // 3. CustomerService를 통해 유저 조회 (UUID -> Email 순서로 시도)
        try {
            // identifier가 UUID 형식일 경우 (OAuth2 성공 시)
            this.actor = customerService.findById(UUID.fromString(identifier)).orElse(null);
        } catch (IllegalArgumentException e) {
            // identifier가 일반 문자열(Email)일 경우
            this.actor = customerService.findByEmail(identifier).orElse(null);
        }

        isActorLoaded = true;
        return actor;
    }

    // 로그인 상태 확인
    public boolean isLogin() {
        return getActor() != null;
    }

    // 로그아웃 상태 확인
    public boolean isLogout() {
        return !isLogin();
    }

    // 관리자 여부 확인
    public boolean isAdmin() {
        if (isLogout()) return false;
        return "ADMIN".equals(getActor().getRole());
    }

    // 헤더 정보가 필요할 때 사용
    public String getHeader(String name) {
        return req.getHeader(name);
    }
}