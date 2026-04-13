테스트 브랜치 생성 → 테스트 코드 작성 → 실행 → 커밋 → PR → merge 자동화.

## 사용법
- `/test` — 최근 커밋 변경 파일 기반
- `/test {파일경로}` — 특정 파일 대상
- `/test --module {모듈명}` — 모듈 전체
- `/test --setup` — 테스트 인프라 세팅만

---

## Step 1: 테스트 브랜치 생성
```bash
git checkout master && git pull origin master
git checkout -b "test/{대상slug}-tests"
```

## Step 2: 테스트 인프라 확인
- 전 모듈: Kotest 6, MockK, kotlin-test
- Spring 모듈: spring-boot-test, coroutines-test
- 각 모듈별: Testcontainers, Ktor Mock 등
- `application-test.yml` 없으면 자동 생성 (spring.cloud.config.enabled: false 포함)

## Step 3: 대상 분석
```bash
git diff HEAD~1..HEAD --name-only -- '*.kt' | grep -v '/test/'
```

## Step 4: 전략 수립 + 코드 작성
→ `.claude/agents/test-team.md` 참조
- 김태현: 전략 수립 (계층별 테스트 유형 결정)
- 이수빈: 단위 테스트 작성 (JUnit5 @Test + Kotest Assertions)
- 박준영: 통합 테스트 (Testcontainers — infrastructure 계층만)

## Step 5: 빌드 + 테스트 실행
→ `.claude/skills/build.md` 참조. 실패 시 최대 3회 자동 수정.

## Step 6: 커밋 + Push + PR + Merge
프로덕션 코드 변경 없으면 자동 squash merge.

## Step 7: 출력
```
═══════════════════════════════════════════
  Test Pipeline Complete ✓
═══════════════════════════════════════════
  총 테스트: {N}개 (통과 {n})
  PR: #{number}  master: {hash}
═══════════════════════════════════════════
```
