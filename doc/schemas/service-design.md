## 5. 서비스별 상세 설계

> **컨벤션 업데이트 (2026-04-13)**: 이 섹션은 초기 설계 이후 여러 차례 리뷰에서 확정된 규칙을 반영하여 갱신됨. 권위 있는 규칙은 `CLAUDE.md`를 참조.
>
> **적용된 주요 규칙:**
> - 패키지: `application/port/inbound`, `application/port/outbound` (이전 `port/in`, `port/out` 금지)
> - UseCase 구현체: `{Resource}Handler` (Service/Impl 금지)
> - 아웃바운드 포트: `{Domain}{Infra}Port` — Message(Kafka) / Persistence(JPA) / Cache(Redis) / Search(ES)
> - Command DTO 분리: `application/dto/command/`, UseCase 파일 내부 data class 금지
> - application 계층 Spring 어노테이션 금지 (예외: `@Transactional` 허용). Bean은 `infrastructure/config`에서 `@Bean`으로 등록
> - 도메인 모델에 카드번호 원문 필드 금지 — `encryptedCardNumber` + `maskedCardNumber`만 보유
> - 응답 래퍼: 각 서비스 로컬 `ApiResponse` (fds-common의 `CommonApiResponse`는 `@Deprecated`)
> - 통화/국가 코드: `CurrencyCode` / `CountryCode` VO (domain/vo)
> - 로그 마스킹: Logback `%mask` / `%maskEx` 컨버터 (PAN 자동 치환)
> - 예외 매핑: `GlobalExceptionHandler`가 `Domain*Exception`을 HTTP 상태코드로 변환

---

### 5.1 fds-common

공통 Kafka 이벤트 스키마 + 공용 도메인(RiskLevel) + 예외 계층. **HTTP 응답 래퍼는 각 서비스 인프라로 분리 — fds-common은 이벤트 스키마 전용 방향.**

- `CommonApiResponse` (web/)는 `@Deprecated`. 신규 서비스는 `{service}/infrastructure/adapter/inbound/web/response/ApiResponse.kt`를 로컬로 둔다. `doc/policy/api-response.md` 참조.
- `CardMasking` (security/) — PAN 앞6+뒤4 형식 (PCI-DSS)
- `DomainExceptions` — NotFound / AlreadyExists / Conflict / Validation / InvalidState / AccessDenied / AuthenticationRequired

#### Kafka 이벤트

```kotlin
// TransactionEvent — 거래 발생 이벤트
// ⚠️ 발행 전 cardNumber는 반드시 마스킹된 상태여야 함 (CardMasking.mask 적용)
data class TransactionEvent(
    val transactionId: String,
    val userId: String,
    val maskedCardNumber: String, // 앞6+뒤4, 원문 발행 금지
    val amount: BigDecimal,
    val currency: String,         // ISO 4217 대문자 3자리
    val merchantName: String,
    val merchantCategory: String, // CAFE, GROCERY, ONLINE, ...
    val country: String,          // ISO 3166-1 alpha-3 대문자 (KOR, USA, ...)
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
)

// DetectionResultEvent — 탐지 결과 이벤트
data class DetectionResultEvent(
    val detectionId: String,
    val transactionId: String,
    val userId: String,
    val riskLevel: RiskLevel,     // LOW, MEDIUM, HIGH, CRITICAL
    val ruleNames: List<String>,  // 적용된 규칙 목록
    val riskScore: Int,           // 0~100
    val timestamp: Instant,
)
```

#### Kafka 토픽

| 토픽 | Producer | Consumer | 설명 |
|------|----------|----------|------|
| `transaction-events` | Transaction Service | Detection Service | 거래 발생 이벤트 |
| `detection-results` | Detection Service | Alert Service | 탐지 결과 이벤트 |

---

### 5.2 fds-transaction-service

거래 데이터를 수집·저장·조회하는 서비스. **현재 구현됨** (2026-04-12/13 세션).

> 🟢 **구현 상태**: Register + Get 완료 (`RegisterTransactionUseCase`, `GetTransactionUseCase`, `TransactionHandler`, `TransactionPersistenceAdapter`, `TransactionWebAdapter`, `GlobalExceptionHandler`, 단위/통합 테스트 36건). 미구현: Search(ES) 연동, DetectionResult 적용 UseCase, Kafka Producer(TransactionMessagePort).

#### 헥사고날 구조 (현재 상태)

