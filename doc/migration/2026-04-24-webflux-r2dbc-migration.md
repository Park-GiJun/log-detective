# WebFlux + R2DBC 전환 기록

- **브랜치**: `refactor/webflux-r2dbc-migration`
- **시작일**: 2026-04-24
- **목표**: Spring MVC → WebFlux, JPA → Exposed R2DBC 전면 전환

## 배경
Spring Boot 4.0.5 + Kotlin 2.3.20 기반 MSA 를 반응형 스택으로 전환. DB 접근은 Exposed 1.1.1 의 R2DBC 모듈을 사용해 suspend 함수 기반 DSL 로 통일.

## 의사결정

| 항목 | 선택 | 사유 |
|------|------|------|
| Web | `spring-boot-starter-webflux` | 반응형 전환의 본체 |
| DB 런타임 | Exposed R2DBC (`exposed-r2dbc`) | suspend DSL, Kotlin-first |
| DB 마이그레이션 | Flyway (JDBC) | R2DBC 공식 미지원, JDBC DataSource 별도 구성 |
| Gateway | `spring-cloud-starter-gateway-server-webflux` | MVC 버전 폐기 |
| Elasticsearch | non-reactive 클라이언트 유지 + `Dispatchers.IO` | 클라이언트 타입 교체 시 ES 어댑터 전면 재작성 회피 |
| Kafka | `KafkaTemplate` 유지 + `CompletableFuture.await()` | Spring Kafka 반응형 미표준 |
| 코루틴 ↔ Reactor 브리지 | `kotlinx-coroutines-reactor` | WebFlux `Mono`/`Flux` 자동 변환 |
| Eureka Server | **MVC 유지** | Eureka 대시보드 Spring MVC 의존, 교체 불가 |
| JSONB 직렬화 | `kotlinx-serialization-json` | Jackson 버전 변동(2.x→3.x) 회피 |

## 변경 범위

### buildSrc
- `Dependencies.kt`:
  - Spring: `WEB` 유지(Eureka 용), `WEBFLUX` 추가, `JPA` 제거
  - Flyway 관련 정리: `FLYWAY`(=`starter-jdbc`), `FLYWAY_CORE` 분리
  - `SpringCloud.GATEWAY_MVC` → `SpringCloud.GATEWAY` (Reactor)
  - `SpringCloud.RESILIENCE4J` → `RESILIENCE4J_REACTOR`
  - 신설: `R2dbc.POSTGRESQL`, `R2dbc.POOL`
  - `Exposed.JDBC` → `Exposed.R2DBC`, `KOTLIN_DATETIME` → `JAVA_TIME`
  - `Docs.SPRINGDOC`: webmvc-ui → webflux-ui
- `Versions.kt`: 그대로 (Exposed 1.1.1, Spring Cloud 2025.1.1)
- `log-spring-boot.gradle.kts`: `WEB` → `WEBFLUX`, `kotlinx-coroutines-reactor` 추가
- **신규** `log-exposed-r2dbc.gradle.kts`: Exposed R2DBC + Flyway(JDBC) + kotlinx-serialization + Postgres JDBC/R2DBC 드라이버

### 모듈 build.gradle.kts
- **log-ingest-service**: `plugin.jpa` 제거, `log-exposed-r2dbc` 적용, JPA/Flyway 수동 선언 제거
- **log-generator**: 동일
- **log-gateway**: `GATEWAY_MVC` → `GATEWAY`
- **log-alert-service**: `plugin.jpa` 제거, `log-exposed-r2dbc` 적용
- **log-detection-service**: `log-exposed-r2dbc` 적용
- **log-eureka-server**: 변경 없음 (MVC 유지)
- **log-common**: 변경 없음 (도메인 전용)

### 소스 코드 — 공통 패턴

1. **Port 시그니처**: 모든 out/in port `fun` → `suspend fun`
2. **Handler**: `@Transactional` 제거 (Exposed `suspendTransaction` 블록으로 대체), 메서드 `suspend fun`
3. **WebAdapter (@RestController)**: 메서드 `suspend fun`, `ResponseEntity` 반환 유지 가능 (WebFlux 자동 bridge)
4. **PersistenceAdapter**: JPA Repository 삭제 → Exposed Table + `suspendTransaction` 내부 DSL
5. **MessageAdapter (Kafka)**: `KafkaTemplate.send().await()` 로 suspend 지원
6. **SearchAdapter (ES, sync)**: `withContext(Dispatchers.IO) { ... }` 래핑
7. **SecurityConfig**: `SecurityFilterChain` → `SecurityWebFilterChain`, `HttpSecurity` → `ServerHttpSecurity`
8. **R2DBC+Flyway Config** (서비스마다 1 개 신설):
   - `ConnectionFactory` (R2DBC) 와 `DataSource` (JDBC, Flyway 전용) 동시 구성
   - `Database.connect(ConnectionFactoryImpl(...))` 로 Exposed 전역 Database 등록
   - Flyway 실행은 `FlywayMigrationInitializer` 로 start-up 에서 JDBC DataSource 기반 실행
