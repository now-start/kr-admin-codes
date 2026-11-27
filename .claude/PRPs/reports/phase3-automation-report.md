# Implementation Report: Phase 3 — 배포 자동화

## Summary
GitHub Actions 워크플로 3개를 추가했다. 코드는 끝났지만 **레포 설정 두 가지와 main 머지가 끝나야 실제로 돈다**(아래 남은 일).

| 워크플로 | 트리거 | 하는 일 |
|---|---|---|
| `ci.yml` | PR, main 푸시 | `./gradlew build`(테스트·커버리지). 별도 잡으로 GitHub 러너에서 행안부 게시판 접속 확인(`check-source`, 실패해도 빌드는 통과) |
| `collect.yml` | 매일 18:00 UTC(03:00 KST), 수동 | `build --from-mois` → `meta.tsv`가 바뀌었으면 `data/<기준일>-<version12>` 브랜치로 PR(원천 정보·경고 로그·확인 목록 포함). 실패하면 이슈를 열거나 기존 이슈에 댓글. 매번 API로 예약 워크플로를 다시 켜서 60일 비활성 자동 중지를 막음 |
| `pages.yml` | main의 `data/v1/**` 변경, 수동 | `data/v1/*.tsv`를 바이트 그대로 Pages `/v1/`에 배포 + 파일 목록 `index.html` |

## Verified
- `build --from-mois`를 로컬에서 실제로 실행: 게시판에서 최신 게시글(2026-11-18, nttId 131063)을 찾고 쿠키·Referer로 zip을 받음. 받은 zip의 SHA-256이 Phase 1 샘플과 같아 데이터 파일은 그대로이고 `source.tsv`에 게시글 주소·제목·게시일이 채워짐
- 워크플로 YAML 문법 (Ruby YAML 파서). `actionlint`는 설치돼 있지 않아 GitHub 첫 실행으로 확인 예정

## Not Verified (머지·설정 후 확인)
- GitHub 러너(해외)에서 행안부 사이트 접속 — CI의 `mois-reachability` 잡이 첫 PR에서 확인한다. 막혀 있으면 self-hosted 러너나 국내 서버 cron이 필요하다
- 수집 PR 생성, 실패 이슈, Pages 배포, keepalive가 실제로 동작하는지
- keepalive: 워크플로를 API로 다시 켜면 60일 타이머가 초기화된다는 전제(liskin/gh-workflow-keepalive와 같은 방식). 60일이 지나야 확인 가능

## 남은 일 (레포 주인 계정 now-start 필요)
로컬 gh는 다른 계정이라 레포 설정 권한이 없다(403).
1. Settings → Pages → Build and deployment → Source: **GitHub Actions**
2. Settings → Actions → General → Workflow permissions → **Allow GitHub Actions to create and approve pull requests** 체크
3. `feat/phase3-automation` → `main` PR (Phase 2·4·3 커밋 포함), 머지

## Deviations
- Phase 3 계획 문서 없이 진행(범위가 워크플로 3개로 작음).
- PRD의 `changes.json`·RSS는 만들지 않음. 변경 이력은 `succession.tsv`가 이미 담고, GitHub Release 알림은 Phase 5에서 판단.
- `ci.yml`은 PRD에 없던 추가.