```
fds-transaction-service/
├── domain/
│   ├── model/
│   │   ├── Transaction.kt              # cardNumber 원문 필드 없음 — encrypted+masked만
│   │   └── DetectionResult.kt          # Detection 결과 적용용 도메인
│   ├── enums/
│   │   └── TransactionStatus.kt
│   └── vo/
│       ├── CountryCode.kt              # @JvmInline value class — ISO 3166-1 alpha-3
│       └── CurrencyCode.kt              # @JvmInline value class — ISO 4217
│
├── application/
│   ├── dto/
│   │   └── command/
│   │       └── RegisterTransactionCommand.kt   # init require() 자체 검증
│   ├── handler/
│   │   └── TransactionHandler.kt        # @Transactional (application 예외 허용)
│   └── port/
│       ├── inbound/
│       │   ├── RegisterTransactionUseCase.kt
│       │   └── GetTransactionUseCase.kt
│       └── outbound/
│           ├── TransactionPersistencePort.kt    # JPA
│           ├── CardEncryptor.kt                  # 카드번호 암호화 포트
│           ├── TransactionMessagePort.kt         # Kafka Producer (계획)
│           └── TransactionSearchPort.kt          # ES (계획)
│
└── infrastructure/
    ├── adapter/
    │   ├── inbound/
    │   │   └── web/
    │   │       ├── transaction/
    │   │       │   ├── TransactionWebAdapter.kt     # POST/GET /api/v1/transactions
    │   │       │   └── dto/
    │   │       │       ├── RegisterTransactionRequest.kt  # Bean Validation
    │   │       │       └── TransactionResponse.kt         # toResponse() 확장 함수
    │   │       ├── exception/
    │   │       │   └── GlobalExceptionHandler.kt    # Domain*Exception → HTTP
    │   │       └── response/
    │   │           └── ApiResponse.kt                # 로컬 래퍼
    │   └── outbound/
    │       ├── crypto/
    │       │   └── PassthroughCardEncryptor.kt       # @ConditionalOnProperty fail-closed
    │       └── persistence/
    │           └── transaction/
    │               ├── adapter/TransactionPersistenceAdapter.kt
    │               ├── entity/TransactionEntity.kt
    │               └── repository/TransactionJpaRepository.kt
    └── config/
        ├── TransactionApplicationConfig.kt   # Handler + Clock @Bean 등록
        └── logging/
            ├── CardNumberMaskingConverter.kt       # %mask
            ├── CardNumberMaskingThrowableConverter.kt  # %maskEx
            └── PanMasker.kt                          # 공통 마스킹 유틸
```

#### 도메인 모델

```kotlin
// domain/model/Transaction.kt
data class Transaction(
    val transactionId: String,
    val userId: String,
    val maskedCardNumber: String,        // 앞6+뒤4 (표시용)
    val encryptedCardNumber: String,     // 저장용 (Handler에서 CardEncryptor로 생성)
    val amount: BigDecimal,
    val currency: CurrencyCode,          // VO
    val merchantName: String,
    val merchantCategory: String,
    val country: CountryCode,            // VO
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val riskLevel: RiskLevel? = null,
    val riskScore: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    // 상태 전이 메서드: applyDetectionResult / markSuspicious / approveAfterReview / blockAfterReview
    // 모두 PENDING/SUSPICIOUS 등 진입 상태 검증 require() 포함

    companion object {
        fun create(
            transactionId: String, userId: String,
            plainCardNumber: String, encryptedCardNumber: String,
            amount: BigDecimal, currency: CurrencyCode, ...
        ): Transaction = Transaction(
            maskedCardNumber = CardMasking.mask(plainCardNumber),
            encryptedCardNumber = encryptedCardNumber,
            // ...
        )
    }
}

enum class TransactionStatus { PENDING, APPROVED, SUSPICIOUS, BLOCKED }
```

**핵심 설계 포인트:**
- `plainCardNumber`는 **팩토리 파라미터로만** 전달 — 도메인 필드에 저장하지 않음
- `encryptedCardNumber`는 Handler가 `CardEncryptor.encrypt()` 호출 후 주입
- `TransactionPersistencePort.save(tx)`는 **단일 인자** — port 경계에서 plaintext 완전 차단

#### Inbound Port (UseCase)

```kotlin
// application/port/inbound/RegisterTransactionUseCase.kt
interface RegisterTransactionUseCase {
    fun register(command: RegisterTransactionCommand): Transaction
}

// application/port/inbound/GetTransactionUseCase.kt
interface GetTransactionUseCase {
    fun getByTransactionId(transactionId: String): Transaction
}

// application/dto/command/RegisterTransactionCommand.kt
data class RegisterTransactionCommand(
    val transactionId: String,  // 클라이언트 주입 idempotency 키 — doc/policy/transaction-id.md
    val userId: String,
    val cardNumber: String,     // plaintext — Handler가 즉시 encrypt 후 버림
    val amount: BigDecimal,
    val currency: String,       // "USD" — VO 변환은 Handler
    val country: String,        // "USA"
    val merchantName: String, val merchantCategory: String, val city: String,
    val latitude: Double, val longitude: Double,
) {
    init {
        // 비-web 호출자(Kafka Consumer 등)도 검증되도록 require() 포함
        require(TRANSACTION_ID_REGEX.matches(transactionId))
        require(CARD_NUMBER_REGEX.matches(cardNumber))
        require(amount > BigDecimal.ZERO)
        require(ISO_3_UPPER.matches(currency))
        require(ISO_3_UPPER.matches(country))
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}
```

