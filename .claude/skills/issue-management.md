# 이슈 관리

## 이슈 생성
```bash
export PATH="$HOME/bin:$PATH"
gh issue create --repo Park-GiJun/fds \
  --title "[{카테고리}] {이슈 제목}" \
  --label "{카테고리},{심각도},{review-action|tech-debt}" \
  --milestone "Phase 1: 기반 구축" \
  --body "## 문제\n{설명}\n\n## 출처\n리뷰 {날짜} — {리뷰어}\n\n## 관련 파일\n- {경로}"
```

## 중복 방지
```bash
existing=$(gh issue list --repo Park-GiJun/fds --search "{제목}" --state open --json number --jq '.[0].number' 2>/dev/null)
if [ -z "$existing" ]; then gh issue create ...; else echo "스킵: #$existing"; fi
```

## 라벨 매핑
| 카테고리 | 라벨 | 심각도 라벨 |
|----------|------|-----------|
| Architect | architecture | severity: critical/high/medium/low |
| Security | security | |
| Performance | performance | |
| Code Quality | code-quality | |
| Testing | testing | |
| Domain | domain | |

## 이슈 등록 후 브랜치 자동 생성
모든 심각도(LOW/INFO 포함)에 대해 브랜치를 생성한다.

## 브랜치 prefix 규칙
| 라벨 | prefix | 우선순위 |
|------|--------|---------|
| security | security/ | 1 |
| architecture | refactor/ | 2 |
| performance | perf/ | 3 |
| domain | feat/ | 4 |
| testing | test/ | 5 |
| code-quality | refactor/ | 6 |
| tech-debt | chore/ | 7 |

브랜치명: `{prefix}/issue-{번호}-{slug}`
