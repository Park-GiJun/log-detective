package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter.ApiKeyAuthenticationFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
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
 * permitAll 경로 — `ApiKeyAuthenticationFilter.PERMIT_ALL_PATHS` 단일 상수에서 파생.
 * Filter 의 `shouldNotFilter` 와 SecurityFilterChain 의 `requestMatchers` 가 동일 소스를 참조해
 * 한쪽만 변경되어 우회 경로 표류(drift) 가 발생하는 회귀를 차단한다.
 */
@Configuration
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
) {

    /**
     * 이슈 #109 — Filter 이중 등록 차단.
     *
     * `@Component` 가 붙은 Servlet Filter 는 Spring Boot 가 ServletContext 에 자동 등록하기 때문에
     * SecurityFilterChain 의 `addFilterBefore` 와 합쳐 동일 필터가 두 번 호출된다.
     * `OncePerRequestFilter` 가 race 를 막아주긴 하지만, 401 응답이 두 번 기록되는 등 부수 효과가 남는다.
     *
     * `FilterRegistrationBean.isEnabled = false` 로 자동 등록만 끄고
     * SecurityFilterChain 단계의 등록만 살린다.
     */
    @Bean
    fun apiKeyFilterRegistration(
        filter: ApiKeyAuthenticationFilter,
    ): FilterRegistrationBean<ApiKeyAuthenticationFilter> =
        FilterRegistrationBean(filter).apply {
            isEnabled = false
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // CSRF — API Key 헤더 인증은 브라우저 세션 기반이 아니므로 CSRF 토큰 모델 불필요.
            .csrf { it.disable() }
            // STATELESS — 모든 요청은 헤더로 자체 인증, 서버 세션 미사용. 세션 고정 / 쿠키 노출 회피.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                // PERMIT_ALL_PATHS 의 모든 경로를 하위 경로 포함(`/**`) 으로 허용 — Filter 의
                // `startsWith` 매칭과 동일 의미를 SecurityFilterChain 차원에서도 보장.
                ApiKeyAuthenticationFilter.PERMIT_ALL_PATHS.forEach { path ->
                    authorize.requestMatchers("$path/**").permitAll()
                    authorize.requestMatchers(path).permitAll()
                }
                authorize.anyRequest().authenticated()
            }
            // UsernamePasswordAuthenticationFilter 앞에 끼워 form login 보다 먼저 인증 컨텍스트 채운다.
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
