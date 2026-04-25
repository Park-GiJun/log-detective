도메인 .kt 파일을 받아 짝이 되는 JPA Entity 를 자동 생성한다.

## 사용법
- `/create-entity-from-domain {도메인 파일 경로}` — 예: `/create-entity-from-domain backend/log-ingest-service/src/main/kotlin/com/gijun/logdetect/ingest/domain/model/Outbox.kt`

---

## Step 1: 입력 검증
- 인자가 없으면 사용법 안내하고 중단
- 인자가 `.kt` 가 아니면 중단
- 파일이 존재하지 않으면 중단
- 경로에 `domain/model/` 이 없으면 경고만 출력하고 진행 (헥사고날 경계 위반 의심)

## Step 2: 도메인 파일 분석
대상 파일을 Read 하여 다음을 추출:

### 2-1. 메타 정보
- **클래스명**: `data class {Name}(...)` 의 `{Name}`
- **패키지명**: `package ...` 라인
- **모듈 root**: 경로에서 `backend/{module-name}/` 추출 (예: `log-ingest-service`, `log-generator`)
- **모듈의 base package**: 경로에서 `kotlin/com/gijun/logdetect/{base}/` 의 `{base}` 추출 (예: `ingest`, `generator`)
- **스키마**: 모듈명에서 추정 — `log-ingest-service` → `ingest`, `log-generator` → `generator`, 그 외는 `public`

### 2-2. 필드 추출
data class 의 생성자 파라미터를 모두 파싱. 각 필드:
- 변수명 (camelCase)
- 타입 (nullable 여부 포함)
- default 값 유무

## Step 3: 타입 매핑 규칙
도메인 타입 → JPA 컬럼 매핑:

| 도메인 타입 | JPA 매핑 |
|---|---|
| `Long?` (id 필드, default null) | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` + `Long? = null` |
| `Long` / `Int` | `@Column(nullable = false)` |
| `String` (필드명에 `id`/`name` 포함, 짧음) | `@Column(nullable = ?, length = 64)` |
| `String` (`message`/`body`/`description` 등 긴 텍스트) | `@Column(columnDefinition = "TEXT")` |
| `String` (그 외) | `@Column(nullable = ?, length = 255)` |
| `String?` | nullable + 위 규칙 |
| `Enum<*>` | `@Enumerated(EnumType.STRING) @Column(length = 16)` |
| `Map<String, *>` / `List<*>` (직렬화 대상) | `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "JSONB")` |
| `Instant` / `OffsetDateTime` | `@Column(nullable = ?)` (timestamptz 자동) |
| `UUID` | `@Column(nullable = ?)` |
| `Boolean` | `@Column(nullable = false)` |
| 도메인 model 참조 | 매핑 보류, TODO 주석으로 표시 (관계 설계 필요) |

### 컬럼명 변환
- 변수명 camelCase → 컬럼명 snake_case (예: `aggregateId` → `aggregate_id`, `nextAttemptAt` → `next_attempt_at`)
- `id` 컬럼은 변환 없이 `id`

### 테이블명 변환
- 클래스명 → snake_case 복수형 (예: `Outbox` → `outbox_messages`, `LogEvent` → `log_events`, `Scenario` → `scenarios`)
- 단수형이 자연스러운 도메인은 그대로 (`Detection` → `detections`)
- **단, 같은 모듈에 이미 V1__ Flyway 파일이 있고 거기 테이블명이 정해졌으면 그것을 우선** — `db/migration/V*.sql` 에서 `create table ([a-z_.]+)` 로 매칭하여 도메인 클래스명과 매칭되는 테이블명 사용

## Step 4: 출력 경로 결정
입력 도메인 파일이 `.../{base}/domain/model/{Domain}.kt` 면, 출력은:
```
.../{base}/infrastructure/adapter/out/persistence/{domainCamel}/entity/{Domain}Entity.kt
```

여기서 `{domainCamel}` = `{Domain}` 의 첫 글자 lowercase (예: `Outbox` → `outbox`, `LogEvent` → `logEvent`).

이미 `.../persistence/{domainCamel}/entity/` 디렉토리가 있으면 그대로 사용. 없으면 디렉토리 생성.

**기존 패턴과 일관성 검증**: 같은 모듈에 이미 `persistence/{otherDomain}/entity/` 가 있으면 그 패턴 따름. 없으면 (단일 도메인) `persistence/entity/` 에 직접 두는 것도 허용 — 사용자에게 어느 쪽인지 한 번 확인 후 결정.

## Step 5: Entity 코드 생성
다음 템플릿으로 작성:

```kotlin
package {entity-package}

