# log-detective — 로그 탐지 시스템

애플리케이션/시스템 로그를 실시간 수집·분석하여 보안 위협·이상 징후·장애 패턴을 탐지하는 MSA 기반 백엔드 + Compose Web 대시보드.

---

## 1. 프로젝트 개요

### 1.1 목표

- 초당 수천 건의 로그 이벤트를 수집·영속화·검색·탐지하는 파이프라인 구축
- **MSA + 이벤트 기반 + 헥사고날 아키텍처(Port & Adapter)** 기반으로 도메인 로직과 인프라를 분리
- **규칙 기반 탐지 엔진** 6종(BruteForce, SQLi, ErrorSpike, OffHour, Geo, RareEvent) 구현
- Kotlin Multiplatform(Wasm) 기반 Compose Web으로 대시보드/시각화 제공

### 1.2 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin 2.3.20 |
| JVM | JDK 25 (Microsoft Build) |
| Build | Gradle 9.4.0 (Kotlin DSL), buildSrc convention plugins |
| Backend | Spring Boot 4.0.5, Spring Cloud 2025.1 |
| Database | PostgreSQL 17, JPA/Hibernate, Flyway |
| Cache / Lock | Redis 7 + **Redisson 3.52** |
| Search | Elasticsearch 8.17 |
| Messaging | Apache Kafka 4.0 (KRaft) |
| HTTP Client | Ktor Client 3.4.0 |
| API Gateway | Spring Cloud Gateway Server WebMVC |
| Service Discovery | Netflix Eureka (+ Spring Cloud Config Server 겸임) |
| Circuit Breaker | Resilience4j |
| API Docs | SpringDoc OpenAPI 3.0 (Swagger UI) |
| Observability | Micrometer + Prometheus + Zipkin |
| Frontend | Compose Multiplatform 1.10.3 (Kotlin/Wasm) |
| Test | Kotest 6.1 + MockK 1.14 + Testcontainers 1.21 |
| Quality | ktlint 1.5.0, dev.detekt 2.0.0-alpha.2 (수동 실행) |

---

## 2. 시스템 아키텍처

### 2.1 전체 구성도

```
                    ┌──────────────────┐
                    │  compose-web     │  :3003 (대시보드)
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
       │  logs.raw          │ logs.detected      │ alerts.dispatched
       └──────────┬─────────┴─────────┬──────────┘
                  ▼                   ▼
            ┌─────────────────────────────┐
            │         Kafka (KRaft)        │
            └─────────────────────────────┘

         ┌────────────┐  ┌────────────┐  ┌────────────┐
         │ PostgreSQL │  │   Redis    │  │Elasticsearch│
         │  logdetect │  │ (Redisson) │  │  + Kibana   │
         └────────────┘  └────────────┘  └────────────┘

                    ┌──────────────────────────┐
                    │ log-eureka-server :28761 │
                    │ (Discovery + Config)     │
                    └──────────────────────────┘
```

### 2.2 데이터 흐름

```
1. Generator / 외부 로그 소스 → Gateway → Ingest Service
   - 로그 이벤트 수집 (REST)
   - PostgreSQL (ingest.log_events) + Elasticsearch(logs-YYYY.MM.DD) 이중 저장
   - Kafka [logs.raw] 토픽으로 발행

2. Ingest → Kafka → Detection Service
   - [logs.raw] 컨슈밍
   - Redisson 슬라이딩 윈도우로 패턴 카운트
   - 규칙 엔진(6종) 평가 → 매치 시 [logs.detected] 발행
   - detection.detection_history 에 감사 이력 저장

3. Detection → Kafka → Alert Service
   - [logs.detected] 컨슈밍
   - Redisson 기반 알림 fingerprint 중복제거
   - alert.alerts 영속화 + dispatcher(log/webhook)로 발송
   - [alerts.dispatched] 팬아웃
```

### 2.3 포트 구성

