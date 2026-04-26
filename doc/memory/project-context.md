# 프로젝트 컨텍스트 (2026-04-26 기준)

## 구현 현황
- **Phase 0 (스켈레톤)**: 완료
- **Phase 1 (수집 파이프라인)**: 진행 중
  - log-generator: 시나리오 CRUD + REST/Kafka/File 전송 + 다중 실행 / RequestType 분기 / 단위 테스트 완성 (#35)
  - log-ingest-service: 헥사고날 전체 구현 완료. **Outbox 패턴 도입 (#37)** — DB ↔ ES/Kafka 트랜잭션 정합 시도. 단, e1ae759 커밋에 **CRITICAL 결함 다수** (컴파일 결함, #25 미해결, 테스트 0건)
- **Phase 2 (탐지 엔진)**: 미착수 (스켈레톤 + Flyway 시드만)

## 기술 부채

### CRITICAL (즉시)
1. [CRITICAL] **ChannelType 3중 불일치** — enum (ES/KAFKA/CACHE) ↔ Publisher when (FILE/OTHERS) ↔ SQL CHECK (FILE/OTHERS). **컴파일 실패 가능**
2. [CRITICAL] **OutboxPublisher.@Transactional 내 외부 IO** — #25 가 위치만 이동. 트랜잭션 길이 = Σ(ES+Kafka latency)
3. [CRITICAL] **처리량 1/100** — 100 batch × 1s = 6K msg/min. ES `_bulk` / Kafka batch send 미사용
4. [CRITICAL] **fetchPending SQL 결함** — `status='PENDING'` 만 fetch. FAILED 행 영구 미재시도
5. [CRITICAL] **Handler/Publisher 단위 테스트 0건** — 6종 불변식 어느 하나도 테스트 부재

### HIGH
6. [HIGH] OutboxPublisher 위치 (`infrastructure/scheduler/`) — `infrastructure/adapter/in/scheduler/` 로 이동
7. [HIGH] inbound 어댑터(Publisher) 가 outbound port 직접 호출 — `DispatchOutboxUseCase` 신설
8. [HIGH] Application Handler 의 인프라 누수 — ObjectMapper, ES 인덱스 네이밍
9. [HIGH] payload PII 평문 보관 + retention 정책 부재 (GDPR)
10. [HIGH] KafkaTemplate.send() Future 미확인 (이전 리뷰 반복)
11. [HIGH] write amplification x3 — 동일 payload 두 번 직렬화 + outbox 2N건 INSERT
12. [HIGH] `(status, next_attempt_at)` partial index 미사용
13. [HIGH] log-ingest-service 인증/인가 전면 부재 (permitAll)
14. [HIGH] 입력 검증 전무 (limit 상한, message 길이 등) — 이슈 #26
15. [HIGH] ES 인덱스 키 UTC 고정 — KST 정책 미정 (이슈 #31 미해결)
16. [HIGH] 백오프 4회 = 80s+ 지연 → R001 5분 윈도우 미탐 위험
17. [HIGH] Testcontainers 통합 테스트 0건 — 이슈 #30
18. [HIGH] 패키지명 camelCase (`logEvent/`, `outbox/`)

### MEDIUM
19. [MEDIUM] CoroutineScope 생명주기 관리 부재 (log-generator)
20. [MEDIUM] IngestSendFileAdapter 동시 쓰기 안전성 (log-generator)
21. [MEDIUM] findRecent 하드코딩 100건 fetch
22. [MEDIUM] timestamp 신뢰 정책 + KST/UTC 경계 명세화 (이슈 #31)
23. [MEDIUM] `Outbox.createdAt: Instant?` + non-null default 모호. Clock 추상화 부재
24. [MEDIUM] OutboxPublisher.dispatch 흐름 복잡 (when + try/catch + return false)
25. [MEDIUM] dispatch 마다 readValue 역직렬화 (write/read 양쪽)
26. [MEDIUM] mark* row-by-row UPDATE → IN-list 일괄
27. [MEDIUM] ChannelType.CACHE MissingBranch
28. [MEDIUM] `outbox_aggregate_idx` INSERT 핫패스 비용
29. [MEDIUM] last_error 마스킹 강화 (호스트/시크릿 redactor)
30. [MEDIUM] aggregateId = eventId — Kafka 파티션 키 일관성 손실
31. [MEDIUM] fetchPending 정렬 (재시도 vs 신규 시계열 흔들림)
32. [MEDIUM] Publisher @Transactional 락 점유 회귀 테스트 부재

### LOW
33. [LOW] Pair<LogEvent, Instant> → 명명된 data class — 이슈 #28
34. [LOW] BACKOFF 매직 넘버 의도 주석
35. [LOW] native query schema 이중관리
36. [LOW] `:limit` 외부 입력 시 클램프
37. [LOW] ObjectMapper `activateDefaultTyping` 금지 정책 잠금
38. [LOW] payload 손상 시 즉시 markDead 정책 문서화
39. [LOW] design.md 에 KST/UTC + 채널 의미 명문화

## 반복 실수 패턴
1. 도메인 모델 간 타입 불일치 (Long vs Int vs Double)
2. jakarta vs springframework 어노테이션 혼동
3. **@Transactional 경계 내 외부 I/O(ES/Kafka) 혼입 — 퇴보**: Outbox 도입에도 dispatch 단계에서 동일 패턴 재현
4. **비동기 Future/응답 무시 (fire-and-forget 오용) — 퇴보**: KafkaTemplate.send Future 미확인
5. **컴파일 검증 없는 머지 — 신규**: enum/SQL/when 3중 불일치가 머지됨 → CI `gradlew build` 게이트 부재
6. **동일 사실 다중 소스 (SoT 부재) — 신규**: ChannelType (enum) ↔ V*.sql (CHECK) ↔ when 분기 3곳에 중복 정의

## 아키텍처 결정
- 각 서비스는 자체 도메인 모델 유지, common은 메시지 DTO 전용
- Handler는 @Bean 수동 등록, adapter는 @Component
- @Transactional은 handler에서 허용 (유일한 Spring 어노테이션 예외)
- **Outbox 패턴 도입** (단, 위 CRITICAL 이슈 해결 전까지 신뢰 불가)

## 도메인 정책 결정 필요 (design.md 영향)
1. 시간대 정책 — ES 인덱스 키 KST vs UTC, 룰 윈도우 정렬 통일
2. 룰별 SLA — R001(5분) 의 백오프 허용 한계
3. 어그리거트 정책 — BruteForce/Geo 의 파티션 키 (userId/ip)
4. 채널 의미 명문화 — KAFKA=탐지, ES=검색/감사