#### Outbound Port

```kotlin
// application/port/outbound/TransactionPersistencePort.kt
interface TransactionPersistencePort {
    fun save(transaction: Transaction): Transaction  // 단일 인자 — plaintext 없음
    fun findByTransactionId(transactionId: String): Transaction?
    fun existsByTransactionId(transactionId: String): Boolean
}

// application/port/outbound/CardEncryptor.kt
interface CardEncryptor {
    fun encrypt(plain: String): String
    // decrypt는 YAGNI — 필요 시 별도 TokenizationPort
}

// 계획: TransactionMessagePort(Kafka), TransactionSearchPort(ES)
```

#### Application Handler

```kotlin
// application/handler/TransactionHandler.kt
// @Component 금지 — infrastructure/config/TransactionApplicationConfig에서 @Bean 등록
class TransactionHandler(
    private val transactionPersistencePort: TransactionPersistencePort,
    private val cardEncryptor: CardEncryptor,
    private val clock: Clock,
) : RegisterTransactionUseCase, GetTransactionUseCase {

    @Transactional  // CLAUDE.md 예외: application/handler의 @Transactional 허용
    override fun register(command: RegisterTransactionCommand): Transaction {
        val encrypted = cardEncryptor.encrypt(command.cardNumber)
        val transaction = Transaction.create(
            transactionId = command.transactionId,
            plainCardNumber = command.cardNumber,  // 팩토리 내부에서 즉시 mask
            encryptedCardNumber = encrypted,
            currency = CurrencyCode(command.currency),
            country = CountryCode(command.country),
            // ...
            now = Instant.now(clock),
        )
        return transactionPersistencePort.save(transaction)
        // 중복은 adapter가 unique constraint catch → DomainAlreadyExistsException
    }

    @Transactional(readOnly = true)
    override fun getByTransactionId(transactionId: String): Transaction =
        transactionPersistencePort.findByTransactionId(transactionId)
            ?: throw DomainNotFoundException("거래를 찾을 수 없습니다: $transactionId")
}
```

#### DB 스키마 (Flyway)

```sql
-- V1__create_transactions.sql
CREATE TABLE transactions (
    id                      BIGSERIAL      PRIMARY KEY,          -- JPA GenerationType.IDENTITY
    transaction_id          VARCHAR(36)    UNIQUE NOT NULL,      -- 클라이언트 주입 idempotency 키
    user_id                 VARCHAR(20)    NOT NULL,
    encrypted_card_number   VARCHAR(256)   NOT NULL,             -- KMS/Passthrough 경유 필수
    masked_card_number      VARCHAR(20)    NOT NULL,             -- 앞6+뒤4
    amount                  DECIMAL(18,2)  NOT NULL,
    currency                VARCHAR(3)     NOT NULL,             -- ISO 4217
    merchant_name           VARCHAR(100)   NOT NULL,
    merchant_category       VARCHAR(30)    NOT NULL,
    country                 VARCHAR(3)     NOT NULL,             -- ISO 3166-1 alpha-3
    city                    VARCHAR(50)    NOT NULL,
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    risk_level              VARCHAR(10),
    risk_score              INTEGER,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_status CHECK (status IN ('PENDING','APPROVED','SUSPICIOUS','BLOCKED')),
    CONSTRAINT chk_risk_level CHECK (risk_level IS NULL OR risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX idx_transactions_transaction_id ON transactions(transaction_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_country ON transactions(country);
```

#### ES 인덱스 매핑

```json
{
  "mappings": {
    "properties": {
      "transactionId":    { "type": "keyword" },
      "userId":           { "type": "keyword" },
      "amount":           { "type": "double" },
      "currency":         { "type": "keyword" },
      "merchantName":     { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
      "merchantCategory": { "type": "keyword" },
      "country":          { "type": "keyword" },
      "city":             { "type": "keyword" },
      "location":         { "type": "geo_point" },
      "status":           { "type": "keyword" },
      "createdAt":        { "type": "date" }
    }
  }
}
```

#### API 스펙

| Method | Path | 설명 | 상태 |
|--------|------|------|------|
| POST | `/api/v1/transactions` | 거래 등록 (201 / 409 / 400) | 🟢 구현됨 |
| GET | `/api/v1/transactions/{transactionId}` | 거래 단건 조회 (200 / 404) | 🟢 구현됨 |
| GET | `/api/v1/transactions?userId=&page=&size=` | 유저별 목록 | 🟡 계획 |
| GET | `/api/v1/transactions/search?keyword=&page=&size=` | 키워드 검색 (ES) | 🟡 계획 |

**POST /api/v1/transactions Request Body:**
```json
{
  "transactionId": "TX-00000001",
  "userId": "USER_00001",
  "cardNumber": "4123456789012345",
  "amount": 55000,
  "currency": "KRW",
  "country": "KOR",
  "merchantName": "스타벅스",
  "merchantCategory": "CAFE",
  "city": "서울",
  "latitude": 37.5665,
  "longitude": 126.978
}
```

