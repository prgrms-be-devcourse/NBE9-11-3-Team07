package com.back.mozu.global.config

import com.back.mozu.domain.customer.service.CustomOAuth2UserService
import lombok.RequiredArgsConstructor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.*
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer
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
@RequiredArgsConstructor
class SecurityConfig {
    private val customOAuth2UserService: CustomOAuth2UserService? = null
    private val oAuth2SuccessHandler: OAuth2SuccessHandler? = null
    private val jwtAuthenticationFilter: JwtAuthenticationFilter? = null

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
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
    fun monitoringFilterChain(http: HttpSecurity): SecurityFilterChain? {
        http
            .securityMatcher("/actuator/**") // 이 경로로 들어오면 이 체인이 담당함
            .authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
                auth.anyRequest().permitAll()
            } // 무조건 통과
            .csrf(Customizer { csrf: CsrfConfigurer<HttpSecurity?>? -> csrf!!.disable() })
            .cors(Customizer { cors: CorsConfigurer<HttpSecurity?>? ->
                cors!!.configurationSource(
                    corsConfigurationSource()
                )
            })
            .sessionManagement(Customizer { session: SessionManagementConfigurer<HttpSecurity?>? ->
                session!!.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            })
            .requestCache(Customizer { cache: RequestCacheConfigurer<HttpSecurity?>? -> cache!!.disable() })
            .securityContext(Customizer { context: SecurityContextConfigurer<HttpSecurity?>? -> context!!.disable() })
        return http.build()
    }

    // 관리자용 체인
    @Bean
    @Order(1)
    @Throws(Exception::class)
    fun adminFilterChain(http: HttpSecurity): SecurityFilterChain? {
        http
            .cors(Customizer { cors: CorsConfigurer<HttpSecurity?>? ->
                cors!!.configurationSource(
                    corsConfigurationSource()
                )
            })
            .securityMatcher("/api/v1/admin/**")
            .csrf(Customizer { csrf: CsrfConfigurer<HttpSecurity?>? -> csrf!!.disable() })
            .formLogin(Customizer { form: FormLoginConfigurer<HttpSecurity?>? -> form!!.disable() })
            .httpBasic(Customizer { basic: HttpBasicConfigurer<HttpSecurity?>? -> basic!!.disable() })
            .authorizeHttpRequests { auth ->
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
    fun oauth2FilterChain(http: HttpSecurity): SecurityFilterChain? {
        http
            .cors(Customizer { cors: CorsConfigurer<HttpSecurity?>? ->
                cors!!.configurationSource(
                    corsConfigurationSource()
                )
            })
            .securityMatcher("/api/v1/auth/**", "/oauth2/**", "/login/**")
            .csrf(Customizer { csrf: CsrfConfigurer<HttpSecurity?>? -> csrf!!.disable() })
            .formLogin(Customizer { form: FormLoginConfigurer<HttpSecurity?>? -> form!!.disable() })
            .httpBasic(Customizer { basic: HttpBasicConfigurer<HttpSecurity?>? -> basic!!.disable() })
            .authorizeHttpRequests { auth ->
                auth
                    .anyRequest().permitAll()
            }
            .oauth2Login(Customizer { oauth2: OAuth2LoginConfigurer<HttpSecurity?>? ->
                oauth2!!
                    .authorizationEndpoint { auth -> auth.baseUri("/api/v1/auth/oauth2/authorization") }
                    .redirectionEndpoint { redir -> redir.baseUri("/login/oauth2/code/google") }
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(
                            customOAuth2UserService
                        )
                    }
                    .successHandler(oAuth2SuccessHandler)
            }
            )
        return http.build()
    }

    // 일반 API용 체인 (JWT + STATELESS)
    @Bean
    @Order(3)
    @Throws(Exception::class)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain? {
        http
            .cors(Customizer { cors: CorsConfigurer<HttpSecurity?>? ->
                cors!!.configurationSource(
                    corsConfigurationSource()
                )
            })
            .csrf(Customizer { csrf: CsrfConfigurer<HttpSecurity?>? -> csrf!!.disable() })
            .sessionManagement(Customizer { session: SessionManagementConfigurer<HttpSecurity?>? ->
                session!!.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            })
            .formLogin { form: FormLoginConfigurer<HttpSecurity?>? -> form!!.disable() }
            .httpBasic { basic: HttpBasicConfigurer<HttpSecurity?>? -> basic!!.disable() }
            .authorizeHttpRequests { auth ->
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