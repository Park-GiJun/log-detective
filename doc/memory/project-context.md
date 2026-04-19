# 프로젝트 컨텍스트 (2026-04-19 기준)

## 구현 현황
- **Phase 0 (스켈레톤)**: 완료
- **Phase 1 (수집 파이프라인)**: 진행 중
  - log-generator: 시나리오 CRUD + REST/Kafka/File 전송 + Web Controller 완성, 상태 관리 Redisson 이관 중 (issue-11)
  - log-ingest-service: 헥사고날 전체 구현 완료 (REST + JPA + Kafka + ES), 리뷰 이슈 수정 필요
  - log-common: LogEvent, LogEventMessage, LogLevel, Severity 등 공유 모델
- **Phase 2 (탐지 엔진)**: 미착수 (스켈레톤 + Flyway 시드만)

## 기술 부채
1. [HIGH] Scenario.fraudRatio: Long → Double 타입 수정 필요
2. [HIGH] log-ingest-service 인증/인가 전면 부재 (permitAll)
3. [HIGH] @Transactional 내 ES/Kafka 동기 호출 → after-commit 분리 필요
4. [HIGH] Kafka send() Future 미처리 → 데이터 유실 위험
5. [HIGH] 입력 검증 전무 (limit 상한, message 길이 등)
6. [MEDIUM] CoroutineScope 생명주기 관리 부재
7. [MEDIUM] IngestSendFileAdapter 동시 쓰기 안전성
8. [MEDIUM] findRecent 하드코딩 100건 fetch
9. [MEDIUM] publishRawBatch 건건 send
10. [MEDIUM] timestamp 신뢰 정책 + KST/UTC 경계 명세화
11. [LOW] Pair<LogEvent, Instant> → 명명된 data class 교체

## 반복 실수 패턴
1. 도메인 모델 간 타입 불일치 (Long vs Int vs Double)
2. jakarta vs springframework 어노테이션 혼동
3. @Transactional 경계 내 외부 I/O(ES/Kafka) 혼입 — **신규**
4. 비동기 Future/응답 무시 (fire-and-forget 오용) — **신규**

## 아키텍처 결정
- 각 서비스는 자체 도메인 모델 유지, common은 메시지 DTO 전용
- Handler는 @Bean 수동 등록, adapter는 @Component
- @Transactional은 handler에서 허용 (유일한 Spring 어노테이션 예외)
