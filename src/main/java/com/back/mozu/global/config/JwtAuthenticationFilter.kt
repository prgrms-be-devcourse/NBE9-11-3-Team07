package com.back.mozu.global.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import lombok.RequiredArgsConstructor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.List

@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter : OncePerRequestFilter() {
    private val jwtProvider: JwtProvider? = null

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null) {
            if (jwtProvider!!.validateToken(token)) {
                val userId = jwtProvider.getUserId(token)
                val role = jwtProvider.getRole(token)

                val authentication =
                    UsernamePasswordAuthenticationToken(
                        userId!!,
                        null,
                        List.of<SimpleGrantedAuthority?>(SimpleGrantedAuthority("ROLE_" + role))
                    )

                SecurityContextHolder.getContext().setAuthentication(authentication)
            } else {
                // 토큰 만료 시 401 반환
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
                response.getWriter().write("{\"error\": \"Token expired\"}")
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