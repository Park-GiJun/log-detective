# 도메인 모델 타입 일관성

## 문제
같은 도메인 개념(fraudRatio)이 계층마다 다른 타입으로 선언되면 런타임 오류 위험.
- `GeneratorStartCommand.fraudRatio: Double` vs `Scenario.fraudRatio: Long`
- `GeneratorStartCommand.rate: Int` vs `Scenario.rate: Long`

## 규칙
- 도메인 모델의 타입을 먼저 확정하고, DTO/Entity/Request가 이를 따르도록 한다
- 비율(ratio)은 `Double`, 개수(count)는 `Int`, 식별자는 `Long`
