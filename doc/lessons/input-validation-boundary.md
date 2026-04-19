# 입력 검증은 도메인 경계에서

## 원칙
외부 입력은 Command/Request 변환 시점에 검증. 도메인 모델은 이미 유효한 값만 받아야 한다.

## 적용 기준
- **Web Adapter**: Request DTO에서 필수값 검증, 길이 제한, 범위 검증
- **Command 변환**: enum 변환 실패를 적절한 예외(400)로 변환
- **도메인 모델**: 불변식(invariant)만 검증 (init block의 require)

## 안티 패턴
```kotlin
// BAD: enum 변환이 handler에서 발생 → 500 에러
LogLevel.valueOf(command.level.uppercase())

// GOOD: command 생성 시 검증 → 400 에러
val level = LogLevel.entries.find { it.name == rawLevel.uppercase() }
    ?: throw DomainValidationException("잘못된 로그 레벨: $rawLevel")
```

## 관련 리뷰
- 2026-04-19 b48306a — IngestEventCommand에서 level 검증 없이 handler까지 전달