검증: Bean Validation + `RegisterTransactionCommand.init { require(...) }` 이중 보호. 실패 시 `GlobalExceptionHandler`가 400 + 필드 목록(최대 3개 + "외 N개")으로 응답.

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "transactionId": "TX-00000001",
    "userId": "USER_00001",
    "maskedCardNumber": "412345******2345",
    "amount": 55000,
    "currency": "KRW",
    "country": "KOR",
    "merchantName": "스타벅스",
    "merchantCategory": "CAFE",
    "city": "서울",
    "latitude": 37.5665,
    "longitude": 126.978,
    "status": "PENDING",
    "riskLevel": null,
    "riskScore": null,
    "createdAt": "2026-04-12T10:30:00Z",
    "updatedAt": "2026-04-12T10:30:00Z"
  },
  "message": null,
  "errorCode": null,
  "timestamp": "2026-04-12T10:30:00Z"
}
```

**Response (409 Conflict — 중복 transactionId):**
```json
{
  "success": false,
  "message": "이미 등록된 거래입니다: TX-00000001",
  "errorCode": "ALREADY_EXISTS",
  "timestamp": "..."
}
```

#### 보안 설정

- **카드번호 암호화**: `CardEncryptor` 포트 주입 — prod는 KMS 구현체 필수 (`doc/policy/kms-encryption.md`)
- **Passthrough 스텁**: `FDS_CRYPTO_PASSTHROUGH_ENABLED=true` 명시 설정 시에만 활성 (fail-closed 기본)
- **로그 마스킹**: `%mask` / `%maskEx` Logback 컨버터가 13~19자리 숫자 자동 마스킹
- **요청 로그**: Bean Validation 실패 시 필드명만 응답(최대 3개), rejectedValue echo 금지
- **합성 PAN 정책**: dev/staging은 합성 카드번호만 사용 (`doc/policy/synthetic-pan.md`)

---

### 5.3 fds-detection-service

거래 이벤트를 소비하여 이상 여부를 판정하는 서비스. **구현 예정** (`feat/detection-service-skeleton` 브랜치에서 진행).

#### 헥사고날 구조

```
fds-detection-service/
├── domain/
│   ├── model/
│   │   ├── Detection.kt                # 탐지 결과 도메인 (score aggregate)
│   │   ├── DetectionContext.kt         # 입력: 거래 + 사용자 최근 이력
│   │   ├── RuleResult.kt               # 단일 규칙 평가 결과
│   │   └── UserBehaviorProfile.kt      # Redis 조회 결과
│   ├── rule/                           # ← rule은 pure 도메인 (Spring 의존 0)
│   │   ├── DetectionRule.kt            # 인터페이스: evaluate(ctx): RuleResult
│   │   ├── HighAmountRule.kt
│   │   ├── RapidSuccessionRule.kt
│   │   ├── GeoImpossibleTravelRule.kt
│   │   └── MidnightTransactionRule.kt
│   └── enums/
│       ├── DetectionDecision.kt        # APPROVE / REVIEW / BLOCK
│       └── (RiskLevel은 fds-common)
│
├── application/
│   ├── dto/
│   │   ├── command/EvaluateTransactionCommand.kt   # Kafka Consumer가 생성
│   │   └── result/DetectionReport.kt
│   ├── handler/
│   │   └── DetectionHandler.kt         # Rule 오케스트레이션, @Transactional
│   └── port/
│       ├── inbound/
│       │   ├── EvaluateTransactionUseCase.kt
│       │   └── GetDetectionResultUseCase.kt
│       └── outbound/
│           ├── UserBehaviorCachePort.kt            # Redis (Load+Save 단일 포트)
│           ├── DetectionResultPersistencePort.kt   # JPA (또는 Redis 대체)
│           └── DetectionResultMessagePort.kt       # Kafka Producer
│
└── infrastructure/
    ├── adapter/
    │   ├── inbound/
    │   │   ├── web/
    │   │   │   ├── DetectionWebAdapter.kt
    │   │   │   └── exception/GlobalExceptionHandler.kt
    │   │   └── message/
    │   │       └── TransactionEventConsumer.kt     # Kafka Consumer
    │   └── outbound/
    │       ├── cache/
    │       │   └── UserBehaviorRedisAdapter.kt     # Sorted Set + Lua 원자화
    │       ├── persistence/
    │       │   └── detection/
    │       │       ├── adapter/DetectionResultPersistenceAdapter.kt
    │       │       ├── entity/DetectionResultEntity.kt
    │       │       └── repository/DetectionResultJpaRepository.kt
    │       └── message/
    │           └── DetectionResultKafkaAdapter.kt
    └── config/
        ├── DetectionApplicationConfig.kt  # Handler + Rule 리스트 @Bean
        ├── RuleConfig.kt                   # 각 Rule @Bean (추가/제거 용이)
        ├── KafkaConsumerConfig.kt          # concurrency 12+, trusted.packages 구체 FQCN
        ├── KafkaProducerConfig.kt
        └── RedisConfig.kt
