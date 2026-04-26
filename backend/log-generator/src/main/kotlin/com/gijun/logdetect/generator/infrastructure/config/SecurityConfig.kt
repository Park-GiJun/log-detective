package com.gijun.logdetect.generator.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    @Value("\${security.admin.username:\${ADMIN_USERNAME:admin}}") private val adminUsername: String,
    // ADMIN_PASSWORD 미설정 시 빈 문자열로 바인딩되어 인증이 사실상 무력화될 수 있음 → init 블록에서 fail-fast
    @Value("\${security.admin.password:\${ADMIN_PASSWORD:}}") private val adminPassword: String,
) {

    init {
        require(adminPassword.isNotBlank()) {
            "ADMIN_PASSWORD 환경변수가 설정되지 않았거나 비어 있습니다. " +
                "보안상 빈 패스워드로 부팅할 수 없습니다 — 환경변수 또는 security.admin.password 프로퍼티를 설정하세요."
        }
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/actuator/info", permitAll)
                authorize("/api/v1/generator/status", permitAll)
                authorize("/api/v1/generator/**", hasRole("ADMIN"))
                authorize("/api/v1/scenarios/**", hasRole("ADMIN"))
                authorize(anyRequest, denyAll)
            }
            httpBasic { }
        }
        return http.build()
    }

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val admin = User.builder()
            .username(adminUsername)
            .password(passwordEncoder.encode(adminPassword))
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(admin)
    }
}
