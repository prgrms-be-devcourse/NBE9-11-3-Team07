package com.back.mozu.global.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider  // 생성자 주입으로 변경
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null) {
            if (jwtProvider.validateToken(token)) {
                val userId = jwtProvider.getUserId(token)
                val role = jwtProvider.getRole(token)

                val authentication =
                    UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_$role"))    // listOf + 문자열 템플릿 수정
                    )

                SecurityContextHolder.getContext().authentication = authentication  // setter -> 프로퍼티 직접 접근으로 수정
            } else {
                // 토큰 만료 시 401 반환
                response.status = HttpServletResponse.SC_UNAUTHORIZED   // setter -> 프로퍼티 직접 접근으로 수정
                response.writer.write("{\"error\": \"Token expired\"}") // setter -> 프로퍼티 직접 접근으로 수정
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization")
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7)
        }
        return null
    }
}