```

#### 도메인 모델

```kotlin
// domain/model/Detection.kt
data class Detection(
    val detectionId: String,
    val transactionId: String,
    val userId: String,
    val riskLevel: RiskLevel,
    val riskScore: Int,                  // 0~100, 합산 후 cap
    val triggeredRules: List<String>,
    val detectedAt: Instant,
) {
    companion object {
        fun aggregate(
            detectionId: String, transactionId: String, userId: String,
            results: List<RuleResult>, now: Instant,
        ): Detection {
            val triggered = results.filter { it.triggered }
            val totalScore = triggered.sumOf { it.score }.coerceAtMost(MAX_RISK_SCORE)
            return Detection(
                detectionId = detectionId,
                transactionId = transactionId,
                userId = userId,
                riskLevel = riskLevelOf(totalScore),
                riskScore = totalScore,
                triggeredRules = triggered.map { it.ruleName },
                detectedAt = now,
            )
        }
        private const val MAX_RISK_SCORE = 100
        private fun riskLevelOf(score: Int): RiskLevel = when {
            score < 30 -> RiskLevel.LOW
            score < 60 -> RiskLevel.MEDIUM
            score < 80 -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }
    }
}

// domain/model/DetectionContext.kt
data class data class DetectionContext(
    val transactionId: String,
    val userId: String,
    val amount: BigDecimal,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val occurredAt: Instant,
    val profile: UserBehaviorProfile,    // 과거 이력 스냅샷
)(
    val transactionId: String,
    val userId: String,
    val amount: BigDecimal,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val occurredAt: Instant,
    val profile: UserBehaviorProfile,    // 과거 이력 스냅샷
)

// domain/model/UserBehaviorProfile.kt
data class UserBehaviorProfile(
    val userId: String,
    val recentTransactionCount: Int,     // 최근 5분 거래 건수
    val averageAmount: BigDecimal,       // 평균 거래 금액
    val lastCountry: String?,
    val lastOccurredAt: Instant?,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
)

// domain/rule/DetectionRule.kt — pure 도메인 인터페이스
interface DetectionRule {
    val name: String
    fun evaluate(context: DetectionContext): RuleResult
}

// domain/model/RuleResult.kt
data class RuleResult(
    val ruleName: String,
    val triggered: Boolean,
    val score: Int,        // 0~100, 기여 점수
    val reason: String,
)
```

#### 탐지 규칙 상세

| 규칙 | 클래스 | 조건 | 점수 |
|------|--------|------|------|
| 고액 거래 | `HighAmountRule` | 평균 금액의 5배 초과 | 40 |
| 연속 거래 | `RapidSuccessionRule` | 5분 내 5건 이상 | 30 |
| 불가능 이동 | `GeoImpossibleTravelRule` | 직전 거래와 시속 900km 초과 | 50 |
| 새벽 고액 | `MidnightTransactionRule` | 00~05시(KST) + 50만원 이상 | 25 |

**상수 관리**: `DetectionConstants.kt`에 `const val` 통합 또는 각 Rule의 `companion object`에 선언. SCREAMING_SNAKE_CASE.

**리스크 레벨 산정:**
```
riskScore = min(100, sum(각 규칙 score))
- 0~29:   LOW
- 30~59:  MEDIUM
- 60~79:  HIGH
- 80~100: CRITICAL
```

**Rule 등록**: `infrastructure/config/RuleConfig.kt`에서 각 Rule을 `@Bean`으로 등록 → Handler가 `List<DetectionRule>` 주입받아 순회. 규칙 추가는 Bean 추가 한 줄.

#### 탐지 규칙 구현 가이드

```kotlin
// domain/rule/HighAmountRule.kt — pure 도메인, Spring 의존 0
class HighAmountRule : DetectionRule {
    override val name = "HIGH_AMOUNT"

    override fun evaluate(context: DetectionContext): RuleResult {
        val threshold = context.profile.averageAmount.multiply(BigDecimal(MULTIPLIER))
        val triggered = context.amount > threshold
        return RuleResult(
            ruleName = name,
            triggered = triggered,
            score = if (triggered) SCORE else 0,
            reason = if (triggered) "금액 ${context.amount} > 평균의 ${MULTIPLIER}배" else "",
        )
    }
    companion object {
        private const val MULTIPLIER = 5
        private const val SCORE = 40
    }
}

// domain/rule/GeoImpossibleTravelRule.kt
class GeoImpossibleTravelRule : DetectionRule {
    override val name = "GEO_IMPOSSIBLE_TRAVEL"

