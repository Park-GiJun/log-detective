# log-detective — Claude Code 설정

## 프로젝트 정보
- Kotlin 2.3.20 + Spring Boot 4.0.5 MSA 기반 **로그 탐지 시스템**
- 헥사고날 아키텍처: `domain` / `application` / `infrastructure(adapter, config)`
- 멀티모듈: `buildSrc` 로 의존성 관리, `backend/` + `frontend/` 분리
- 프론트엔드: Compose Multiplatform 1.10.3 (Kotlin/Wasm, 3003 포트)
- 규칙 기반 탐지 엔진 6종 (BruteForce / SQLi / ErrorSpike / OffHour / Geo / RareEvent)

## 코딩 규칙

### 헥사고날 경계
- `domain` 패키지에 Spring/JPA/Kafka 등 프레임워크 의존 금지
- `application` 계층에 Spring 어노테이션(@Service, @Component 등) 사용 금지 → `infrastructure/config` 에서 @Bean 등록
- `application` 은 port 인터페이스만 의존 (port 위치: `application.port.in`, `application.port.out`)
- 도메인 모델은 HTTP 응답으로 직접 노출 금지 → `infrastructure` 계층에 Response DTO 분리
- `infrastructure.adapter.in` 위치에 web/filter/messaging 등 inbound adapter 배치

### 네이밍
- UseCase 구현체: `{Resource}Handler` (Service, Impl 사용 금지)
- 아웃바운드 포트 접미어 고정:
  - Kafka/이벤트 → `Message` (e.g., `LogEventMessagePort`)
  - DB/JPA → `Persistence` (e.g., `LogEventPersistencePort`)
  - Redisson → `Cache` (e.g., `RateLimitCachePort`)
  - Elasticsearch → `Search` (e.g., `LogEventSearchPort`)
- non-const val 은 camelCase, const val 만 SCREAMING_SNAKE_CASE

### DTO / UseCase 분리
- `application/port/in` 에는 **인터페이스 계약만**, 입출력 DTO는 `application/dto/` 하위로 분리
  - `application/dto/command/{Action}{Resource}Command.kt` — 쓰기 입력
  - `application/dto/query/{Action}{Resource}Query.kt` — 읽기 입력
  - `application/dto/result/{Resource}Result.kt` — 도메인 노출 회피용 출력
  - UseCase 파일 내부에 data class 선언 금지

### Spring 어노테이션 정책
- `application/handler`: @Bean 수동 등록만 (`infrastructure/config` 에서 등록)
  - **예외**: `@Transactional` 은 유스케이스 경계를 명확히 하기 위해 handler 메서드에 선언 허용
- `infrastructure/adapter`: @Component 허용
- `infrastructure/config`: @Configuration + @Bean

### 커밋 / 언어
- 한국어 커밋 메시지 사용
- 한국어 주석 허용 (단, WHY 중심)

## 품질 도구

### ktlint / detekt (수동 실행)
- **build 에서는 실행되지 않음** — `log-kotlin-base.gradle.kts` 에서 `check` 태스크로부터 언훅됨
- 수동 실행:
  - `gradlew codeFormat` — 전 모듈 ktlintFormat
  - `gradlew codeQuality` — 전 모듈 ktlintCheck + detekt
- `.editorconfig` 에 `@Composable`/`@Test` 함수명 예외
- `config/detekt/detekt.yml` 전역 룰 (MaxLineLength 140 등)

### 의존성 버전
- `buildSrc/src/main/kotlin/Versions.kt` 단일 소스
- 주요 버전: Kotlin 2.3.20, JDK 25, Gradle 9.4.0, Spring Boot 4.0.5, Ktor 3.4.0, Kotest 6.1.0, Compose MP 1.10.3, detekt 2.0.0-alpha.2

## Git 브랜치 규칙
- **코드 변경 후 commit/push 시 반드시 prefix 브랜치에서 작업. master 직접 push 금지.**
- 브랜치 prefix: `feat/`, `fix/`, `refactor/`, `perf/`, `test/`, `security/`, `chore/`, `docs/`
- 브랜치 생성: `/create-branch` 또는 `/create-issue-branch` 사용
- 워크플로우: 브랜치 생성 → 코딩 → `/commit` → `/ship` (PR → merge)
- 문서(`doc/`)만 변경하는 경우도 `docs/` prefix 브랜치 사용

## 커스텀 커맨드

### `/init-review` — 초기 Baseline 수립 (프로젝트 시작 시 1회)
6명 리뷰어 + 2명 리드가 전체 코드베이스를 분석하여 기준선을 수립한다.
- 산출물: `doc/review/0000-00-00-baseline.md`, `doc/memory/project-context.md`, `doc/memory/domain-glossary.md`, `doc/memory/review-checklist.md`

### `/commit` — 커밋 메시지 자동 생성 + 커밋
- 에이전트 "송준호" (Commit Craft)
- 형식: `{타입}: {한국어 요약}`
- 브랜치명에서 이슈 번호 자동 추출 → `Refs #번호` 자동 추가
- 빌드 검증 후 커밋
- 커밋 시 문서 자동 업데이트 (README.md, doc/, CLAUDE.md) — 변경 유형에 따라 해당 문서만 갱신

### `/ship` — PR 생성 → merge 자동화
- `/ship` — PR 생성
- `/ship --merge` — PR 생성 + 즉시 squash merge
- `/ship --draft` — Draft PR
- 에이전트 "오세린" (Ship Captain) — PR 메시지 전문가
- 에이전트 "강민재" (Gate Keeper) — merge 전 체크리스트 검증

