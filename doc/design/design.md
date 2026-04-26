# log-detective 설계문서

## 1. 개요

**log-detective**는 대량의 애플리케이션/시스템 로그에서 보안 위협·이상 징후·장애 패턴을 **실시간 탐지**하고 알림을 발송하는 시스템이다. MSA + 이벤트 기반 + 헥사고날 아키텍처(Port & Adapter)를 기반으로 도메인 로직과 인프라를 분리하고, 규칙 기반 탐지 엔진과 Compose Web 대시보드를 포함한다.

### 1.1 왜 만드는가
- 로그는 많지만 사람이 다 볼 수 없다 → 규칙 기반 + 통계 기반 탐지가 필요
- Kafka 파이프라인과 헥사고날 아키텍처를 실전 도메인에 적용해 MSA/이벤트 기반 시스템 설계 역량을 드러낸다
- Compose Web 프론트를 붙여 탐지 결과를 시각적으로 확인/시연

### 1.2 범위
- **포함**: 로그 수집, 영속화, 검색, 규칙 기반 탐지, 알림 발송, 대시보드
- **제외**: ML 기반 이상탐지(향후 확장), 로그 수집 에이전트(Fluentd/Filebeat 대체 없음 — Generator가 대체), 분산 트레이싱 연동(Zipkin은 내부 관측용으로만)

---

## 2. 아키텍처

### 2.1 전체 구성도

```
                    ┌──────────────────┐
                    │  compose-web     │  :3003 (테스트/시각화)
                    │  (Kotlin/Wasm)   │
                    └────────┬─────────┘
                             │ REST
                             ▼
  ┌──────────────┐    ┌──────────────┐
  │ log-generator│───▶│ log-gateway  │ :8084
  │   :28090     │    │ (Cloud GW)   │
  └──────────────┘    └──────┬───────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
  ┌──────────┐         ┌──────────┐         ┌──────────┐
  │  ingest  │ :28081  │ detection│ :28082  │  alert   │ :28083
  │ service  │         │ service  │         │ service  │
  └────┬─────┘         └────┬─────┘         └────┬─────┘
       │                    │                    │
       │  logs.raw          │  logs.detected     │  alerts.dispatched
       └──────────┬─────────┴─────────┬──────────┘
                  ▼                   ▼
            ┌─────────────────────────────┐
            │          Kafka (KRaft)       │
            └─────────────────────────────┘

            ┌────────────┐   ┌────────────┐   ┌────────────┐
            │ PostgreSQL │   │    Redis   │   │Elasticsearch│
            │ (logdetect)│   │ (Redisson) │   │   +Kibana   │
            └────────────┘   └────────────┘   └────────────┘

                    ┌──────────────────────────┐
                    │ log-eureka-server :28761 │
                    │ (Discovery + Config)     │
                    └──────────────────────────┘
```

### 2.2 통신 방식

| 경로 | 방식 | 이유 |
|---|---|---|
| Generator → Ingest | HTTP (Ktor Client) | 외부 로그 소스 시뮬레이션 |
| Ingest → Detection | Kafka (`logs.raw`) | 비동기·버퍼링·리플레이 |
| Detection → Alert | Kafka (`logs.detected`) | 탐지 결과 팬아웃 |
| Alert → 외부 채널 | Kafka (`alerts.dispatched`) + Webhook | 확장성 |
| Service → DB | JPA (sync) | 트랜잭션 필요 경로만 |
| Service → Redis | Redisson | 분산락·rate limit·중복제거 |
| 모든 서비스 → Eureka/Config | Spring Cloud | 서비스 디스커버리 + 중앙 설정 |

---

## 3. 모듈 책임

### 3.1 log-common
- 공통 도메인 타입, Kafka 메시지 DTO, 공통 Exception, 유틸
- 어떤 Spring 의존성도 두지 않음 (순수 Kotlin 라이브러리)