    override fun evaluate(context: DetectionContext): RuleResult {
        val profile = context.profile
        if (profile.lastLatitude == null || profile.lastOccurredAt == null) {
            return RuleResult(name, triggered = false, score = 0, reason = "")
        }
        val distanceKm = haversine(
            profile.lastLatitude, profile.lastLongitude!!,
            context.latitude, context.longitude,
        )
        val elapsedHours = Duration.between(profile.lastOccurredAt, context.occurredAt).toSeconds() / 3600.0
        val speedKmH = if (elapsedHours > 0) distanceKm / elapsedHours else Double.MAX_VALUE
        val triggered = speedKmH > MAX_FEASIBLE_SPEED_KMH
        return RuleResult(
            ruleName = name,
            triggered = triggered,
            score = if (triggered) SCORE else 0,
            reason = if (triggered) "이동 속도 ${speedKmH.toInt()}km/h" else "",
        )
    }
    companion object {
        private const val MAX_FEASIBLE_SPEED_KMH = 900
        private const val SCORE = 50
    }
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine 공식 — 두 좌표 간 거리(km)
        // ... (구현)
    }
}
```

#### Handler 오케스트레이션

```kotlin
// application/handler/DetectionHandler.kt
class DetectionHandler(
    private val userBehaviorCachePort: UserBehaviorCachePort,
    private val persistencePort: DetectionResultPersistencePort,
    private val messagePort: DetectionResultMessagePort,
    private val rules: List<DetectionRule>,   // RuleConfig에서 @Bean 리스트 주입
    private val clock: Clock,
) : EvaluateTransactionUseCase {

    @Transactional
    override fun evaluate(command: EvaluateTransactionCommand): Detection {
        val profile = userBehaviorCachePort.load(command.userId)
        val context = DetectionContext(
            transactionId = command.transactionId,
            userId = command.userId,
            amount = command.amount,
            country = command.country,
            latitude = command.latitude,
            longitude = command.longitude,
            occurredAt = command.occurredAt,
            profile = profile,
        )
        val results = rules.map { it.evaluate(context) }
        val detection = Detection.aggregate(
            detectionId = UUID.randomUUID().toString(),
            transactionId = command.transactionId,
            userId = command.userId,
            results = results,
            now = Instant.now(clock),
        )
        persistencePort.save(detection)
        messagePort.publish(detection)
        userBehaviorCachePort.recordTransaction(command)   // 다음 거래용 윈도우 갱신
        return detection
    }
}
```

#### Redis 데이터 구조

| Key Pattern | Type | TTL | 용도 |
|-------------|------|-----|------|
| `user:behavior:{userId}` | Hash | 30분 | 유저 행동 프로필 (평균 금액, 마지막 위치 등) |
| `user:tx:window:{userId}` | Sorted Set | 5분 (sliding) | 최근 거래 이력 (score=epochMillis) |
| `detection:result:{transactionId}` | String (JSON) | 1시간 | 탐지 결과 캐싱 |

**중요**: 이전 리뷰 교훈 (`doc/memory/review-checklist.md`) — **INCR+TTL 금지**, Sorted Set + Lua 원자화 필수.

```lua
-- record_transaction.lua (Sorted Set + 슬라이딩 윈도우)
-- KEYS[1] = user:tx:window:{userId}
-- ARGV[1] = transactionId
-- ARGV[2] = now epochMillis
-- ARGV[3] = window start epochMillis (now - 5min)
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3])
redis.call('EXPIRE', KEYS[1], 600)
return redis.call('ZCARD', KEYS[1])
```

```
# user:behavior:USER_00001
HSET user:behavior:USER_00001
    avgAmount       "45000"
    lastCountry     "KOR"
    lastTime        "2026-04-12T10:30:00Z"
    lastLat         "37.5665"
    lastLon         "126.978"
```

#### Kafka 연동

- **Consumer**: `transaction-events` 토픽 구독, group id `fds-detection-service`
  - `concurrency: 12+` (review-checklist 요구)
  - `trusted.packages: com.gijun.fds.common.event` (구체 FQCN, 와일드카드 금지)
  - 수신 후 `EvaluateTransactionCommand` 생성 → `DetectionHandler.evaluate()` 호출
- **Producer**: `detection-results` 토픽 발행
  - `DetectionResultEvent` (fds-common 정의) 매핑
  - linger.ms / batch.size 최적화 (후속 이슈)

#### API 스펙

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/detections/{transactionId}` | 거래별 탐지 결과 조회 |
| GET | `/api/v1/detections?userId=&riskLevel=&page=&size=` | 탐지 결과 목록 |
| GET | `/api/v1/detections/stats` | 탐지 통계 (기간별 건수, 레벨별 분포) |

응답 포맷은 transaction-service와 동일한 로컬 `ApiResponse` 래퍼 사용. `GlobalExceptionHandler`로 도메인 예외 매핑.

---

### 5.4 fds-alert-service

탐지 결과를 받아 알림을 생성/관리하는 서비스. **구현 예정**.

#### 헥사고날 구조

