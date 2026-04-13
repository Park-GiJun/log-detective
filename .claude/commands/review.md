커밋 단위 코드 리뷰를 수행한다. 6명 리뷰어 + 2명 리드가 팀을 구성한다.

## 사용법
- `/review` — 최신 커밋 리뷰
- `/review HEAD~3..HEAD` — 커밋 범위 지정

---

## Step 1: 커밋 분석
```bash
git log --oneline -5
git diff HEAD~1..HEAD --stat
git diff HEAD~1..HEAD
```

## Step 2: 6명 리뷰어 병렬 실행
→ `.claude/agents/reviewers.md` 참조

각 리뷰어에게 diff + 파일 컨텍스트를 전달하여 병렬 실행한다.
참조 문서: `doc/memory/project-context.md`, `doc/memory/review-checklist.md`, `doc/memory/domain-glossary.md`

## Step 3: 리드 종합 (순차)
→ `.claude/agents/reviewers.md`의 최민준(Tech Lead) + 한소율(Quality Lead) 참조

- 리뷰어 간 충돌/모순 확인
- 심각도 조정
- 이전 리뷰 대비 개선/퇴보 확인
- 반복 실수 패턴 감지
- Action Items + Tech Debt 확정

## Step 4: 리뷰 결과 기록
`doc/review/YYYY-MM-DD-{해시}.md`에 저장

## Step 5: 학습 내용 기록
`doc/lessons/{주제}.md`에 Quality Lead 학습 포인트 기록

## Step 6: project-context.md 업데이트
Tech Lead 분석을 반영하여 구현 현황, 기술 부채, 반복 실수 패턴 누적 업데이트

## Step 7: 리뷰 산출물 커밋
```bash
git add doc/review/ doc/lessons/ doc/memory/
git commit -m "docs: 리뷰 {해시} — 기술 {LEVEL} / 품질 {LEVEL}"
```

## Step 8: GitHub 이슈 등록
→ `.claude/skills/issue-management.md` 참조
- Action Items + 모든 심각도 Tech Debt(LOW 포함) → 이슈 등록

## Step 8.5: 이슈 브랜치 자동 생성
등록된 모든 이슈에 대해 브랜치 생성 + 코멘트

## Step 9: 요약 출력
```
═══════════════════════════════════════════
  FDS Code Review Report — {해시}
═══════════════════════════════════════════
  기술: {LEVEL}  |  품질: {LEVEL}
  Action Items: {N}건  |  Tech Debt: {N}건
  GitHub Issues: {N}건
═══════════════════════════════════════════
```