### 3.2 log-eureka-server (:28761)
- Netflix Eureka + Spring Cloud Config Server 겸용
- 모든 서비스의 `application.yml`은 여기로부터 datasource/kafka/redis 설정을 pull

### 3.3 log-gateway (:8084)
- Spring Cloud Gateway (MVC)
- 외부 진입점: `/api/logs/**` → ingest, `/api/detections/**` → detection, `/api/alerts/**` → alert
- Caffeine 기반 로컬 캐시로 토큰/레이트리밋 경량 처리

### 3.4 log-ingest-service (:28081)
**책임**: 로그 수집 + 영속화 + ES/Kafka 발행 (Outbox 패턴으로 트랜잭션 보장)
- `POST /api/logs` / `POST /api/logs/batch` 엔드포인트
- **Handler 흐름** — `log_events` INSERT 와 `outbox_messages` INSERT (ES + KAFKA 두 행) 를 같은 `@Transactional` 안에서 수행. 외부 시스템 (ES / Kafka) 직접 호출 없음.
- **OutboxPublisher** (`infrastructure/scheduler`) — `@Scheduled` 1초 폴링으로 PENDING 행을 `SELECT FOR UPDATE SKIP LOCKED` 로 가져와 channel 별로 dispatch (`logs.raw` Kafka 토픽 / `logs-YYYY.MM.DD` ES 인덱스). 실패 시 지수 백오프, 5회 초과 시 DLQ(status=DEAD).
- 결과: DB 커밋되면 발행은 결국 일어남(at-least-once). ES/Kafka 컨슈머는 `eventId` 로 멱등성 보장.
- Flyway로 스키마 마이그레이션 관리 (`log_events`, `outbox_messages`)

### 3.5 log-detection-service (:28082)
**책임**: 규칙 기반 탐지 엔진
- `logs.raw` 컨슈머 → 각 규칙 평가 → 매치 시 `logs.detected` 발행
- Redisson으로 슬라이딩 윈도우 카운트 (BruteForceRule 등)
- 규칙 정의는 `application.yml` / Config Server에서 동적 로드
- Ktor Client로 외부 IP geolocation API 호출 (GeoAnomalyRule)

### 3.6 log-alert-service (:28083)
**책임**: 알림 집계·중복제거·영속화·발송
- `logs.detected` 컨슈머 → Redisson으로 alert fingerprint 중복 체크
- `alerts` 테이블에 영속화 (PostgreSQL)
- Webhook/Slack/Email 디스패치는 전략 패턴 (초기엔 로그 출력만)
- `alerts.dispatched` 발행으로 하위 시스템 팬아웃 여지 확보

### 3.7 log-generator (:28090)
**책임**: 시나리오 기반 로그 트래픽 시뮬레이터
- Kotlin Coroutine 기반 동시 전송
- **`Scenario` 도메인을 1차 입력으로 사용** — `name / type:RequestType(KAFKA|FILE|REST) / attackType / successful / rate / fraudRatio[0~100%]`
- Generator 시작 시 `scenarioId` 만 받아 시나리오 정의대로 동작 (rate=EPS, fraudRatio/100=의 비율로 사기 이벤트 생성)
- **시나리오별 독립 실행** — 인메모리 `ConcurrentMap<Long, Job>` + Redisson 키 namespace `generator:state:{scenarioId}:*`, 동시에 여러 시나리오 가동 가능
- AttackType 6종 × `successful` true/false 분기로 6규칙(R001~R006) 모두 정상/실패 변형 합성
- RequestType 분기에 따라 REST(`IngestSendClientPort`) / KAFKA(`IngestSendMessagePort`) / FILE(`IngestSendFilePort`) 어댑터 선택

### 3.8 compose-web (:3003, 프론트)
**책임**: 테스트/시각화 대시보드
- Kotlin/Wasm + Compose Multiplatform 1.10.3
- 초기 화면: 최근 로그 스트림, 탐지/알림 카운터, 규칙 on/off 토글
- API는 log-gateway를 통해 호출 (CORS는 게이트웨이에서 허용)

