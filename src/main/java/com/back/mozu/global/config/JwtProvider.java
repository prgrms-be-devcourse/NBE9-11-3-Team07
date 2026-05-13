package com.back.mozu.global.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}") // application.yml의 jwt.secret 값을 주입받음
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // 토큰 생성, jwt 문자열로 변환
    public String createToken(String userId, String role) {
        return Jwts.builder()
                .claim("userId", userId)      // payload에 저장
                .claim("role", role)
                .issuedAt(new Date())               // 토큰 발급 시간
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())          // signature
                .compact();                         // "xxx.yyy.zzz" 형태 문자열로 변환
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())    // signature로 서명 검증
                    .build()
                    .parseSignedClaims(token);      // 파싱 시도 - 유효하지 않으면 여기서 예외 발생
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰 파싱 payload 꺼내기
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(String token) {
        return getClaims(token).get("userId", String.class);
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String createRefreshToken(String userId, String role) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token);
    }


}