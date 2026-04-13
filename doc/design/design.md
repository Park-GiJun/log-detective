# log-detective 설계문서

## 1. 개요

**log-detective**는 대량의 애플리케이션/시스템 로그에서 보안 위협·이상 징후·장애 패턴을 **실시간 탐지**하고 알림을 발송하는 시스템이다. `fds`(이상거래 탐지)의 아키텍처를 그대로 재사용하되, 도메인만 "금융 트랜잭션" → "로그 이벤트"로 치환한 것이다.

### 1.1 왜 만드는가
- 로그는 많지만 사람이 다 볼 수 없다 → 규칙 기반 + 통계 기반 탐지가 필요
- fds 프로젝트에서 검증된 마이크로서비스/Kafka 파이프라인을 재활용해 학습/포트폴리오 목적으로 확장
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
**책임**: 로그 수집 + 영속화 + Kafka 발행
- `POST /api/logs` / `POST /api/logs/batch` 엔드포인트
- 수신 즉시 PostgreSQL(`log_events` 테이블) + Elasticsearch(`logs-YYYY.MM.DD` 인덱스) 이중 저장
- `logs.raw` 토픽으로 발행
- Flyway로 스키마 마이그레이션 관리

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
**책임**: 로그 트래픽 시뮬레이터
- Kotlin Coroutine 기반 동시 전송
- 정상 로그 + 의도적 공격 패턴(BruteForce, SQLi, 비정상 시간대) 주입
- Ktor Client로 `log-gateway`에 POST

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

detection_rules
───────────────
id              VARCHAR PK
name            VARCHAR
enabled         BOOLEAN
severity        VARCHAR
config          JSONB
updated_at      TIMESTAMPTZ
```

> JPA entity는 각 서비스(`log-ingest-service`, `log-alert-service`, `log-detection-service`)에 분산. 마이그레이션은 **소유 서비스**의 Flyway 리소스에 둔다.

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

> docker-compose는 현재 사용하지 않음. 인프라는 기존 fds 환경의 도커 네트워크 또는 로컬 네이티브 설치 재활용.

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
| **Config Server는 Eureka 서버에 합체** | fds 관행 유지, 운영 노드 최소화 |

---

## 10. 오픈 이슈

- [ ] Spring Boot 4.0.5 + Kotlin 2.3.20 조합의 잠재 문제 — Spring Boot 4.1 릴리즈 시점에 재검증
- [ ] ES 8.17 + Spring Data Elasticsearch BOM 관리 버전 호환성 확인 필요
- [ ] Testcontainers 1.21.0 유지 vs 2.0.4 마이그레이션 (통합 테스트 작성 시점에 결정)
- [ ] compose-web과 gateway 간 인증 체계 (초기: 무인증, Phase 4에서 JWT)
- [ ] 로그 PII 마스킹 정책 — ingest 단계에서 처리할지, detection 단계에서 처리할지
