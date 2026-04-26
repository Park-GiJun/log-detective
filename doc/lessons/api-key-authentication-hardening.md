# API Key 인증 — 도입 이후 강화 체크리스트

> 출처: 리뷰 7cae154 (2026-04-26) — 박서진 + 윤지아 + 이도윤

## 안티패턴

API Key 인증을 "헤더 검증" 단계까지만 구현하고 다음 운영 항목을 후속 작업으로 미룸:

1. **Multi-key rotation** — 단일 정적 키. 누설 시 무중단 회전 불가
2. **Rate-limit / lockout** — brute-force 방어 부재
3. **Length-safe 비교** — `MessageDigest.isEqual` 의 길이 leak (정규화 안 하면)
4. **메트릭 / 감사 로깅** — 인증 실패 폭증 감지 불가, 누가 호출했는지 추적 불가
5. **Hot-path 객체 할당** — 매 요청 byte[] / Authentication 객체 새로 할당 (10K TPS 시 GC pressure)
6. **Defense in depth** — actuator endpoint 노출 미제한
7. **Test 회귀 잠금** — adapter 가 헤더 첨부하는지 테스트 안 함 (라인 통째 삭제해도 PASS)

## 교훈

> API Key 는 "구현" 이 아니라 "운영 시스템" 이다.

헤더 검증 한 줄 추가는 5분이지만, 다음 7항목 없이는 1주일 내 brute-force / 키 누설 / 운영 사고로 이어진다.

## 체크리스트

API Key 인증 PR 머지 조건:

- [ ] **Length-safe 비교** — 고정 길이 해시 (SHA-256) 정규화 후 `MessageDigest.isEqual`
- [ ] **Multi-key 지원** — `List<String>` expectedKeys 또는 `Map<clientId, key>` (zero-downtime rotation)
- [ ] **Rate-limit** — IP/키 단위 카운팅 (Redis/Redisson). 5회 실패 후 5분 lockout
- [ ] **Hot-path 캐싱** — expectedKey 의 byte[] 1회 캐싱, Authentication 토큰 싱글턴
- [ ] **메트릭** — Micrometer `auth.failure.total{path,reason}` counter
- [ ] **클라이언트 식별** — `X-Client-Id` 헤더 + 키 → clientId 맵핑
- [ ] **회귀 테스트**:
  - [ ] 클라이언트 어댑터가 헤더를 첨부하는지 (MockEngine 헤더 캡처)
  - [ ] 401 E2E (헤더 누락 / 잘못된 키 → Outbox 0건 + 401)
  - [ ] whitespace 헤더 / 다중 헤더 / 대소문자 정책
- [ ] **Actuator 노출 제한** — `management.endpoints.web.exposure.include` 명시
- [ ] **응답 메시지 통일** — "Unauthorized" 만, 누락/잘못된 키 구분 안 함
- [ ] **로깅** — X-Forwarded-For 처리 (proxy 환경)
- [ ] **design.md 명문화** — 인증 정책 절 (모델 / 키 회전 / rate-limit 정책)

## 참고: Spring Boot 자동 등록 함정

`@Component` Filter + SecurityConfig 의 `addFilterBefore` = **이중 등록**. 해결:
- 옵션 A: `@Component` 제거 + SecurityConfig 에서 `@Bean` 으로만 등록 + `addFilterBefore`
- 옵션 B: `@Component` 유지 + `FilterRegistrationBean<T>` 으로 글로벌 등록 비활성화 + SecurityConfig 의 `addFilterBefore` 만 사용
