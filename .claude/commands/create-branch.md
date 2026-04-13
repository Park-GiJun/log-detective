현재 진행 상황을 분석하여 적절한 prefix와 이름으로 브랜치를 생성하고 체크아웃한다.

## 사용법
- `/create-branch` — 현재 작업 내용 기반으로 자동 추천
- `/create-branch {설명}` — 설명을 기반으로 브랜치 생성 (예: `/create-branch transaction kafka consumer 구현`)

---

## Step 1: 현재 상황 분석
```bash
git status -s
git diff --stat
git log --oneline -5
```
- 변경된 파일의 패턴을 분석하여 작업 내용을 파악
- 열린 이슈가 있으면 관련 이슈 확인

## Step 2: prefix 결정
변경 내용에 따라 자동 분류:

| 변경 패턴 | prefix |
|-----------|--------|
| 신규 기능 구현 (도메인/API/비즈니스) | feat/ |
| 버그 수정, 기존 동작 변경 | fix/ |
| 구조 변경, 패키지 이동, 리네이밍 | refactor/ |
| 성능 개선 | perf/ |
| 테스트 추가/수정 | test/ |
| 보안 관련 | security/ |
| 빌드, 설정, 인프라 | chore/ |
| 문서 | docs/ |

## Step 3: 브랜치명 생성
형식: `{prefix}/{slug}`

slug 규칙:
- 핵심 키워드 3~5단어
- 한국어 → 영문 축약
- kebab-case, 소문자

예시:
- `feat/transaction-kafka-consumer`
- `fix/generator-startup-error`
- `refactor/detection-rule-engine`
- `chore/hikaricp-pool-config`

## Step 4: 브랜치 생성 + 체크아웃
```bash
git fetch origin master
git checkout -b {브랜치명} master
```

작업 중인 변경사항이 있으면:
- 변경사항을 새 브랜치에 포함
- `git stash` 불필요 (checkout -b가 working tree를 유지)

## Step 5: 출력
```
═══════════════════════════════════════════
  Branch Created
═══════════════════════════════════════════
  브랜치: {브랜치명}
  Base: master ({hash})
  작업 내용: {분석된 작업 설명}
  다음 단계: 코딩 → /commit → /ship
═══════════════════════════════════════════
```