---

## 4. 데이터 모델

### 4.1 PostgreSQL (`logdetect` 데이터베이스)

```
log_events                    alerts
─────────────                 ──────
id              BIGINT PK     id                BIGINT PK
event_id        UUID          detection_id      UUID
source          VARCHAR       rule_id           VARCHAR
level           VARCHAR       severity          VARCHAR
message         TEXT          fingerprint       VARCHAR UNIQUE
timestamp       TIMESTAMPTZ   first_seen_at     TIMESTAMPTZ
host            VARCHAR       last_seen_at      TIMESTAMPTZ
ip              INET          hit_count         INT
user_id         VARCHAR       status            VARCHAR
attributes      JSONB         dispatched_at     TIMESTAMPTZ
                              payload           JSONB

detection_rules               outbox_messages
───────────────               ───────────────
id              VARCHAR PK    id                BIGINT PK (identity)
name            VARCHAR       aggregate_id      VARCHAR(64)
enabled         BOOLEAN       channel           VARCHAR(16)  -- ES | KAFKA | FILE | OTHERS
severity        VARCHAR       destination       VARCHAR(255) -- ES index 또는 Kafka topic
config          JSONB         payload           JSONB
updated_at      TIMESTAMPTZ   status            VARCHAR(16)  -- PENDING | PUBLISHED | FAILED | DEAD
                              attempts          INT
                              next_attempt_at   TIMESTAMPTZ
                              created_at        TIMESTAMPTZ
                              published_at      TIMESTAMPTZ
                              last_error        TEXT
```

> JPA entity는 각 서비스(`log-ingest-service`, `log-alert-service`, `log-detection-service`)에 분산. 마이그레이션은 **소유 서비스**의 Flyway 리소스에 둔다.

**`outbox_messages`** (log-ingest-service 소유) — DB / ES / Kafka 트랜잭션 보장용 Outbox 패턴.
Handler 가 LogEvent save 와 같은 트랜잭션에서 outbox row 를 append → 별도 Publisher 가 폴링(`SELECT FOR UPDATE SKIP LOCKED`)하여 channel 별로 dispatch. DB 커밋되면 발행은 결국 일어남(at-least-once), 컨슈머는 멱등성 보장.

### 4.2 Elasticsearch
- 인덱스 패턴: `logs-YYYY.MM.DD` (일 단위 롤오버)
- 매핑: `timestamp`, `source`, `level`, `message`(text), `ip`, `host`, `user_id`, `attributes`(flattened)
- Kibana로 임시 조회/대시보드

### 4.3 Kafka 토픽
| 토픽 | 키 | 값 | 파티션 전략 |
|---|---|---|---|
| `logs.raw` | `source` | `LogEventMessage` | source별 해시 |
| `logs.detected` | `rule_id` | `DetectionMessage` | rule별 해시 |
| `alerts.dispatched` | `alert_id` | `AlertMessage` | 라운드로빈 |

### 4.4 Redis (Redisson)
| 용도 | 키 패턴 | 타입 |
|---|---|---|
| 슬라이딩 윈도우 카운트 | `rule:{ruleId}:{dimension}` | `RScoredSortedSet` |
| 알림 중복제거 fingerprint | `alert:dedupe:{fingerprint}` | `RBucket<TTL>` |
| 분산락 | `lock:rule:{ruleId}` | `RLock` |
| 규칙 실행 rate limit | `rate:{ruleId}` | `RRateLimiter` |

---

## 5. 탐지 규칙 (초기 6개)

