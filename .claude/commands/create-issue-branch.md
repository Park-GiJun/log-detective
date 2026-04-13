이슈 번호(들)를 받아서 적절한 브랜치를 생성하고 체크아웃한다.

## 사용법
- `/create-issue-branch 1` — 단일 이슈
- `/create-issue-branch 7 8 9` — 여러 이슈 묶음
- `/create-issue-branch all` — 열린 이슈 전체 일괄 생성

---

## 브랜치 규칙
→ `.claude/skills/issue-management.md` 참조
- prefix: security > architecture > performance > domain > testing > code-quality > tech-debt
- 형식: `{prefix}/issue-{번호}-{slug}`

## all 모드 의존 관계
1. severity: critical 먼저
2. 보안 → 아키텍처 → 성능 → 품질 → 테스트 → 기술부채
3. 테스트 이슈는 코드 변경 이슈 이후

## 실행
```bash
git fetch origin && git checkout master && git pull origin master
git checkout -b {브랜치명}
gh issue comment {번호} --repo Park-GiJun/fds --body "🔀 브랜치 생성: \`{브랜치명}\`"
```
