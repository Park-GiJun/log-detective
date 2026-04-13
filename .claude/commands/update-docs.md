프로젝트 문서(README.md, CLAUDE.md)를 현재 코드 상태에 맞게 업데이트한다.

## 사용법
- `/update-docs` — README.md + CLAUDE.md 전체 검토 및 업데이트
- `/update-docs readme` — README.md만
- `/update-docs claude` — CLAUDE.md만

---

## Step 1: 현재 코드 상태 파악
```bash
find . -name "*.kt" -path "*/src/main/*" -not -path "*/build/*" | sort
git log --oneline -10
```

## Step 2: README.md 업데이트
현재 README를 읽고 다음 섹션을 코드 상태와 비교하여 업데이트:

| 섹션 | 검증 대상 |
|------|----------|
| 기술 스택 | build.gradle.kts, Versions.kt, Dependencies.kt |
| 시스템 아키텍처 | 모듈 구조, 서비스 포트 |
| 패키지 구조 | 실제 패키지 (inbound/outbound/handler) |
| 서비스별 설계 | 도메인 모델, API 스펙, DB 스키마 |
| 인프라 | docker-compose.yml, application.yml, 기동 순서 |
| 구현 순서 | 현재 Phase 진행 상황 |

**규칙:**
- 변경이 필요한 섹션만 수정 (전체 재작성 금지)
- 코드에 없는 내용은 추가하지 않음 (설계 문서가 아닌 현행 문서)
- 마크다운 형식 유지

## Step 3: CLAUDE.md 업데이트
현재 CLAUDE.md를 읽고 다음을 검증:

| 항목 | 검증 대상 |
|------|----------|
| 코딩 규칙 | 실제 적용 중인 컨벤션과 일치하는지 |
| 커스텀 커맨드 | .claude/commands/ 파일들과 동기화 |
| 리뷰 팀 구성 | .claude/agents/ 파일들과 동기화 |
| 문서 구조 | doc/ 하위 실제 파일 구조와 일치하는지 |

## Step 4: 커밋
변경이 있으면 커밋한다:
```bash
git add README.md CLAUDE.md
git commit -m "docs: README/CLAUDE.md 현행화 — {변경 요약}"
```

## Step 5: 출력
```
═══════════════════════════════════════════
  Documentation Updated
═══════════════════════════════════════════
  README.md: {변경된 섹션 목록 또는 "변경 없음"}
  CLAUDE.md: {변경된 섹션 목록 또는 "변경 없음"}
═══════════════════════════════════════════
```