| ID | 이름 | 트리거 조건 | Severity |
|---|---|---|---|
| `R001` | **BruteForceLoginRule** | 동일 IP 기준 5분 내 로그인 실패 ≥10회 | HIGH |
| `R002` | **SqlInjectionPatternRule** | `message`에 SQLi 시그니처 매칭 (`UNION SELECT`, `OR 1=1` 등) | HIGH |
| `R003` | **ErrorRateSpikeRule** | 서비스별 ERROR 로그 비율이 직전 1시간 평균의 3배 초과 | MEDIUM |
| `R004` | **OffHourAccessRule** | 지정된 관리 계정의 00:00~05:00 로그인 성공 | MEDIUM |
| `R005` | **GeoAnomalyRule** | 동일 user_id가 1시간 내 물리적으로 불가능한 거리에서 접근 | HIGH |
| `R006` | **RareEventRule** | 과거 30일간 관측된 적 없는 `source + level + pattern` 조합 | LOW |

각 규칙은 `DetectionRule` 인터페이스를 구현하며, 설정은 `detection_rules.config` JSONB로 외부화.

```kotlin
interface DetectionRule {
    val id: String
    fun evaluate(event: LogEvent, context: DetectionContext): RuleResult
}
```

---

## 6. 인프라 / 포트 맵

| 구성 요소 | 포트 | 비고 |
|---|---|---|
| log-eureka-server | 28761 | Eureka + Config |
| log-ingest-service | 28081 | |
| log-detection-service | 28082 | |
| log-alert-service | 28083 | |
| log-gateway | 8084 | 외부 진입점 (유일한 1XXXX 미만) |
| log-generator | 28090 | |
| compose-web (dev) | 3003 | Webpack dev server |
| PostgreSQL | 5432 | `logdetect` DB |
| Redis | 6379 | Redisson |
| Kafka | 9092 | KRaft 모드 |
| Elasticsearch | 9200 | |
| Kibana | 5601 | |
| Zipkin | 9411 | |
| Kafka UI | 9090 | |

> docker-compose는 현재 사용하지 않음. 이미 구동 중인 원격 공유 인프라에 `.env`로 접속하거나 로컬 네이티브 설치를 사용한다.

---

## 7. 기술 스택

| 계층 | 기술 | 버전 |
|---|---|---|
| 언어 | Kotlin | 2.3.20 |
| JVM | JDK (Microsoft Build) | 25 |
| 빌드 | Gradle | 9.4.0 |
| 백엔드 | Spring Boot | 4.0.5 |
| 마이크로서비스 | Spring Cloud | 2025.1.1 |
| ORM | Spring Data JPA | BOM 관리 |
| 마이그레이션 | Flyway | BOM 관리 |
| HTTP Client | Ktor Client | 3.4.0 |
| 캐시 | Caffeine | 3.2.0 |
| 분산 락/자료구조 | Redisson | 3.52.0 |
| DB | PostgreSQL | 17 |
| 검색 | Elasticsearch | 8.17 |
| 메시징 | Apache Kafka | 4.0 (KRaft) |
| 프론트엔드 | Compose Multiplatform | 1.10.3 |
| 타겟 | Kotlin/Wasm (wasmJs) | — |
| 테스트 | Kotest / MockK / Testcontainers | 6.1.0 / 1.14.2 / 1.21.0 |

---

## 8. 로드맵

### Phase 0 — 스켈레톤 ✅
- 모듈 구조, 포트, buildSrc convention plugin, 기본 Application 클래스

### Phase 1 — 수집 파이프라인 (P0)
1. `log-common`에 `LogEventMessage`, `DetectionMessage`, `AlertMessage` DTO 정의
2. `log-ingest-service`: REST 엔드포인트 + JPA 엔티티 + Kafka Producer + ES 저장
3. `log-generator`: 정상 트래픽 시뮬레이션만 우선 구현
4. Flyway V1__init.sql (log_events 테이블)

### Phase 2 — 탐지 엔진 (P0)
1. `DetectionRule` 추상 + Registry
2. R001 (BruteForce), R002 (SQLi) 먼저 구현
3. Redisson 슬라이딩 윈도우
4. `logs.raw` 컨슈머, `logs.detected` 프로듀서

