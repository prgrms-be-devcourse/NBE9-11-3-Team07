package com.back.mozu.global.config

import com.back.mozu.domain.customer.service.CustomOAuth2UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,   // @RequiredArgsConstructor 제거 → 생성자 주입으로 변경
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,         // @Value 주입 아니므로 val 이 맞음
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        // setAllowedOrigins() → 프로퍼티 직접 접근으로 변경
        // mutableListOf<String?> → listOf<String> 으로 변경 (CORS 설정값은 불변, null 불필요)
        config.allowedOrigins = listOf("http://localhost:3000")
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    // 0순위: 모니터링용 체인 (프로메테우스 접근 허용)
    @Bean
    @Order(0)
    @Throws(Exception::class)
    fun monitoringFilterChain(http: HttpSecurity): SecurityFilterChain {  // SecurityFilterChain? → ? 제거 (http.build()는 null 반환 안 함)
        http
            .securityMatcher("/actuator/**") // 이 경로로 들어오면 이 체인이 담당함
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() } // 무조건 통과
            // !! 제거 → 람다로 단순화
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .securityContext { it.disable() }
        return http.build()
    }

    // 관리자용 체인
    @Bean
    @Order(1)
    @Throws(Exception::class)
    fun adminFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // !! 제거 → 람다로 단순화
            .cors { it.configurationSource(corsConfigurationSource()) }
            .securityMatcher("/api/v1/admin/**")
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->  // auth 여러 번 사용 → 이름 명시 (한번만 사용할 때 it 가능)
                auth
                    .requestMatchers("/api/v1/admin/auth/**").permitAll()
                    .requestMatchers("/api/v1/admin/holidays").permitAll()
                    .anyRequest().hasRole("ADMIN")
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )
        return http.build()
    }

    // OAuth2 로그인용 체인 (세션 허용)
    @Bean
    @Order(2)
    @Throws(Exception::class)
    fun oauth2FilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // !! 제거 → 람다로 단순화
            .cors { it.configurationSource(corsConfigurationSource()) }
            .securityMatcher("/api/v1/auth/**", "/oauth2/**", "/login/**")
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
            .oauth2Login { oauth2 ->  // oauth2 여러 번 사용 → 이름 명시
                oauth2
                    .authorizationEndpoint { it.baseUri("/api/v1/auth/oauth2/authorization") }
                    .redirectionEndpoint { it.baseUri("/login/oauth2/code/google") }
                    .userInfoEndpoint { it.userService(customOAuth2UserService) }
                    .successHandler(oAuth2SuccessHandler)
            }
        return http.build()
    }

    // 일반 API용 체인 (JWT + STATELESS)
    @Bean
    @Order(3)
    @Throws(Exception::class)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {  // SecurityFilterChain? → ? 제거
        http
            // !! 제거 → 람다로 단순화
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->  // auth 여러 번 사용 → 이름 명시
                auth
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}