9. **application.yml**:
   - `spring.datasource.*` → Flyway 용 JDBC 전용
   - `spring.r2dbc.*` 추가 → Exposed R2DBC 런타임
   - `spring.jpa.*` 제거

### Exposed 1.x 패키지 주의
- 모든 임포트 최상위 prefix 가 `org.jetbrains.exposed.v1.*` 로 이동
- 주요 경로: `v1.core`, `v1.r2dbc`, `v1.r2dbc.transactions`, `v1.javatime`, `v1.json`
- 트랜잭션 진입점: `suspendTransaction(db) { ... }`
- 쿼리 결과: Flow 반환, `.toList()` / `.firstOrNull()` 사용

## 모듈별 전환 현황

| 모듈 | build.gradle.kts | 포트 suspend | 핸들러 suspend | 테이블/어댑터 | Config | application.yml |
|------|------------------|--------------|----------------|---------------|--------|------------------|
| log-ingest-service  | ✅ | ✅ | ✅ | ✅ Exposed Table + R2DBC Adapter | ✅ R2dbcConfig + SecurityConfig reactive | ✅ r2dbc + jdbc(Flyway) |
| log-generator       | ✅ | ✅ | ✅ | ✅ Scenarios Table + R2DBC Adapter | ✅ R2dbcConfig + SecurityConfig reactive | ✅ r2dbc + jdbc(Flyway) |
| log-alert-service   | ✅ | — 스캐폴드 — | — | — | — | ✅ r2dbc + jdbc(Flyway) |
| log-detection-service | ✅ | — 스캐폴드 — | — | — | — | ✅ r2dbc + jdbc(Flyway) |
| log-gateway         | ✅ GATEWAY reactive | — | — | — | — (routing은 Config Server) | 변경 없음 |
| log-eureka-server   | — MVC 유지 (Eureka 대시보드) — | — | — | — | — | — |
| log-common          | — 변경 없음 (도메인 전용) — | — | — | — | — | — |

## 검증 상태

- **빌드 검증 (`./gradlew build`)**: WSL 환경에 Linux JDK 25 없음 → 사용자 Windows 환경에서 실행 필요
- **확인 권장 명령**:
  ```powershell
  .\gradlew.bat clean build -x test
  .\gradlew.bat :log-ingest-service:bootRun   # 실제 부팅 확인
  .\gradlew.bat :log-generator:bootRun
  ```
- **예상 조정 필요 영역**:
  1. Exposed 1.x 패키지 import — `org.jetbrains.exposed.v1.*` prefix 실제 stable 릴리즈와 일치 확인
  2. `timestampWithTimeZone`, `CurrentTimestampWithTimeZone` 은 `exposed-java-time` 에 포함 여부
  3. `jsonb` DSL 시그니처 — `org.jetbrains.exposed.v1.json.jsonb(name, serialize, deserialize)`
  4. R2DBC Postgres URL 에서 schema 프로퍼티 적용 방식 (`?currentSchema=`)
  5. Eureka Client 가 WebFlux 환경에서 정상 등록하는지 (`spring-cloud-starter-netflix-eureka-client` 는 공통 지원)
  6. Spring Security WebFlux 의 `authorizeExchange` DSL 이 4.0.x 문법과 일치하는지

## 리스크 / 미결 사항

1. **Exposed 1.x R2DBC API 안정성** — 1.1.1 은 stable 이지만 마이너 시그니처 변경 가능성. 빌드 에러 나면 공식 문서 재확인 필요.
2. **INET 컬럼** — Exposed 네이티브 타입 미지원으로 `varchar(64)` 매핑. R2DBC Postgres 드라이버가 INET ↔ String 변환 지원하는지 검증 필요.
3. **Kafka 반응형 미정** — `spring-kafka` 는 반응형 표준 없음. 당분간 `CompletableFuture.await()` 로 처리.
4. **테스트** — Kotest + Testcontainers 조합 유지. R2DBC 테스트용 `ConnectionFactory` 별도 구성 필요.
5. **JPA 플러그인 사이드이펙트** — `kotlin("plugin.jpa")` 가 쓰여있던 모듈(ingest, alert)에서 open class 강제가 사라짐. 기존 handler `open class` 선언 유지 여부 검토.

## 다음 단계

1. log-ingest-service 완성 → 컴파일 통과 → 체크포인트 커밋
2. 동일 패턴 적용: generator → alert → detection
3. log-gateway 라우팅 재구성
4. 통합 빌드 검증 (`./gradlew build`)