### Phase 3 — 알림 (P1)
1. `log-alert-service` JPA + fingerprint 중복제거
2. 로그 출력 기반 더미 디스패처
3. Webhook 어댑터

### Phase 4 — 게이트웨이 & 프론트 (P1)
1. `log-gateway` 라우팅 + CORS
2. `compose-web` 대시보드: 실시간 카운터, 최근 알림 리스트
3. SSE 또는 WebSocket로 push 고려

### Phase 5 — 고급 규칙 (P2)
1. R003 (ErrorRateSpike), R004 (OffHour), R005 (Geo), R006 (RareEvent)
2. 규칙 on/off 토글 UI
3. Generator에 공격 패턴 시뮬레이션 추가

### Phase 6 — 관측/운영 (P2)
1. Zipkin 분산 추적 연동
2. Micrometer + Prometheus 커스텀 메트릭 (탐지율, 알림률)
3. Kibana 대시보드

---

## 9. 주요 설계 결정

| 결정 | 이유 |
|---|---|
| **단일 DB `logdetect`** | 초기 단순성 > 서비스별 DB 분리. Phase 6에서 분리 검토 |
| **Ingest가 ES에 직접 쓰기** | Kafka → ES Sink Connector를 별도 운영하지 않기 위함. 트레이드오프: ingest 지연 |
| **JPA ddl-auto=validate + Flyway** | 프로덕션과 동일한 스키마 관리 관행 학습 목적 |
| **Redis → Redisson 교체** | `RLock`, `RRateLimiter` 같은 고수준 자료구조 재사용. Spring Data Redis보다 DSL 편의성 우선 |
| **Compose Web (Wasm)** | 프론트도 Kotlin으로 유지 — 학습 폭 확대, 타입 공유 가능성 |
| **Spring Boot 4.0.5 + Kotlin 2.3.20** | 공식 지원은 Spring Boot 4.1부터지만, `extra["kotlin.version"]` 오버라이드로 선행 적용 |
| **Config Server는 Eureka 서버에 합체** | 운영 노드 최소화 및 기동 순서 단순화 |

---

## 10. 시간대 정책

> 출처: 이슈 #31, #66, 리뷰 2026-04-26 e1ae759 (윤지아 Domain Expert / 한소율 Quality Lead)

### 10.1 원칙

| 항목 | 정책 |
|---|---|
| **저장 (DB / Kafka payload)** | **UTC** 고정 (`TIMESTAMPTZ` / `Instant`) |
| **표시 (대시보드 / 리포트)** | **KST(UTC+9)** — 사용자 노출 시점에 변환 |
| **룰 윈도우 정렬** | UTC — 모든 슬라이딩 윈도우 / 비율 계산은 UTC 기준 |
| **`LogEvent.timestamp`** | **클라이언트 입력 신뢰** — 외부 로그 소스가 기록한 발생 시각 |
| **`LogEvent.ingestAt` (서버 시각)** | 서버 UTC `Instant.now()` — 신뢰 가능한 기준선 |

### 10.2 KST/UTC 경계 처리

- ES 인덱스 키 `logs-yyyy.MM.dd` 는 **UTC 기준** (`LogEventCommandHandler.kt` 의 `event.timestamp.atOffset(ZoneOffset.UTC)`)
- `R004 OffHourAccessRule` 의 "00:00~05:00 관리자 접근" 은 **KST 의도** — 룰 평가 시점에 `ZoneId.of("Asia/Seoul")` 로 변환하여 비교한다 (저장은 UTC 유지)
- 클라이언트 timestamp 가 미래 시각이거나 과도하게 과거(예: 24시간 초과)인 경우 ingest 단계에서 거부 또는 `ingestAt` 로 보정 — TODO

### 10.3 알려진 영향 (TODO)

