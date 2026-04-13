현재 브랜치를 push하고, master 대상 PR을 생성하고, merge까지 자동화한다.

## 사용법
- `/ship` — PR 생성
- `/ship --merge` — PR 생성 + 즉시 squash merge
- `/ship --draft` — Draft PR 생성

---

## Step 1: 사전 검증
- master 브랜치면 중단
- uncommitted 변경 있으면 경고
- master 대비 커밋 수 확인

## Step 2: 빌드 검증
→ `.claude/skills/build.md` 참조

## Step 3: PR 메시지 생성
→ `.claude/agents/git-workflow.md`의 "오세린" 규칙 적용

## Step 4: Push + PR 생성
```bash
export PATH="$HOME/bin:$PATH"
git push -u origin {branch}
gh pr create --repo Park-GiJun/fds --base master --title "{제목}" --body "{본문}"
```

## Step 5: Merge (--merge 시)
→ `.claude/agents/git-workflow.md`의 "강민재" 체크리스트 적용
```bash
gh pr merge {번호} --repo Park-GiJun/fds --squash --delete-branch
git checkout master && git pull origin master
```
