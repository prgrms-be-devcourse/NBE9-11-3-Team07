package com.back.mozu.global.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtProvider {
    @Value("\${jwt.secret}") // application.yml의 jwt.secret 값을 주입받음
    private lateinit var secret: String // @Value값 주입 + 참조타입 이므로 lateinit var 사용

    @Value("\${jwt.expiration}")
    private val expiration: Long = 0    // @Value값 주입 이지만 원시타입 이므로 기본값 으로 해결 가능

    @Value("\${jwt.refresh-expiration}")
    private val refreshExpiration: Long = 0

    private val signingKey: SecretKey
        get() = Keys.hmacShaKeyFor(secret.toByteArray())

    // 토큰 생성, jwt 문자열로 변환
    fun createToken(userId: String, role: String): String {
        return Jwts.builder()
            .claim("userId", userId) // payload에 저장
            .claim("role", role)
            .issuedAt(Date()) // 토큰 발급 시간
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(this.signingKey) // signature
            .compact() // "xxx.yyy.zzz" 형태 문자열로 변환
    }

    // 토큰 유효성 검증
    fun validateToken(token: String): Boolean {
        try {
            Jwts.parser()
                .verifyWith(this.signingKey) // signature로 서명 검증
                .build()
                .parseSignedClaims(token) // 파싱 시도 - 유효하지 않으면 여기서 예외 발생
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // 토큰 파싱 payload 꺼내기
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(this.signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
    }

    fun getUserId(token: String): String {
        return getClaims(token).get("userId", String::class.java)
    }

    fun getRole(token: String): String {
        return getClaims(token).get("role", String::class.java)
    }

    fun createRefreshToken(userId: String, role: String): String {
        return Jwts.builder()
            .claim("userId", userId)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(this.signingKey)
            .compact()
    }

    fun validateRefreshToken(token: String): Boolean {
        return validateToken(token)
    }
}