| 서비스 | 포트 | 설명 |
|--------|-----:|------|
| log-eureka-server | 28761 | Service Discovery + Config Server |
| log-ingest-service | 28081 | 로그 수집/영속화 |
| log-detection-service | 28082 | 규칙 기반 탐지 |
| log-alert-service | 28083 | 알림 집계/발송 |
| **log-gateway** | **8084** | **외부 진입점 (유일한 1XXXX 미만)** |
| log-generator | 28090 | 트래픽 시뮬레이터 |
| compose-web (dev) | 3003 | Webpack dev server |
| PostgreSQL | 5432 | `logdetect` DB (원격 공유 서버) |
| Redis | 6380 | Redisson (원격 공유 서버) |
| Kafka | 9094 | 원격 공유 서버 |
| Elasticsearch | 9201 | 원격 공유 서버 |

---

## 3. 모듈 구조

### 3.1 멀티모듈 레이아웃

```
log-detective/
├── buildSrc/                                 # 버전/플러그인 중앙 관리
│   └── src/main/kotlin/
│       ├── Versions.kt, Dependencies.kt
│       ├── log-kotlin-base.gradle.kts        # JVM + ktlint + detekt
│       ├── log-spring-boot.gradle.kts        # + Spring Boot 공통
│       └── log-spring-boot-service.gradle.kts# + SpringDoc
│
├── backend/
│   ├── log-common/                           # 공통 도메인/DTO/예외/유틸
│   ├── log-eureka-server/                    # Eureka + Config Server
│   ├── log-gateway/                          # API Gateway
│   ├── log-ingest-service/                   # → log-common
│   ├── log-detection-service/                # → log-common
│   ├── log-alert-service/                    # → log-common
│   └── log-generator/                        # → log-common (트래픽 시뮬)
│
├── frontend/
│   └── compose-web/                          # Kotlin/Wasm + Compose MP
│
├── config/detekt/detekt.yml                  # detekt 전역 룰
├── doc/design/design.md                      # 설계문서
├── infra/init-db.sql                         # CREATE DATABASE logdetect
└── .env / .env.example                       # 원격 인프라 접속 정보
```

### 3.2 모듈별 기술 의존성

| | JPA | Kafka | Redisson | ES | Ktor | Flyway | Schema |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| log-ingest-service | O | Producer | - | O | O | O | `ingest` |
| log-detection-service | O | Consumer/Producer | O | - | O | O | `detection` |
| log-alert-service | O | Consumer/Producer | O | - | O | O | `alert` |
| log-generator | - | - | - | - | O | - | - |
| log-gateway | - | - | - | - | - | - | - |
| log-eureka-server | - | - | - | - | - | - | - |

### 3.3 log-generator — 로그 이벤트 시뮬레이터

탐지 파이프라인을 검증하기 위해 `LogEvent` 도메인 모델 그대로의 **로그 이벤트**를 합성해 `log-gateway` 로 전송한다.  FDS 결제/가맹점 개념과 혼동 금지 — 이 모듈은 가맹점·카테고리·통화 개념을 갖지 않으며, 오직 `source` / `level` / `message` / `host` / `ip` / `userId` / `attributes` 조합만 생성한다.

| 요소 | 내용 |
|---|---|
| **source 풀** | `auth-service`, `api-gateway`, `order-service`, `payment-service`, `admin-portal`, `legacy-batch`(희귀) |
| **정상 로그** | INFO 85% / WARN 10% / ERROR 5% 가중 분포, 엔드포인트·상태코드·latency 속성 포함 |
| **공격 시나리오** | `AttackType` enum 6종 — 탐지 규칙 R001~R006 과 1:1 매칭 |

| AttackType | 매칭 규칙 | 합성 패턴 |
|---|---|---|
| `BRUTE_FORCE` | R001 BruteForceLoginRule | `auth-service` WARN, 동일 IP 대역(`211.45.x`) 반복 실패 |
| `SQL_INJECTION` | R002 SqlInjectionPatternRule | `api-gateway` WARN, `message` 에 `UNION SELECT` / `' OR 1=1 --` 등 페이로드 주입 |
| `ERROR_SPIKE` | R003 ErrorRateSpikeRule | `payment-service` ERROR 연속 (upstream timeout / 5xx / circuit breaker OPEN) |
| `OFF_HOUR_ACCESS` | R004 OffHourAccessRule | `admin-portal` INFO, `timestamp` 강제 00~05시 KST + 관리자 userId(9001~9005) |
| `GEO_ANOMALY` | R005 GeoAnomalyRule | `auth-service` INFO 로그인 성공 + 해외 IP 대역(54/124/81/51/94) |
| `RARE_EVENT` | R006 RareEventRule | 희귀 source `legacy-batch` + ERROR + 드문 패턴명 |

