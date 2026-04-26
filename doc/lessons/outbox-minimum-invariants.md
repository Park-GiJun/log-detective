# Outbox 패턴 적용 시 최소 불변식

> 출처: 리뷰 e1ae759 (2026-04-26) — 한소율 (Quality Lead) 학습 포인트

## 안티패턴

Outbox 패턴을 "코드 도입" 으로 끝내고 다음 6종 불변식 중 어느 하나도 테스트로 고정하지 않음:

1. **멱등성 키** — 동일 eventId 가 두 번 발행되어도 컨슈머가 한 번만 처리
2. **파티션 키** — 도메인 어그리거트(userId/ip) 기준 순서 보장
3. **백오프 진행** — N회 실패 시 5s × 2^n 으로 nextAttemptAt 증가
4. **DEAD 전이** — MAX_ATTEMPTS 초과 시 DEAD 로 정착
5. **FAILED 재시도** — fetchPending 이 PENDING + (FAILED && next_attempt_at ≤ now) 모두 fetch
6. **동시성 격리** — 복수 인스턴스가 SKIP LOCKED 로 서로 다른 행을 처리

이번 PR 은 6종 모두 테스트 부재. 특히 **#5 가 SQL 결함으로 미동작** — FAILED 행이 영원히 재시도 안 됨.

## 교훈

> Outbox 의 산출물은 "코드" 가 아니라 "불변식 6종 보장" 이다.

테스트 없이 도입된 Outbox 는:
- "DB/ES/Kafka 정합 보장" 이라는 **마케팅 메시지만 남고**
- 실제로는 부분 실패 케이스에서 데이터 유실/중복/순서 깨짐을 **모두 허용**한다.

테스트 없는 Outbox = **부채 가속기** (도입 비용 + 유지 비용 + 거짓 안심 비용).

## 적용 룰

신규 Outbox 도입 시 PR 머지 조건:

- [ ] 6종 불변식 각각에 대한 단위/통합 테스트 존재
- [ ] FAILED 재시도 SQL 이 정확한지 회귀 테스트 (단위 테스트 가능)
- [ ] Testcontainers 통합 테스트로 SKIP LOCKED 동시성 검증
- [ ] 컨슈머측 멱등성 보장 메커니즘 명시 (eventId 기준 중복 제거 위치)
- [ ] Publisher `@Transactional` 경계가 외부 IO 를 포함하지 않는다는 회귀 테스트