import {Domain 클래스 import}
{필요한 enum / 도메인 import}
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode  // JSONB 필드 있을 때만
import org.hibernate.type.SqlTypes              // JSONB 필드 있을 때만
import java.time.Instant                        // Instant 필드 있을 때만
import java.util.UUID                           // UUID 필드 있을 때만

@Entity
@Table(name = "{table-name}", schema = "{schema}")
class {Domain}Entity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    {각 필드의 @Column ...}
) {
    fun toDomain(): {Domain} = {Domain}(
        {모든 필드 매핑}
    )

    companion object {
        fun from(domain: {Domain}): {Domain}Entity = {Domain}Entity(
            {모든 필드 매핑 — id 는 domain.id 그대로 (null 이면 IDENTITY 가 채움)}
        )
    }
}
```

**주의**:
- 필드 순서: `id` 먼저 → 도메인 정의 순서 유지
- 도메인 모델에 `Instant?` + default `Instant.now()` 같이 어색한 nullable 이 있으면 entity 에서는 `nullable = false` 로 두고 `toDomain()` 에서 그대로 전달, `from()` 에서 `?: Instant.now()` fallback
- 도메인이 `Instant?` 인데 DDL 이 NOT NULL 이면 (예: `published_at` 같은 정말 nullable 한 컬럼은 도메인도 `Instant?`) DDL 보고 정합성 맞추기 — 같은 모듈의 `db/migration/V*.sql` 에서 해당 테이블 컬럼 `not null` 여부 확인하여 우선
- `payload` 같이 직렬화된 String 필드는 `@JdbcTypeCode(SqlTypes.JSON) + columnDefinition = "JSONB"` 자동 적용

## Step 6: 생성 후 보고
다음 형식으로 출력:

```
═══════════════════════════════════════════
  Entity 생성 완료
═══════════════════════════════════════════
  도메인:    {도메인 파일 경로}
  Entity:    {생성된 파일 경로}
  테이블:    {schema}.{table-name}
  필드:      {N}개 (id 포함)
  매핑 주의: {JSONB / Enum / nullable 차이 등 특이 매핑 요약}
═══════════════════════════════════════════
```

후속 작업 안내:
- `{Domain}JpaRepository` 가 없으면 만들 것 권장 (`Spring Data JpaRepository<{Domain}Entity, Long>`)
- `{Domain}PersistencePort` / `{Domain}PersistenceAdapter` 가 있으면 거기서 entity 사용

## 주의사항

- **헥사고날 경계 준수** — entity 는 반드시 `infrastructure/adapter/out/persistence/` 하위에만 생성. domain/ 아래에 생성 금지
- **도메인 파일은 절대 수정하지 않음** — Read 만
- **DDL 우선** — 같은 모듈에 `db/migration/V*.sql` 이 있으면 테이블명/컬럼명/nullable/length 를 거기서 가져옴 (도메인 모델보다 DDL 이 진실의 원천)
- **이미 entity 파일이 있으면** 사용자에게 덮어쓸지 확인 (Edit 가 아니라 새로 생성하는 커맨드이므로)
- **UseCase / Port 자동 생성은 안 함** — Entity 만 만든다. Adapter / Repository 생성은 별도 커맨드 분리 권장