### `/test` — 테스트 자동 생성 파이프라인
- `test/` 브랜치 생성 → 테스트 작성 → 실행 → 커밋 → PR → merge 전체 자동화
- 에이전트: 김태현(전략) → 이수빈(단위) → 박준영(통합)
- 프레임워크: Kotest 6 + MockK + Testcontainers

### `/create-issue-branch` — 이슈 기반 브랜치 생성
- `/create-issue-branch 1` — 단일 이슈 브랜치
- `/create-issue-branch 7 8 9` — 묶음 브랜치
- `/create-issue-branch all` — 열린 이슈 전체를 의존 순서대로 일괄 생성
- prefix 규칙: `security/` > `refactor/` > `perf/` > `feat/` > `test/` > `chore/`
- 브랜치명: `{prefix}/issue-{번호}-{slug}`

### `/create-entity-from-domain` — 도메인 → JPA Entity 자동 생성
- `/create-entity-from-domain {도메인 .kt 경로}` — 예: `/create-entity-from-domain backend/log-ingest-service/.../domain/model/Outbox.kt`
- 출력: `infrastructure/adapter/out/persistence/{domain}/entity/{Domain}Entity.kt`
- 매핑: `Long?` id → `@Id IDENTITY`, Enum → `@Enumerated(STRING)`, Map/List → `@JdbcTypeCode JSON + JSONB`, camelCase → snake_case
- DDL 우선 — 같은 모듈 `db/migration/V*.sql` 이 있으면 거기서 테이블명/nullable/length 가져옴 (도메인보다 진실)
- `toDomain()` + `companion.from(domain)` 변환 메서드 자동 포함
- 헥사고날 경계 검증 (도메인 파일이 `domain/model/` 에 있는지)

### `/review` — 커밋 단위 코드 리뷰
- 6 Reviewers + 2 Leads 가 리뷰
- 산출물: `doc/review/YYYY-MM-DD-{hash}.md`, `doc/lessons/{주제}.md`, `doc/memory/project-context.md` 누적 업데이트

### 리뷰 팀 구성

**Reviewers (병렬 실행)**
| # | 이름 | 역할 | 담당 |
|---|------|------|------|
| 1 | 강현수 | Architect | 헥사고날 준수, 의존 방향, SOLID, 모듈 경계 |
| 2 | 박서진 | Security | OWASP, 민감 데이터 마스킹, 인증/인가 |
| 3 | 이도윤 | Performance | N+1, Kafka/Redisson/ES 설정, 동시성 |
| 4 | 정하은 | Code Quality | 클린 코드, Kotlin 관용구, 네이밍 |
| 5 | 김태현 | Testing | 테스트 존재 여부, 커버리지, 누락 시나리오 |
| 6 | 윤지아 | Domain Expert | 로그 탐지 비즈니스 로직, 규칙 엔진 정확성 |

**Leads (순차 실행)**
| 이름 | 역할 | 담당 |
|------|------|------|
| 최민준 | Tech Lead | Reviewer 1~3 종합, 기술 심각도, 반복 실수 추적 |
| 한소율 | Quality Lead | Reviewer 4~6 종합, 품질 심각도, 학습 추출 |

**Git 워크플로우 에이전트**
| 이름 | 역할 | 담당 커맨드 |
|------|------|------------|
| 송준호 | Commit Craft | `/commit` |
| 오세린 | Ship Captain | `/ship` |
| 강민재 | Gate Keeper | `/ship --merge` |

### 전체 워크플로우

```
/create-issue-branch {번호}
         │
         ▼
     코딩 작업
         │
         ▼
     /test                  ← 테스트 자동 생성
         │
         ▼
     /commit                ← 커밋 + 빌드 검증
         │
         ▼
     /ship                  ← PR 생성
         │
         ▼
     /review                ← 6인 리뷰 + 2인 리드
         │
         ▼
     /ship --merge          ← 체크리스트 → squash merge
         │
         ▼
     이슈 자동 close + 브랜치 삭제
```

## 문서 구조

```
doc/
├── design/
│   └── design.md                 # 아키텍처, 데이터 모델, 탐지 규칙, 로드맵
├── review/                       # 코드 리뷰 결과 (커밋별)
│   └── 0000-00-00-baseline.md    # 초기 Baseline (/init-review 산출물)
├── lessons/                      # 학습 내용 (주제별)
└── memory/                       # 누적 컨텍스트
    ├── project-context.md        # 프로젝트 상황, 반복 실수, 기술 부채
    ├── domain-glossary.md        # 도메인 용어 사전
    └── review-checklist.md       # 기술/품질 체크리스트
```

## 주의사항

- **Spring Boot 4.0.5 + Kotlin 2.3.20** 조합은 공식 지원 이전 (Spring Boot 4.1 에서 공식 지원 예정). 컴파일/런타임은 정상이며 BOM 오버라이드로 대응 중. 이슈 발생 시 Kotlin 2.2.21 다운그레이드 검토.
- **detekt 2.0.0-alpha.2** 는 알파 버전 — Kotlin 2.3 호환을 위한 선택. stable 릴리즈 시 교체 권장.
- **인프라는 원격 공유 서버**를 사용. `.env` 에 접속 정보가 있으며 `.gitignore` 처리됨. 원격 PostgreSQL 에 `logdetect` DB 수동 생성 필요.