- [ ] **R001 BruteForce (5분 윈도우)** — KST 자정(15:00 UTC) 경계에서 동일 IP 이벤트가 다른 ES 인덱스로 분산. UTC 윈도우 정렬을 유지하므로 룰 정확성에는 영향 없으나, ES 검색/감사 시점에 인덱스 가로질러 조회 필요
- [ ] **R004 OffHour** — UTC 인덱스에 저장된 KST 시간대 이벤트를 룰 평가 시 정확히 매핑하는지 통합 테스트로 보강
- [ ] 운영 정책이 KST 인덱스를 요구할 경우, ES 인덱스 키 산출을 `ZoneId.of("Asia/Seoul")` 로 전환할지 재검토 (저장 정책은 UTC 유지)

---

## 11. 채널별 의미

> 출처: 이슈 #66 — Outbox `channel` 컬럼 (`ES | KAFKA | FILE | OTHERS`) 의 도메인 의미 명문화

| 채널 | 의미 | 소비자 | 비고 |
|---|---|---|---|
| **KAFKA** | **탐지 입력** — 실시간 룰 엔진의 1차 진입점 | `log-detection-service` (`logs.raw` 컨슈머) | 발행 SLA 가 가장 짧음 (R002 < 10s) |
| **ES** | **검색 / 감사** — Kibana 조회, 사후 포렌식, 컴플라이언스 보존 | 사람 / 외부 도구 | 발행 SLA 비교적 여유 있음 (R003/R005/R006 < 5분) |
| **FILE** | **확장 슬롯** — 외부 시스템(SIEM, 로그 아카이빙, 콜드 스토리지) 연동 예약 | 미정 | Phase 6 이후 도입 검토. 현재 코드 경로 비활성 |
| **OTHERS** | **확장 슬롯** — Webhook, 외부 API push, 비표준 어댑터 예약 | 미정 | 예: 외부 보안팀 SOC, 3rd-party 위협 인텔 |

