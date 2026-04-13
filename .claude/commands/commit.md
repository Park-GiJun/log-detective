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

## Step 1.5: README 업데이트 확인
변경 파일을 분석하여 README.md에 반영할 내용이 있는지 확인한다.
- 새 모듈/기능, 구조 변경, API/설정 변경 시 → README 업데이트
- 테스트만, 문서만, 내부 리팩터링 → 스킵

## Step 2: 빌드 검증
→ `.claude/skills/build.md` 참조

## Step 3: 커밋 메시지 생성
→ `.claude/agents/git-workflow.md`의 "송준호" 규칙 적용
- 제목: `{타입}: {한국어 요약}` (최대 72자)
- 본문: "왜" 중심, `Closes #번호` / `Refs #번호`
- 브랜치명에서 이슈 번호 자동 추출

## Step 4: 커밋 실행
```bash
git commit -m "{메시지}"
```

## Step 5: 출력
```
═══════════════════════════════════════════
  Commit Created
═══════════════════════════════════════════
  해시: {hash}  브랜치: {branch}  메시지: {제목}
  변경: {N} files, +{add} -{del}
  이슈: #{번호}
═══════════════════════════════════════════
```
