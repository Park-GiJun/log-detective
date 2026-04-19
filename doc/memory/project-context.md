# 프로젝트 컨텍스트 (2026-04-19 기준)

## 구현 현황
- **Phase 0 (스켈레톤)**: 완료
- **Phase 1 (수집 파이프라인)**: 진행 중
  - log-generator: 시나리오 CRUD + REST/Kafka/File 전송 + Web Controller 완성
  - log-ingest-service: IngestResult 도메인 모델 추가, 나머지 미구현
  - log-common: LogEvent, LogEventMessage, LogLevel, Severity 등 공유 모델

## 기술 부채
1. [HIGH] GeneratorQueryHandler → CommandHandler 직접 참조 (DIP 위반)
2. [HIGH] Scenario.fraudRatio: Long → Double 타입 수정 필요
3. [MEDIUM] CoroutineScope 생명주기 관리 부재
4. [MEDIUM] IngestSendFileAdapter 동시 쓰기 안전성
5. [MEDIUM] Scenario.successful 필드 의미 불명확

## 반복 실수 패턴
1. 도메인 모델 간 타입 불일치 (Long vs Int vs Double)
2. jakarta vs springframework 어노테이션 혼동

## 아키텍처 결정
- 각 서비스는 자체 도메인 모델 유지, common은 메시지 DTO 전용
- Handler는 @Bean 수동 등록, adapter는 @Component
- @Transactional은 handler에서 허용 (유일한 Spring 어노테이션 예외)
