변경 사항을 분석하여 커밋 메시지를 자동 생성하고 커밋한다.

## 사용법
- `/commit` — 전체 변경사항 커밋
- `/commit --amend` — 마지막 커밋 수정

---

## Step 1: 변경 사항 분석
```bash
git status -s
git diff --cached --stat
git diff --stat
```
staged 파일이 없으면 코드 파일을 자동 stage. `.env`, `credentials` 등 민감 파일은 절대 stage하지 않는다.

## Step 2: 문서 자동 업데이트
변경된 파일을 분석하여 문서 업데이트가 필요한지 판단한다.

### 2-1. README.md 업데이트 판단
아래 중 하나라도 해당하면 README.md를 업데이트한다:
- 새 모듈/서비스 추가 또는 삭제
- API 엔드포인트 추가/변경 (Controller 파일 변경)
- 기술 스택 변경 (build.gradle.kts, Dependencies.kt, Versions.kt)
- 인프라 구성 변경 (docker-compose, application.yml)
- 패키지 구조 변경 (새 adapter/port/handler 패키지)

스킵 조건: 테스트만, 내부 리팩터링, 버그 수정

### 2-2. doc/ 폴더 업데이트 판단
변경 내용에 따라 해당 문서를 업데이트한다:

| 변경 유형 | 대상 문서 |
|----------|----------|
| 도메인 모델 추가/변경 | `doc/design/design.md` (데이터 모델 섹션) |
| API 엔드포인트 추가/변경 | `doc/schemas/service-design.md` (API 스펙) |
| 탐지 규칙 변경 | `doc/design/design.md` (탐지 규칙 섹션) |
| 서비스 간 통신 변경 | `doc/schemas/service-design.md` |
| DB 스키마 변경 (Entity) | `doc/design/design.md` (스키마 섹션) |

스킵 조건: 기존 로직의 단순 버그 수정, 테스트만 변경

### 2-3. CLAUDE.md 업데이트 판단
아래 중 하나라도 해당하면 CLAUDE.md를 업데이트한다:
- 코딩 규칙/컨벤션 변경
- `.claude/commands/` 또는 `.claude/agents/` 파일 변경
- `doc/` 하위 구조 변경
- 커스텀 커맨드 추가/수정

### 2-4. 문서 업데이트 규칙
- 변경이 필요한 섹션만 수정 (전체 재작성 금지)
- 코드에 실제로 존재하는 내용만 반영 (설계 문서가 아닌 현행 문서)
- 문서 변경분도 같은 커밋에 포함하여 stage

## Step 3: 빌드 검증
→ `.claude/skills/build.md` 참조

## Step 4: 커밋 메시지 생성
→ `.claude/agents/git-workflow.md`의 "송준호" 규칙 적용
- 제목: `{타입}: {한국어 요약}` (최대 72자)
- 본문: "왜" 중심, `Closes #번호` / `Refs #번호`
- 브랜치명에서 이슈 번호 자동 추출
- 문서도 함께 변경된 경우 본문에 `docs: {변경 요약}` 한 줄 추가

## Step 5: 커밋 실행
```bash
git commit -m "{메시지}"
```

## Step 6: 출력
```
═══════════════════════════════════════════
  Commit Created
═══════════════════════════════════════════
  해시: {hash}  브랜치: {branch}  메시지: {제목}
  변경: {N} files, +{add} -{del}
  이슈: #{번호}
  문서: {업데이트된 문서 목록 또는 "변경 없음"}
═══════════════════════════════════════════
```
