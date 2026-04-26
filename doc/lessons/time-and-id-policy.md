# 시간 추상화와 식별자 책임 분리

> 출처: 리뷰 e1ae759 (2026-04-26)

## 1. 시간 추상화 부재의 누적 비용

### 안티패턴

`Instant.now()` 를 코드 곳곳에서 직접 호출 + UTC 인덱스 키 고정 + 백오프 시계열이 시간 정책에 의존.

이번 PR 의 동시 발현:
- `Outbox.newPending` 에서 `Instant.now()` 가 두 번 호출 (nextAttemptAt, createdAt)
- ES 인덱스 키를 `ZoneOffset.UTC` 로 고정 → KST 운영 시 자정 경계에서 하루치 이벤트가 다른 인덱스로 분산
- 백오프 nextAttemptAt 계산도 `Instant.now()` 직접 — 테스트에서 "10분 후" 시뮬레이션 불가

### 교훈

> 시간 정책은 도메인 결정이지 인프라 디테일이 아니다.

- **Clock 포트** 도입 — 도메인 계층에 `Clock` 추상화. 테스트는 `FixedClock` 으로 결정성 확보.
- **타임존 정책** 단일 명문화 — 인덱스 키, 룰 윈도우, 보관 기한, 자정 경계 처리를 design.md 에 한 번만 결정.
- **시계열 데이터의 KST/UTC 변환은 표시 계층에서만** — 저장은 UTC, 인덱스 키는 운영 정책에 따라.

## 2. 식별자 책임 분리 — eventId vs aggregateId

### 안티패턴

Outbox 행에서 `aggregateId = eventId.toString()` — 멱등성 키와 파티션 키를 한 필드로 겸용.

### 교훈

> Outbox 행에는 두 종류의 식별자가 모두 필요하다.

| 필드 | 책임 | 본 프로젝트 권장 값 |
|------|------|---------------------|
| `eventId` | **멱등성 키** — 컨슈머가 중복 차단 | UUID (이벤트 단위) |
| `aggregateId` | **파티션/순서 키** — Kafka 파티션 일관성, 룰별 어그리거트 단위 보장 | `userId` 또는 `ip` |

룰별 어그리거트가 다르면(BruteForce=ip, Geo=userId, RareEvent=userId+source 등) Outbox 행이 룰별로 분리되어야 할 수도 있음 — design.md 에서 결정.

### 적용 룰

- 멱등성 키와 파티션 키를 한 필드에 묶지 않는다.
- 어그리거트 결정은 도메인 룰(R001~R006) 기반.
- 컨슈머측 멱등성 처리 위치를 코드/주석/테스트 중 한 곳에 명시한다.

---

## 3. 컨벤션은 사람 리뷰가 아니라 정적 검증으로

### 안티패턴

- camelCase 패키지명 (`logEvent/`, `outbox/`) — Kotlin/Java 표준은 all-lowercase
- ES 인덱스 명명규약이 application Handler 에 위치 — 헥사고날 경계 누수
- ChannelType enum, SQL CHECK, when 분기가 동일 사실을 3곳에 중복 정의 (SoT 부재)

### 교훈

> 사람 리뷰가 같은 컨벤션을 두 번 이상 잡으면, 그것은 도구로 옮길 시점이다.

- detekt `PackageNaming` 룰 활성화
- ArchUnit 으로 application 계층의 jakarta/hibernate/elasticsearch import 금지
- enum ↔ DDL CHECK 정합성은 통합 테스트 1건으로 회귀 방지 가능
