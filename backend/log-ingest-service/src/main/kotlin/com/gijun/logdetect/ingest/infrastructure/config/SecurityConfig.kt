package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter.ApiKeyAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * 이슈 #86 — log-ingest-service 인증/인가 도입.
 *
 * 이전: `anyRequest().permitAll()` + CSRF disable. 외부망 노출 시 즉시 침해.
 *
 * 결정 — 옵션 B (X-API-Key 헤더 인증):
 *  - internal 서비스 간 호출 (Generator → Ingest) 만 존재하므로 JWT 인프라 불필요.
 *  - 환경변수 `INGEST_API_KEY` 단일 키로 운영. 키 누설 시 환경변수 회전만으로 대응.
 *  - 향후 OAuth/JWT 마이그레이션 시 본 필터를 신규 인증 메커니즘으로 교체하는 단계적 진화 가능.
 *
 * permitAll 경로 — actuator 헬스/info 만 (운영 LB 의 헬스체크 + 디버그). 본문은 민감 정보 미포함.
 */
@Configuration
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // CSRF — API Key 헤더 인증은 브라우저 세션 기반이 아니므로 CSRF 토큰 모델 불필요.
            .csrf { it.disable() }
            // STATELESS — 모든 요청은 헤더로 자체 인증, 서버 세션 미사용. 세션 고정 / 쿠키 노출 회피.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    .anyRequest().authenticated()
            }
            // UsernamePasswordAuthenticationFilter 앞에 끼워 form login 보다 먼저 인증 컨텍스트 채운다.
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
