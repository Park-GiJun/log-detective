# @Transactional 경계와 외부 I/O

## 원칙
`@Transactional` 안에서 외부 시스템(Kafka, Elasticsearch, HTTP) 호출을 하면 안 된다.

## 이유
- DB 트랜잭션은 DB만 롤백한다. ES/Kafka는 롤백되지 않는다.
- 외부 I/O 실패 시 DB도 롤백되면서 정상 데이터가 유실된다.
- 외부 I/O 지연이 DB 커넥션 점유 시간에 직접 영향 → HikariCP 풀 고갈.

## 해결 패턴
1. **After-Commit Hook**: `TransactionSynchronizationManager.registerSynchronization()`으로 커밋 후 실행
2. **Outbox 패턴**: DB에 outbox 테이블 저장 → 별도 프로세스가 발행
3. **이벤트 기반**: `@TransactionalEventListener(phase = AFTER_COMMIT)`

## 관련 리뷰
- 2026-04-19 b48306a — LogEventCommandHandler에서 @Transactional 내 ES+Kafka 동기 호출 발견