원칙:
- **KAFKA = 탐지 정확성에 직결** → SLA 위반 시 미탐 위험 (이슈 #50 참조)
- **ES = 사후 분석에 직결** → 일시적 지연 허용, 단 결국 발행 보장(at-least-once)
- 신규 채널 추가 시 위 표에 의미 / 소비자 / SLA 명시 후 코드 반영

---

## 12. 어그리거트 정책

> 출처: 이슈 #58, #66 — `Outbox.aggregateId` 의 도메인 책임 명확화 (윤지아 Domain Expert D3)

### 12.1 식별자 책임 분리

| 식별자 | 책임 | 사용처 |
|---|---|---|
| **`eventId` (UUID)** | **멱등성 키** — 컨슈머 중복 제거 | ES `_id`, Detection 컨슈머 dedupe, 사후 추적 |
| **`aggregateId`** | **파티션 / 순서 키** — 어그리거트 단위 직렬화, Kafka 파티션 일관성 | Kafka `ProducerRecord.key`, Outbox 폴링 그룹화 |

같은 어그리거트의 이벤트는 같은 파티션으로 흘러 시간 순서가 보장되어야 한다. 멱등성 키와 파티션 키를 한 필드에 겸용하면 향후 "동일 사용자 이벤트 순서 보장" 채널 추가 시 일관성이 깨진다.

### 12.2 룰별 어그리거트

| 룰 | 어그리거트 키 | 근거 |
|---|---|---|
| **R001 BruteForce** | `ip` | 동일 IP 의 5분 시도 횟수가 룰 단위 |
| **R002 SQLi** | `eventId` (어그리거트 없음) | 단건 패턴 매칭 — 순서 무관 |
| **R003 ErrorSpike** | `source` | 서비스(소스) 단위 ERROR 비율 |
| **R004 OffHour** | `userId` | 사용자 단위 시간대 접근 |
| **R005 Geo** | `userId` | 사용자 단위 위치 이동 추적 |
| **R006 RareEvent** | `userId + source` | 사용자×서비스 단위 희귀도 |

### 12.3 알려진 영향 (TODO)

- [ ] 현재 `LogEventCommandHandler.outboxesFor` 는 `aggregateId = event.eventId.toString()` 로 설정 — 위 정책에 맞춰 도메인 어그리거트 키 (룰별 분기) 로 변경 필요
- [ ] Outbox 도메인에 `idempotencyKey` 필드 분리 신설 (= `eventId` 그대로 보존) → Kafka send 시 `aggregateId` 를 파티션 키로, 컨슈머는 `idempotencyKey` 로 dedupe
- [ ] 룰별 어그리거트가 다르므로, ingest 단계에서 단일 키를 정할 수 없음 → 채널/룰별로 별도 outbox row 를 두거나, 컨슈머 측에서 re-key 하는 방식 결정 필요 (설계 결정 사항)
- 참조: `doc/lessons/time-and-id-policy.md`

---

## 13. 룰별 SLA

> 출처: 이슈 #50, #66 — 발행 지연이 탐지 정확성에 미치는 영향 (윤지아 Domain Expert D2)

### 13.1 발행 SLA 표

| 룰 | 윈도우 | 허용 발행 지연 (`logs.raw` 도착 기준) |
|---|---|---|
| **R001 BruteForce** | 5분 | **< 30s** |
| **R002 SQLi** | 즉시 (단건) | **< 10s** |
| **R003 ErrorSpike** | 1시간 (비율) | **< 5분** |
| **R004 OffHour** | 시간대 (KST) | **< 1분** |
| **R005 Geo** | 1시간 | **< 5분** |
| **R006 RareEvent** | 1일 | **< 5분** |

"발행 지연" = `LogEvent.timestamp` 와 `logs.raw` 컨슈머 도착 시각의 차이.

### 13.2 현재 구현 vs SLA

- **OutboxPublisher 백오프**: `5s × 2^n`, `MAX_BACKOFF_SHIFT = 6` → **상한 5분 (5 × 2^6 = 320s)**
- 4회 실패 시 80s+ 지연 — **R001 30s SLA 위반**, **R002 10s SLA 위반**, **R004 1분 SLA 위반 가능**
- R003/R005/R006 (5분 허용) 은 현재 백오프 상한 안에 들어옴

### 13.3 해결 방향 (TODO)

- [ ] **옵션 A — high-priority outbox 채널 분리**
  - SLA < 1분 룰(R001/R002/R004)을 별도 큐(`outbox_messages_high`)로 분리, 폴링 주기/백오프 다르게 설정
  - 장점: 룰별 SLA 차등 보장 / 단점: 운영 복잡도 증가
- [ ] **옵션 B — 백오프 상한 단축**
  - `MAX_BACKOFF_SHIFT = 6` → 3 (5 × 2^3 = 40s) 또는 2 (20s) 로 단축, `MAX_ATTEMPTS` 를 늘려 총 재시도 시간 보존
  - 장점: 단일 채널 유지 / 단점: 외부 시스템 장애 시 부하 증가
- [ ] **옵션 C — 룰 윈도우 보정**
  - 컨슈머 측에서 늦게 도착한 이벤트를 grace period(예: +30s)로 윈도우 재평가
  - 장점: 발행 측 무관 / 단점: 룰 엔진 복잡도 증가, 알림 지연

현재 단계에서는 **옵션 B (백오프 상한 단축) 우선** 검토 — 운영 복잡도 최소화. SLA 미달 사례가 누적되면 옵션 A 로 전환.

---

## 14. 보안 / 인증 모델

> 출처: 이슈 #86, #112 — service-to-service 인증 정책 명문화 (윤지아 Domain Expert / 박서진 Security)

### 14.1 service-to-service 인증 (Phase 1~3)

내부 서비스 간 호출 (Generator → Ingest) 은 **API Key 헤더 인증** 으로 처리한다.
JWT/OAuth 인프라를 도입하지 않는 이유 — internal 망 / 호출 주체가 1~N 개로 한정 / 운영 복잡도 최소화.

| 항목 | 정책 |
|---|---|
| **헤더명** | `X-API-Key` — `log-common` 의 `ApiKeyConstants.HEADER_NAME` 단일 소스 |
| **저장** | 환경변수 `INGEST_API_KEYS` (comma-separated, multi-key) — 평문 저장 금지, 비밀 관리 시스템 권장 |
| **비교** | `MessageDigest.isEqual` timing-safe 비교 — 측면 채널 추론 차단 |
| **실패 응답** | `401 Unauthorized` + `ApiErrorResponse` JSON 본문 (헤더 값 재노출 금지) |
| **헬스체크** | `/actuator/health/**`, `/actuator/info` 는 인증 우회 (LB 헬스체크용) |

### 14.2 Rotation 정책 (zero-downtime)

키 누설 / 정기 회전 시 무중단 교체:

1. **신키 추가** — `INGEST_API_KEYS=oldKey,newKey` (양키 동시 활성)
2. **호출자 전환** — Generator 쪽 `INGEST_API_KEY` 를 newKey 로 교체 → 재시작
3. **구키 제거** — `INGEST_API_KEYS=newKey` 단일화 → Ingest 재시작

> 현재 구현(`logdetect.ingest.api-key`) 은 **단일 키 only** — multi-key 지원은 후속 이슈로 분리.

### 14.3 Rate-limit / lockout

브루트포스 / 사전 공격 방어:

| 항목 | 정책 |
|---|---|
| **단위** | IP 단위 (Authentication 실패 ip 기준) |
| **임계** | 5회 실패 / 5분 윈도우 |
| **lockout** | 5분 차단 — 차단 중에도 401 반환 (in/out 동일 응답) |
| **저장** | Redisson `RRateLimiter` — Phase 1.5 도입 예정 (현재 미구현) |

### 14.4 클라이언트 식별

향후 호출자별 감사 추적을 위해 키 자체에 식별자 prefix 를 둘 수 있도록 형식 정의:

- 형식: `clientId:key` (예: `generator-prod:abc123...`)
- principal 에 `clientId` 사용 — `SecurityContext.authentication.principal`
- 현재 구현은 단일 principal `"api-client"` — Phase 1.5 에서 형식 도입.

### 14.5 향후 마이그레이션 (Phase 4 — gateway 통합)

`log-gateway` 가 도입되면 인증 책임을 위임:

- **bearer JWT** — 외부 호출자 (UI / partner API) 대상
- **mTLS** — 클러스터 내부 호출 (선택)
- **API Key** — legacy / batch 호출자 호환 유지

본 절의 API Key 정책은 Phase 4 전까지 유지하며, gateway 도입 후 점진 폐기한다.

### 14.6 authorities / role 모델

현재는 권한 분기 미사용 — `UsernamePasswordAuthenticationToken(principal, null, emptyList())`.
향후 read-only / write 등 권한 차등이 필요해지면 `ROLE_INGEST_WRITE` 등 도입.

---

## 15. 오픈 이슈

- [ ] Spring Boot 4.0.5 + Kotlin 2.3.20 조합의 잠재 문제 — Spring Boot 4.1 릴리즈 시점에 재검증
- [ ] ES 8.17 + Spring Data Elasticsearch BOM 관리 버전 호환성 확인 필요
- [ ] Testcontainers 1.21.0 유지 vs 2.0.4 마이그레이션 (통합 테스트 작성 시점에 결정)
- [ ] compose-web과 gateway 간 인증 체계 (초기: 무인증, Phase 4에서 JWT)
- [ ] 로그 PII 마스킹 정책 — ingest 단계에서 처리할지, detection 단계에서 처리할지