```
fds-alert-service/
├── domain/
│   ├── model/
│   │   ├── Alert.kt
│   │   └── AlertPolicy.kt
│   └── enums/
│       └── AlertStatus.kt
│
├── application/
│   ├── dto/
│   │   └── command/
│   │       ├── CreateAlertCommand.kt
│   │       └── UpdateAlertStatusCommand.kt
│   ├── handler/
│   │   └── AlertHandler.kt             # Create + Get + Update 통합
│   └── port/
│       ├── inbound/
│       │   ├── CreateAlertUseCase.kt
│       │   ├── GetAlertUseCase.kt
│       │   └── UpdateAlertUseCase.kt
│       └── outbound/
│           ├── AlertPersistencePort.kt         # JPA
│           ├── AlertDedupCachePort.kt          # Redis 중복 방지 (Lua 원자화)
│           └── AlertNotificationPort.kt        # 알림 발송 확장 포인트
│
└── infrastructure/
    ├── adapter/
    │   ├── inbound/
    │   │   ├── web/
    │   │   │   ├── AlertWebAdapter.kt
    │   │   │   ├── exception/GlobalExceptionHandler.kt
    │   │   │   └── response/ApiResponse.kt
    │   │   └── message/
    │   │       └── DetectionResultConsumer.kt  # Kafka Consumer
    │   └── outbound/
    │       ├── persistence/
    │       │   └── alert/
    │       │       ├── adapter/AlertPersistenceAdapter.kt
    │       │       ├── entity/AlertEntity.kt
    │       │       └── repository/AlertJpaRepository.kt
    │       ├── cache/
    │       │   └── AlertDedupRedisAdapter.kt
    │       └── notification/
    │           └── LogAlertNotificationAdapter.kt   # 1차: 로그, 2차: Slack/Email
    └── config/
        ├── AlertApplicationConfig.kt
        ├── KafkaConsumerConfig.kt
        └── RedisConfig.kt
```

#### 도메인 모델

```kotlin
// domain/model/Alert.kt
data class Alert(
    val id: String,
    val transactionId: String,
    val userId: String,
    val riskLevel: RiskLevel,
    val riskScore: Int,
    val triggeredRules: List<String>,
    val status: AlertStatus,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val resolvedBy: String? = null,
    val memo: String? = null,
)

enum class AlertStatus {
    OPEN,       // 미처리
    REVIEWING,  // 검토 중
    CONFIRMED,  // 이상거래 확인
    DISMISSED,  // 정상 판정
}
```

#### 알림 중복 방지 정책

**Lua 스크립트 원자화 필수** (SISMEMBER + SADD + EXPIRE를 별도 호출 시 race condition).

```lua
-- alert_dedup.lua
-- KEYS[1] = alert:dedup:{userId}
-- ARGV[1] = transactionId
-- ARGV[2] = ttl seconds
local exists = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if exists == 1 then return 0 end
redis.call('SADD', KEYS[1], ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1
```

- Key: `alert:dedup:{userId}` (Set)
- TTL: 60초 (유저별 1분 dedup window)
- Handler는 반환값 0이면 skip, 1이면 Alert 생성 진행

#### DB 스키마 (Flyway)

```sql
-- V1__create_alerts.sql
CREATE TABLE alerts (
    id               VARCHAR(36)   PRIMARY KEY,
    transaction_id   VARCHAR(36)   NOT NULL,
    user_id          VARCHAR(36)   NOT NULL,
    risk_level       VARCHAR(10)   NOT NULL,
    risk_score       INTEGER       NOT NULL,
    triggered_rules  TEXT          NOT NULL,  -- JSON array
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMP WITH TIME ZONE,
    resolved_by      VARCHAR(50),
    memo             TEXT,

    CONSTRAINT chk_risk_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN','REVIEWING','CONFIRMED','DISMISSED'))
);

CREATE INDEX idx_alerts_user_id ON alerts(user_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_risk_level ON alerts(risk_level);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);
```

#### API 스펙

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/alerts` | 알림 목록 (필터: status, riskLevel, userId) |
| GET | `/api/v1/alerts/{id}` | 알림 상세 |
| PATCH | `/api/v1/alerts/{id}/status` | 알림 상태 변경 (REVIEWING, CONFIRMED, DISMISSED) |
| GET | `/api/v1/alerts/stats` | 알림 통계 |

**PATCH /api/v1/alerts/{id}/status Request Body:**
```json
{
  "status": "CONFIRMED",
  "resolvedBy": "admin",
  "memo": "해외 고액 거래 확인, 본인 아님"
}
```

---

### 5.5 fds-gateway

Spring Cloud Gateway Server WebMVC 기반 API Gateway.

#### 구조 (🟢 구현됨)

```
fds-gateway/
└── infrastructure/
    ├── adapter/
    │   └── inbound/
    │       ├── web/HealthWebAdapter.kt
    │       └── filter/
    │           ├── LoggingFilter.kt       # 요청/응답 로깅
    │           └── RateLimitFilter.kt     # IP별 TokenBucket (Caffeine 기반)
    └── config/
        ├── RouteConfig.kt             # lb:// 서비스 디스커버리 경유
        └── SecurityConfig.kt           # BCrypt + 엔드포인트별 authenticated + denyAll
