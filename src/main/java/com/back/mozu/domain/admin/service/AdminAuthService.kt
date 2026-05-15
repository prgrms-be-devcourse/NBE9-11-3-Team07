package com.back.mozu.domain.admin.service

import com.back.mozu.domain.admin.dto.AdminLoginRequestDto
import com.back.mozu.domain.admin.dto.AdminLoginResponseDto
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.global.config.JwtProvider
import com.back.mozu.global.redis.RedisUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AdminAuthService(
    private val customerRepository: CustomerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val redisUtil: RedisUtil,
) {
    fun login(request: AdminLoginRequestDto): AdminLoginResponseDto {
        val customer = customerRepository.findByEmailOrNull(request.loginId)
            ?: throw RuntimeException("존재하지 않는 계정입니다")

        if (customer.role != "ADMIN") {
            throw RuntimeException("관리자 권한이 없습니다")
        }

        val encodedPassword = customer.password
            ?: throw RuntimeException("비밀번호가 설정되지 않은 계정입니다")

        if (!passwordEncoder.matches(request.password, encodedPassword)) {
            throw RuntimeException("비밀번호가 틀렸습니다")
        }

        val token = jwtProvider.createToken(customer.id.toString(), customer.role)
        val refreshToken = jwtProvider.createRefreshToken(customer.id.toString(), customer.role)
        redisUtil.set("refresh:${customer.id}", refreshToken, Duration.ofDays(7))

        return AdminLoginResponseDto(
            accessToken = token,
            refreshToken = refreshToken,
            adminUser = AdminLoginResponseDto.AdminUserDto(
                adminId = customer.id.toString(),
                loginId = customer.email,
                name = customer.email,
            ),
        )
    }
}