## 8. 구현 순서 (권장)

> **업데이트 2026-04-13**: Phase 1·2 완료, Phase 3 진행 중. 경로 이름은 현재 컨벤션(`application/port/inbound`, `{Resource}Handler`, `{Domain}{Infra}Port`) 기준으로 갱신됨.

### Phase 1: 기반 구축 🟢 완료
1. `docker compose up -d`로 인프라 기동 확인
2. Eureka Server + Config Server 기동 확인
3. fds-common Kafka 이벤트 스키마 확정 (TransactionEvent, DetectionResultEvent)
4. Gateway + Generator 구현 + 카드번호 마스킹 + SecurityConfig

### Phase 2: Transaction Service 🟢 완료
5. Flyway 마이그레이션 (`V1__create_transactions.sql` — transaction_id unique)
6. domain 레이어: `Transaction`(encrypted+masked, VO), `TransactionStatus`, `CountryCode`/`CurrencyCode`
7. application: `port/inbound/Register|GetTransactionUseCase`, `dto/command/RegisterTransactionCommand`, `handler/TransactionHandler` (@Transactional)
8. application port/outbound: `TransactionPersistencePort`, `CardEncryptor`
9. infrastructure/adapter/outbound: `TransactionPersistenceAdapter` (unique constraint catch), `PassthroughCardEncryptor` (@ConditionalOnProperty fail-closed)
10. infrastructure/adapter/inbound: `TransactionWebAdapter`, `GlobalExceptionHandler`, 로컬 `ApiResponse`
11. infrastructure/config: `TransactionApplicationConfig` (@Bean Handler + Clock + CardEncryptor), Logback %mask/%maskEx 컨버터
12. 테스트: 단위(Handler/VO/Command/ExceptionHandler/MaskConverter) + MockMvc WebAdapter = 36건
13. 🟡 미구현: TransactionMessagePort(Kafka Producer), TransactionSearchPort(ES), Testcontainers 통합 (Spring Boot 4 test slice 이슈)

### Phase 3: Detection Service 🟡 진행 중 (feat/detection-service-skeleton)
14. domain: `Detection`, `DetectionContext`, `RuleResult`, `UserBehaviorProfile`
15. domain/rule: `DetectionRule` 인터페이스 + `HighAmountRule` / `RapidSuccessionRule` / `GeoImpossibleTravelRule` / `MidnightTransactionRule`
16. application: `port/inbound/EvaluateTransactionUseCase`, `dto/command/EvaluateTransactionCommand`, `handler/DetectionHandler`
17. application port/outbound: `UserBehaviorCachePort` (Redis), `DetectionResultPersistencePort` (JPA), `DetectionResultMessagePort` (Kafka)
18. infrastructure/adapter/outbound/cache: `UserBehaviorRedisAdapter` — **Sorted Set + Lua 원자화** (INCR+TTL 금지)
19. infrastructure/adapter/outbound/persistence: `DetectionResultEntity`/Repo/Adapter
20. infrastructure/adapter/outbound/message: `DetectionResultKafkaAdapter`
21. infrastructure/adapter/inbound/message: `TransactionEventConsumer` — `concurrency: 12+`, `trusted.packages` 구체 FQCN
22. infrastructure/adapter/inbound/web: `DetectionWebAdapter` + `GlobalExceptionHandler` + 로컬 `ApiResponse`
23. infrastructure/config: `DetectionApplicationConfig` (@Bean Handler + Rule 리스트), `RuleConfig`, `KafkaConsumerConfig`, `KafkaProducerConfig`, `RedisConfig`
24. 테스트: 각 Rule 단위, Handler MockK, Kafka Embedded(또는 Testcontainers), Redis Testcontainers

### Phase 4: Alert Service
25. Flyway 마이그레이션 (`V1__create_alerts.sql`)
26. domain/application/infrastructure 구현 (transaction과 동일 컨벤션 적용)
27. Redis 중복 방지 — **Lua 스크립트 원자화** (SISMEMBER+SADD+EXPIRE 조합)
28. Kafka Consumer (`detection-results` 토픽)
29. Notification 포트 (1차: 로그 출력, 2차: Slack/Email 확장)

### Phase 5: Gateway 연동 + E2E
30. Generator → Gateway → Transaction → Kafka → Detection → Kafka → Alert 전체 플로우
31. Eureka Config Server SPOF 대응 계획 (다중 노드)
32. 통합 테스트 시나리오 (`doc/schemas/k6-test-scenarios.md`)

### Phase 6: 성능 테스트 및 최적화
33. k6 부하 테스트 스크립트 작성
34. 병목 분석: HikariCP 풀 크기, Kafka 파티션, ES 힙·벌크 인덱싱, Redis maxmemory
35. KMS CardEncryptor 구현체 도입 (`doc/policy/kms-encryption.md` 참조)
36. CommonApiResponse → 각 서비스 로컬 ApiResponse 분리 마무리
37. 결과 문서화 (README 성능 수치)

---