핵심 파일:
- `domain/model/LogEvent.kt` — 전송 대상 도메인 모델
- `domain/model/AttackType.kt` — 공격 시나리오 enum (R001~R006 매칭)
- `domain/objects/LogEventFactory.kt` — `createNormal()` / `createSuspicious(type)` 팩토리
- `application/handler/command/GeneratorCommandHandler.kt` — `fraudRatio` 에 따라 정상/공격 분기

---

## 4. 헥사고날 아키텍처 (Port & Adapter)

### 4.1 패키지 구조 (서비스 공통)

```
com.gijun.logdetect.{service}/
├── domain/                          # 순수 Kotlin (Spring/인프라 의존 없음)
│   ├── model/                       # 도메인 모델
│   └── enums/                       # 도메인 enum
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── command/             # 쓰기 UseCase 인터페이스
│   │   │   └── query/               # 읽기 UseCase 인터페이스
│   │   └── out/                     # Outbound Port
│   ├── dto/
│   │   ├── command/                 # 쓰기 입력 DTO ({Action}{Resource}Command)
│   │   ├── query/                   # 읽기 입력 DTO
│   │   └── result/                  # 출력 DTO
│   └── handler/                     # UseCase 구현체 ({Resource}Handler)
│
├── infrastructure/
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── web/                 # @RestController
│   │   │   └── messaging/           # Kafka Consumer
│   │   └── out/
│   │       ├── persistence/         # JPA
│   │       ├── messaging/           # Kafka Producer
│   │       ├── cache/               # Redisson
│   │       ├── search/              # Elasticsearch
│   │       └── client/              # Ktor HTTP Client
│   └── config/                      # @Configuration, @Bean 등록
│
└── {Service}Application.kt
```

### 4.2 의존 방향

```
infrastructure.adapter.in  ──▶  application.port.in   ◀──  application.handler
infrastructure.adapter.out ◀──  application.port.out  ◀──  application.handler

[Infrastructure]            [Application]            [Domain]
Spring, JPA, Kafka          Port + Handler           순수 Kotlin
Redisson, ES, Ktor          인터페이스 + 구현체       model, enum
```

**절대 규칙**
- `domain` 패키지는 외부 프레임워크 import 금지
- `application.handler`는 `application.port.out` 인터페이스만 의존
- UseCase 구현체 네이밍: `{Resource}Handler` (Service/Impl 금지)
- UseCase와 DTO는 파일/패키지 분리 (UseCase 파일 내부에 data class 선언 금지)
- 아웃바운드 포트 접미어 고정:
  - Kafka → `Message` (`LogEventMessagePort`)
  - DB/JPA → `Persistence`
  - Redisson → `Cache`
  - Elasticsearch → `Search`

---

## 5. 인프라

### 5.1 원격 공유 인프라

`log-detective`는 별도 `docker-compose.yml`을 두지 않고, 이미 구동 중인 원격 공유 인프라(PostgreSQL / Redis / Kafka / Elasticsearch)에 접속해 사용한다. 접속 정보는 `.env` 파일(gitignore 처리)에 있으며 `.env.example` 로 템플릿을 제공한다.

```
.env (예시)
├── DB_HOST / DB_PORT / DB_NAME=logdetect / DB_USERNAME / DB_PASSWORD
├── REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
├── KAFKA_BOOTSTRAP_SERVERS
└── ELASTICSEARCH_URIS
```

로컬 개발 시에는 `.env.example` 을 복사하여 로컬 Docker/네이티브 설치에 맞게 값을 채워 사용한다.

### 5.2 DB 스키마 격리

단일 `logdetect` 데이터베이스 안에서 **PostgreSQL 스키마**로 서비스 격리.

| 서비스 | 스키마 | 테이블 |
|---|---|---|
| log-ingest-service | `ingest` | `log_events` |
| log-detection-service | `detection` | `detection_rules`, `detection_history` |
| log-alert-service | `alert` | `alerts`, `alert_detections` |

