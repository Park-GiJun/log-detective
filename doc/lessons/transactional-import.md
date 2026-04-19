# @Transactional import 주의

## 문제
`jakarta.transaction.Transactional`과 `org.springframework.transaction.annotation.Transactional`은 다르다.
Spring AOP 프록시는 `org.springframework.transaction.annotation.Transactional`만 인식한다.
`jakarta.transaction.Transactional`을 사용하면 트랜잭션이 적용되지 않을 수 있다.

## 규칙
- handler에서 `@Transactional` 사용 시 반드시 `org.springframework.transaction.annotation.Transactional` import
- IDE 자동 import 시 `jakarta.*` 버전이 선택될 수 있으므로 주의