```

**Rate Limit 구현 교훈**: `ConcurrentHashMap` → `Caffeine`(100K entries, 10min TTL) 전환. `TokenBucket`은 `AtomicInteger` CAS.

#### 라우팅 규칙

| Path | Target |
|------|--------|
| `/api/v1/transactions/**` | Transaction Service (:8081) |
| `/api/v1/detections/**` | Detection Service (:8082) |
| `/api/v1/alerts/**` | Alert Service (:8083) |

#### Rate Limiting

- IP별 초당 1,000건 (TokenBucket 알고리즘)
- 초과 시 `429 Too Many Requests` 응답

---

### 5.6 fds-generator

테스트 데이터 생성기. Ktor Client로 Gateway에 거래 데이터를 전송한다.

#### 구조 (🟢 구현됨)

```
fds-generator/
├── domain/
│   └── model/
│       ├── TransactionData.kt
│       └── TransactionDataFactory.kt       # 거래 데이터 생성 + FraudType 분기
├── application/
│   ├── handler/
│   │   └── GeneratorHandler.kt             # Semaphore(200) + AtomicInteger currentRate
│   └── port/
│       ├── inbound/GeneratorUseCase.kt
│       └── outbound/TransactionSendPort.kt
└── infrastructure/
    ├── adapter/
    │   ├── inbound/web/GeneratorWebAdapter.kt
    │   └── outbound/client/KtorTransactionSendAdapter.kt
    └── config/
        ├── GeneratorApplicationConfig.kt    # Handler @Bean (반환 타입은 UseCase 인터페이스)
        ├── KtorClientConfig.kt
        └── SecurityConfig.kt
```

**구현 교훈**:
- `start()` 코루틴은 `Semaphore(200)`로 동시성 제한
- `currentRate`는 `AtomicInteger`
- `@Bean` 반환 타입은 구체 클래스가 아닌 UseCase 인터페이스

#### API 스펙

**Scenario CRUD** (`/api/v1/scenarios`)

| Method | Path | Body / 설명 |
|--------|------|-------------|
| POST | `/api/v1/scenarios` | `{ name, type:RequestType, attackType, successful, rate, fraudRatio[0~100] }` — 시나리오 생성 |
| GET | `/api/v1/scenarios` | 전체 목록 |
| GET | `/api/v1/scenarios/{id}` | 단건 조회 |
| DELETE | `/api/v1/scenarios/{id}` | 삭제 |

**Generator 실행** (`/api/v1/generator`)

| Method | Path | Body / 설명 |
|--------|------|-------------|
| POST | `/api/v1/generator/start` | `{ scenarioId }` — 시나리오 정의대로 무한 실행 (rate=EPS) |
| POST | `/api/v1/generator/burst` | `{ scenarioId }` — `rate` 만큼 1회 폭주 송신 |
| DELETE | `/api/v1/generator/stop/{scenarioId}` | 단일 시나리오 정지 |
| DELETE | `/api/v1/generator/stop-all` | 전체 시나리오 정지 |
| GET | `/api/v1/generator/status` | `List<{ scenarioId, running, totalSent, totalFailed, configuredRate }>` — 활성/카운터가 남은 모든 시나리오 |

**시나리오 동시 실행**: 서로 다른 `scenarioId` 는 동시 가동 가능 (인메모리 `ConcurrentMap<Long, Job>` + Redisson 키 namespace 분리). 같은 `scenarioId` 두 번째 start 호출은 무시됨.

**fraudRatio 단위**: `Scenario.fraudRatio: Long` 은 **0~100 퍼센트 의미** (Handler 가 `/100.0` 으로 Double 변환). DB 컬럼 변경 회피용.

#### 거래 데이터 생성 규칙

**정상 거래 (95%):**
- 500명 유저 풀에서 랜덤 선택
- 15개 가맹점 (국내 10 + 해외 5)
- 카테고리별 현실적 금액 범위:
  - CAFE: 3,000~15,000원
  - GROCERY: 10,000~200,000원
  - ONLINE: 5,000~300,000원
  - DEPARTMENT: 30,000~500,000원
  - LUXURY: 500,000~5,000,000원

**이상 거래 (5%, fraudRatio로 조절):**

| 유형 | 패턴 | 탐지 규칙 매칭 |
|------|------|----------------|
| HIGH_AMOUNT | 300만~1000만원 | HighAmountRule |
| RAPID_SUCCESSION | 같은 유저 연속 발생 | RapidSuccessionRule |
| FOREIGN_AFTER_DOMESTIC | 국내 직후 해외 결제 | GeoImpossibleTravelRule |
| MIDNIGHT | 새벽 + 50만~300만원 | MidnightTransactionRule |

---