각 서비스는 자체 `db/migration/V*.sql`을 보유하며, `flyway_schema_history` 테이블이 스키마별로 분리되어 충돌이 없다.

### 5.3 기동 순서

```
1. 원격 PostgreSQL 에 CREATE DATABASE logdetect;  (최초 1회)

2. EurekaServerApplication        (:28761) — Eureka + Config Server
   → 환경 변수: .env 로드 (또는 EnvFile 플러그인)

3. IngestServiceApplication       (:28081)
   DetectionServiceApplication    (:28082)
   AlertServiceApplication        (:28083)
   → Config Server에서 공통 설정 수신 (CONFIG_PASSWORD 필수)

4. GatewayApplication             (:8084)
5. GeneratorApplication           (:28090)

# Eureka/Config 미기동 시 → 모든 서비스 기동 실패 (Fail-Fast)
```

---

## 6. 프론트엔드 (compose-web)

Kotlin Multiplatform + Compose Multiplatform 1.10.3 기반 Wasm 타겟.

```bash
gradlew :compose-web:wasmJsBrowserDevelopmentRun   # http://localhost:3003
gradlew :compose-web:wasmJsBrowserProductionWebpack # 프로덕션 번들
```

초기 구성은 테스트/시각화 용도의 최소 골격이며, Phase 4에서 대시보드 UI를 구현한다.

---

## 7. 빌드 & 품질 도구

### 7.1 빌드

```bash
gradlew build                       # 전체 빌드 (ktlint/detekt 미실행)
gradlew :log-ingest-service:bootRun # 단일 서비스 실행
```

### 7.2 코드 품질

ktlint + detekt는 `build`/`check` 파이프라인에서 언훅되어 **수동 실행**한다.

```bash
gradlew codeFormat          # 전 모듈 ktlintFormat (자동 수정)
gradlew codeQuality         # 전 모듈 ktlintCheck + detekt
gradlew :log-common:detekt  # 모듈별 개별 실행
```

- `.editorconfig` 에 `@Composable`/`@Test` 함수명 규칙 예외
- `config/detekt/detekt.yml` 에 MaxLineLength 140, MagicNumber 완화 등

### 7.3 Kotlin 2.3.20 + Spring Boot 4.0 호환

Spring Boot 4.0 BOM은 Kotlin 2.2.21을 관리한다. `buildSrc/log-spring-boot.gradle.kts` 에서 `extra["kotlin.version"] = "2.3.20"` 으로 오버라이드하여 stdlib까지 2.3.20으로 통일. Spring Boot 4.1 릴리즈 시 재검증 예정.

---

## 8. Swagger UI / 모니터링

| 도구 | URL | 용도 |
|---|---|---|
| Eureka Dashboard | http://localhost:28761 | 서비스 등록 현황 |
| Ingest Swagger | http://localhost:28081/swagger-ui/index.html | 수집 API |
| Detection Swagger | http://localhost:28082/swagger-ui/index.html | 탐지 API |
| Alert Swagger | http://localhost:28083/swagger-ui/index.html | 알림 API |
| Compose Web | http://localhost:3003 | 대시보드 |
| Prometheus | http://localhost:{port}/actuator/prometheus | 메트릭 |

---

## 9. 진행 상황

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 스켈레톤 (모듈/포트/buildSrc/공통 설정) | ✅ 완료 |
| 1 | 수집 파이프라인 (Ingest + Generator + Flyway) | 🚧 준비 (Generator 로그 이벤트 팩토리·AttackType 적용) |
| 2 | 탐지 엔진 (DetectionRule 6종 + Redisson) | ⏳ 예정 |
| 3 | 알림 (fingerprint 중복제거 + 디스패처) | ⏳ 예정 |
| 4 | Gateway 라우팅 + compose-web 대시보드 | ⏳ 예정 |
| 5 | 고급 규칙 + Generator 공격 패턴 | ⏳ 예정 |
| 6 | 관측/운영 (Zipkin, Prometheus 커스텀 메트릭) | ⏳ 예정 |

자세한 내용은 `doc/design/design.md` 참조.
