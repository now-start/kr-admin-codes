# kr-admin-codes — 행정동·법정동 코드 이력 데이터셋 + Java SDK

## Problem Statement

행정구역 개편(시도 코드 변경, 구 신설·분할)이 있을 때마다 주소·지역을 다루는 Java 백엔드 팀은 정부 게시판·포털에서 파일을 직접 받아 파싱하고, 옛 코드를 새 코드로 바꾸는 작업을 각자 반복한다. 반영이 늦거나 이름으로 매칭하면 잘못된 지역으로 집계·표시되는데, 이 일을 대신해 주는 Java용 정리 데이터·라이브러리는 없다.

## Evidence

- 작성자가 업무에서 직접 겪음 — 구체 사례 TBD (회사 내부 정보를 빼고 작성자가 채움)
- 최근 3년간 코드 자체가 바뀌는 개편이 반복됨: 강원특별자치도(2023-06, 시도 42→51), 군위군 대구 편입(2023-07, 47720→27720), 전북특별자치도(2024-01, 45→52), 2026년 화성시 행정구 신설·인천 자치구 재편·광주전남 통합(시도 29·46→12) — 모두 행안부 원천 파일에서 확인
- 원천이 흩어져 있고 기계가 읽기 불편함: 행정동은 행안부 게시판 첨부파일, 법정동은 공공데이터포털 API(1년마다 활용 연장 필요), 개편 자료는 주소기반산업지원서비스
- 이름 매칭 함정이 실제로 있음: 동명 법정동(은평구·강남구 신사동), 법정동 하나가 두 구로 분할(화성시 능동) — JeungHo/Kikcd README
- 기존 정리본이 방치됨: kr-juso/administrationCode는 "매일 업데이트" 표기지만 마지막 커밋이 2023-07-09. 수집 워크플로가 `disabled_inactivity`(60일 비활성 자동 중지) 상태이고, 게시글 제목 형식도 바뀌어 크롤러가 못 찾음
- 변경이 잦음: 행안부 변경 게시글이 2025-09~2026-11 사이 16건(월 1회 안팎)
- 수요 신호: 행정동 경계·코드 레포 vuski/admdongkor 별 546개 (Python/JS만 제공)

## Proposed Solution

행안부 10자리 행정동코드와 법정동코드, 둘 사이 매핑, 시점별 변경 이력을 행안부 `jscode(말소코드포함)` 파일 하나에서 가져와 한 스키마로 정리해 GitHub Pages에 버전별 정적 스냅샷으로 공개하고, 이를 내장한 의존성 0개 Java SDK를 Maven Central로 배포한다. SDK는 내장 스냅샷으로 즉시 동작하고 백그라운드로 갱신하며, 갱신이 실패해도 사용하는 서비스에 영향을 주지 않는다. 조회 API 서버 대신 정적 파일 + SDK를 고른 이유는 데이터가 작고(유효 행정동 약 3,900행, 전체 이력 포함 원본 zip 5.5MB) 월 1회 안팎으로만 바뀌며, 취미 프로젝트의 가용성에 남의 운영 서비스가 묶이지 않게 하기 위해서다.

## Key Hypothesis

We believe 시점별 이력과 법정동 매핑을 내장하고 스스로 갱신되는 Java SDK will 행정구역 개편 때마다 반복되는 수동 반영·코드 변환 작업을 없앨 것 for 주소·지역을 다루는 Java 백엔드 운영 서비스.
We'll know we're right when 공개 후 12개월 동안 공식 변경이 빠짐없이 반영되고, 작성자의 프로젝트가 수동 갱신 없이 개편을 넘긴다.

## What We're NOT Building

- 행정동 경계·지도(GeoJSON) — vuski/admdongkor가 이미 잘함
- 조회 API 서버 — GitHub Pages는 정적 전용이고 운영 부담이 생김. 검색·변환은 SDK가 메모리에서 처리
- 주소 문자열 → 코드 변환(주소 파싱) — 별개의 큰 문제, 도로명주소 API 영역
- 통계청 행정구역분류코드 매핑 — v2 후보
- Java 외 언어 SDK — JSON 파일을 직접 쓰면 됨
- SLA·유료 플랜 — 비영리. README에 "SLA 없음" 명시

## Success Metrics

성공 기준: 완성해서 공개하고, 1년 동안 끊기지 않고 갱신되는 것. 사용자 수 목표는 두지 않는다.

| Metric | Target | How Measured |
|--------|--------|--------------|
| v1 공개 | Maven Central 릴리스 + Pages 스냅샷 | 릴리스 존재 여부 |
| 갱신 연속성 | 공개 후 12개월간 누락 0건 | 행안부 변경 공지 목록과 `changes.json` 대조 |
| 반영 리드타임 | 행안부 게시 후 1일 내 배포 (시행일 전 게시분은 시행일 전) | 게시일 vs 배포 커밋일 |
| 정확도 | 공식 원천과 전수 대조 불일치 0건 | 파이프라인 검증 리포트 |

## Open Questions

- [x] 이력 시작 시점 → 원천에 1984~1988년부터 있음. 전체를 담고 정확도 보장은 2023-01-01 이후로 (잠정, 확정 필요)
- [x] 법정동↔행정동 매핑의 공식 원천 → 같은 zip의 `KIKmix`
- [x] 과거 스냅샷 확보 경로 → 불필요. 말소코드포함 파일 하나에 전체 이력이 있음
- [ ] **행정동 데이터 재배포 이용 조건**: 첨부에 공공누리 표시 없음 → 공개 전 행안부 문의 필요
- [ ] 1:N 분할 매핑 표현: 단순 목록 vs 비율·필지 수 같은 가중치
- [ ] 변경내역 hwpx 표 형식이 게시글마다 일정한지 (변환표 수동 보완의 근거)
- [x] 출장소 코드 포함 여부, 생성일자=말소일자 행(유효기간 0일) 처리 규칙 → 둘 다 원천 그대로 포함, 빈 구간은 어떤 날짜에도 유효하지 않음 (Phase 2)
- [ ] 커스텀 도메인 사용 여부 (`github.io`에서 옮기면 URL이 깨짐)
- [ ] 갱신 실패 알림 채널 (GitHub Issue 자동 생성 등)
- [ ] Maven Central에 이미 비슷한 라이브러리가 있는지 (검색 API 오류로 미확인)

---

## Users & Context

**Primary User**
- **Who**: 주소·지역 코드를 저장하거나 집계하는 Spring 등 Java 백엔드 운영 서비스 개발자
- **Current behavior**: 원천 파일을 받아 DB에 넣는 스크립트를 각자 만들고, 개편 소식을 듣고 수동으로 갱신·변환
- **Trigger**: 행정구역 개편 공지, 또는 옛 코드가 섞인 데이터가 조회·집계에서 어긋날 때
- **Success state**: 의존성 하나로 현재·과거 시점 코드 조회와 변환이 되고, 개편 때 손대지 않아도 시행일에 맞춰 바뀜

**Job to Be Done**
When 행정구역이 개편되거나 옛 코드가 섞인 데이터를 다룰 때, I want to 시점 기준으로 올바른 코드와 변환 결과를 바로 얻어서, so I can 수작업 반영 없이 조회·집계를 정확하게 유지한다.

**Non-Users**
- 지도·경계가 필요한 GIS·시각화 사용자 → admdongkor
- Python·R 분석가 → admdongkor 또는 JSON 직접 사용
- 주소 검색이 필요한 서비스 → 도로명주소 API

---

## Solution Detail

### Core Capabilities (MoSCoW)

| Priority | Capability | Rationale |
|----------|------------|-----------|
| Must | 행정동·법정동 현재 코드 조회 (코드·이름, 시도/시군구 계층) | 기본 기능 |
| Must | 시점 지정 조회 (`validFrom`/`validTo`) | 핵심 차별점, 미래 시행일 자동 전환 |
| Must | 옛 코드 → 새 코드 변환 (1:N 포함) | 개편 대응의 핵심 |
| Must | 법정동↔행정동 매핑 | 작성자가 정한 MVP 범위 |
| Must | 내장 스냅샷 + 백그라운드 갱신 + 실패 시 기존 데이터 유지 | 사용하는 서비스가 이 프로젝트 때문에 죽지 않게 |
| Should | jsDelivr 미러 폴백, sha256 검증 | 가용성·무결성 |
| Should | 데이터 신선도 노출 (`dataAge()`, 오래되면 경고 로그) | 방치 감지 |
| Could | 변경 이력 RSS / GitHub Release 알림 | SDK를 안 쓰는 사용자용 |
| Could | Spring Boot auto-configuration 모듈 | 편의 |
| Won't | 조회 API, 경계 데이터, 통계청 코드 | What We're NOT Building 참고 |

### MVP Scope

행정동·법정동 현재 코드 + 이력 시작 시점 이후 변경 이력 + 법정동↔행정동 매핑을 Pages에 공개하고, 이를 조회·변환하는 SDK 0.x를 Maven Central에 올린다. 수집은 매일 자동, 반영은 PR 리뷰 후 머지.

### User Flow

```java
// build.gradle: implementation("io.github.<아이디>:kr-admin-codes:0.1.0")
var codes = AdminCodes.builder().build();                    // 내장 스냅샷으로 즉시 사용
codes.adminDong("1111051500");                               // 오늘 기준 행정동
codes.adminDong("1111051500", LocalDate.of(2023, 1, 1));     // 그 시점 기준
codes.convert("4772000000", LocalDate.of(2023, 1, 1), LocalDate.now()); // 옛 코드 → 새 코드(목록)
codes.legalDongsOf("1111051500");                            // 행정동에 속한 법정동
```

백그라운드 갱신 기본 6시간, 실패해도 기존 스냅샷으로 계속 동작.

---

## Technical Approach

**Feasibility**: MEDIUM-HIGH — 배포·SDK는 단순(HIGH), 과거 이력 복원과 매핑 정확도가 불확실(MEDIUM)

**Architecture Notes**
- Gradle 멀티모듈: `model`(record), `pipeline`(Jsoup·Apache POI·Jackson), `sdk`(Java 17, 의존성 0)
- 생성 결과를 `data/`에 커밋 → 데이터 변경이 git diff로 남고 PR로 리뷰
- 워크플로: `collect.yml`(매일 수집, 변경 시 PR) / `pages.yml`(main 머지 시 Pages 배포) / `release.yml`(태그 시 Maven Central)
- 배포 경로: `/v1/meta.json`, `/v1/changes.json`, `/v1/snapshots/{version}/{dataset}.{json,tsv}` — 한 번 올린 버전은 덮어쓰지 않음
- GitHub Pages 제약: 커스텀 헤더 불가(Cache-Control 10분 고정). ETag·304·CORS `*`는 지원 → SDK 로컬 캐시로 해결
- 미러: `cdn.jsdelivr.net/gh/<아이디>/<repo>@<태그>/data/...`
- SDK: 불변 스냅샷을 `AtomicReference`로 통째 교체, 데몬 스레드 갱신, sha256 일치 시에만 교체, 갱신 실패 시 예외 없이 경고 로그
- 데이터 모델: 모든 레코드에 `validFrom`/`validTo` → 공지된 미래 변경을 미리 배포해 시행일에 자동 전환
- 1:N 분할처럼 사람 판단이 필요한 경우는 `overrides/`에 수동 기록하고 파이프라인이 병합

**Technical Risks**

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| 원천 형식·게시판 HTML 변경으로 수집 실패 | H | 제목 대신 첨부파일명 패턴으로 탐색, 검증 실패 시 배포 중단 + Issue 자동 생성, 항상 PR 리뷰 |
| 예약 워크플로가 60일 비활성으로 자동 중지 (kr-juso가 실제로 이렇게 멈춤) | H | 매일 확인 결과(`lastCheckedAt`)를 커밋해 활동 유지, 마지막 확인일이 오래되면 경고 |
| 소급 변경으로 과거 시점 조회 결과가 나중에 바뀜 (최근 16건 중 6건이 시행 후 게시) | H | 결과가 스냅샷 버전에 따라 달라질 수 있음을 문서화, `changes.json`에 게시일도 기록 |
| 옛→새 변환 자동 도출이 안 되는 경우 (2023년 이후 약 11%) | M | 변경내역 hwpx를 근거로 `overrides/` 수동 보완 |
| 1:N 분할·동명 법정동으로 잘못된 매핑 | H | 이름 매칭 금지(코드 기준), `overrides/` 수동 검수, 알려진 사례를 테스트로 고정 |
| 과거 스냅샷을 못 구해 이력 공백 | M | 이력 시작 시점 제한, 변경 공지 문서로 역산, 대조용 출처 확보 |
| 프로젝트 방치로 데이터가 낡음 | M | `meta.json`에 `lastCheckedAt`, SDK가 오래된 데이터 경고, README에 최종 갱신일 |
| 업무 경험에서 출발한 프로젝트 | M | 회사 코드·내부 데이터 사용 금지, 공개 원천만 사용, 겸업·IP 규정 확인 |
| 행정동 데이터 이용 조건 (첨부에 공공누리 표시 없음) | M | 공개 전 행안부 문의, 답을 받을 때까지 비공개 레포로 개발, 출처 표기 |
| Maven Central 등록(네임스페이스·GPG) | L | `io.github.<아이디>` 네임스페이스, `com.vanniktech.maven.publish` 플러그인 |

---

## Implementation Phases

<!--
  STATUS: pending | in-progress | complete
  PARALLEL: phases that can run concurrently (e.g., "with 3" or "-")
  DEPENDS: phases that must complete first (e.g., "1, 2" or "-")
  PRP: link to generated plan file once created
-->

| # | Phase | Description | Status | Parallel | Depends | PRP Plan |
|---|-------|-------------|--------|----------|---------|----------|
| 1 | 원천 조사 스파이크 | 원천별 형식·갱신 주기·과거 보관 범위·매핑 원천·이용 조건 확인, 샘플 파싱 | complete | - | - | [phase1-sources.md](../../../docs/research/phase1-sources.md) |
| 2 | 스키마 + 파이프라인 | `model` 정의, 수집→정규화→검증→`data/` 생성을 로컬에서 실행 | complete | - | 1 | [plan](../plans/completed/phase2-schema-pipeline.plan.md) · [report](../reports/phase2-schema-pipeline-report.md) |
| 3 | 배포 자동화 | collect/pages 워크플로, `meta.json`·`changes.json`, jsDelivr 미러 확인 | pending | with 4 | 2 | - |
| 4 | Java SDK | 내장 스냅샷, 조회·시점·변환·매핑, 갱신·폴백·검증 | pending | with 3 | 2 | - |
| 5 | 공개 | Maven Central 0.1.0, README·예제, 작성자 프로젝트에 실제 적용 | pending | - | 3, 4 | - |

### Phase Details

**Phase 1: 원천 조사 스파이크**
- **Goal**: Open Questions 중 데이터 원천 관련 항목을 답해 범위를 확정
- **Scope**: 원천 목록표(URL, 형식, 갱신 주기, 과거 보관 여부, 이용 조건), 법정동↔행정동 매핑 원천 확인, 샘플 파일 1개씩 파싱
- **Success signal**: 이력 시작 시점과 매핑 원천이 결정됨

**Phase 2: 스키마 + 파이프라인**
- **Goal**: 원천 → 정규화된 스냅샷을 재현 가능하게 생성
- **Scope**: record 스키마, 수집기(첨부파일명 패턴으로 탐색), cp949 고정폭 파서(바이트 기준), 정규화, 옛→새 변환표 자동 도출, 검증(스키마·건수 급변·알려진 사례), `overrides/` 병합
- **Success signal**: 로컬 실행으로 `data/`가 생성되고 강원·전북·군위 사례가 테스트를 통과

**Phase 3: 배포 자동화**
- **Goal**: 사람 손 없이 매일 수집하고, 머지하면 공개
- **Scope**: GitHub 미러 레포 생성과 푸시 미러 설정(공개 시점), `collect.yml`(변경 시 PR, 실패 시 Issue), `pages.yml`, `meta.json`·`changes.json`. 그 전까지 파이프라인은 로컬에서 실행
- **Success signal**: 원천 변경을 흉내 낸 커밋으로 PR → 머지 → Pages 반영까지 자동으로 돎

**Phase 4: Java SDK**
- **Goal**: 의존성 하나로 조회·변환이 되고, 갱신 실패에도 안 죽는 라이브러리
- **Scope**: TSV 내장 스냅샷, 공개 API, 갱신(ETag)·미러 폴백·sha256 검증, `dataAge()`
- **Success signal**: 네트워크 차단 상태에서도 동작, 갱신 중 동시 조회 테스트 통과

**Phase 5: 공개**
- **Goal**: 누구나 Maven Central에서 받아 쓸 수 있게
- **Scope**: 0.1.0 릴리스, README(SLA 없음, 출처, 사용 예), 작성자 프로젝트 적용
- **Success signal**: 깨끗한 새 프로젝트에서 의존성 추가만으로 예제가 동작

### Parallelism Notes

3과 4는 둘 다 Phase 2의 스키마에만 의존하고, 3은 `.github/`와 배포 경로를, 4는 `sdk/`를 건드려 파일이 겹치지 않으므로 동시에 진행할 수 있다.

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| 언어 | Java | Kotlin, Go | 작성자 주력, 1차 사용자와 같은 생태계 |
| 호스팅 | GitHub Pages | Cloudflare Pages | 레포와 한곳에서 관리, 무료. 헤더 제약은 SDK 캐시로 수용 |
| 런타임 제공 방식 | 정적 스냅샷 + SDK | 조회 API 서버 | 데이터가 작고 드물게 변함, 운영 부담·가용성 문제 회피 |
| SDK 배포 | Maven Central | GitHub Packages, JitPack | GitHub Packages는 공개 패키지도 받으려면 토큰 필요 |
| SDK 의존성 | 0개 (TSV 내장) | Jackson 사용 | 사용자 프로젝트와 버전 충돌 방지 |
| 반영 방식 | 매일 수집 → PR 리뷰 후 머지 | 자동 배포 | 변경이 드물고 1:N 판단이 필요 |
| 데이터 범위 | 행정동 + 법정동 + 매핑 | 행정동만, 현재 스냅샷만 | 작성자 결정 |
| 1차 사용자 | Java 백엔드 운영 서비스 | Java 배치·분석 | 런타임 갱신 설계의 가치가 가장 큼 |
| 주 원천 | 행안부 jscode(말소코드포함) zip | 공공데이터포털 API, 행정표준코드관리시스템 | 행정동·법정동·매핑·전체 이력이 한 파일에 있음. 나머지는 대조 검증용 |
| 저장소 | nowstart.org(Synology Git Server)에서 비공개 개발 → 공개 시 GitHub 미러 추가 | 처음부터 GitHub, Synology에서 직접 운영 | 이용 조건 답을 받기 전엔 비공개가 맞고, Actions·Pages·jsDelivr는 GitHub에서만 됨 |
| 이력 범위 | 전체 포함, 정확도 보장은 2023-01-01 이후 (잠정) | 2023년 이후만 담기 | 원천에 이미 있어 추가 비용이 없음. 오래된 데이터 품질은 미검증 |

---

## Research Summary

원천 조사 상세: [docs/research/phase1-sources.md](../../../docs/research/phase1-sources.md)

**Market Context**
- [vuski/admdongkor](https://github.com/vuski/admdongkor) (별 546, 활발): 행정동 경계 중심. 통계청 8자리 + 행안부 10자리 코드, 1975~2026 시점별, PyPI·npm 라이브러리, 검색 인덱스를 런타임에 레포에서 받아옴. Java 없음, "개발 초기 단계"
- [kr-juso](https://github.com/kr-juso) (juso.dev): 법정동 조회 API는 2026-10 Cloudflare Workers(Rust)로 이식되며 활발. 행정동 목록 TSV는 2023-07 이후 갱신 멈춤
- [bs-koo/areacode](https://github.com/bs-koo/areacode): 2023~2026 변경 이벤트를 사람이 웹에서 조회·변환하는 도구, 데이터는 JS에 내장
- [JeungHo/Kikcd](https://github.com/JeungHo/Kikcd): 개편 이력과 매칭 함정(동명 법정동, 1:N 분할) 정리
- Java: GitHub에서 해당 라이브러리 없음. Maven Central은 검색 API 오류로 미확인
- 공식 원천: 행안부 주민등록 게시판(행정동코드 변경내역), 공공데이터포털 행정안전부_행정표준코드_법정동코드 API, 주소기반산업지원서비스 행정구역 코드 변경안내, 행정표준코드관리시스템

**Technical Context**
- GitHub Pages: 사이트 1GB, 월 100GB 소프트 한도, 커스텀 헤더 불가(Cache-Control 10분), ETag·CORS `*` 지원
- 스냅샷 크기: 원본 zip이 전체 이력 포함 5.5MB. 정규화 후 크기는 Phase 2에서 측정 (SDK 내장 크기와 Pages 대역폭 판단용)
- jsDelivr가 GitHub 레포를 직접 서빙 → 무설정 미러

---

*Generated: 2026-11-27*
*Status: DRAFT - needs validation*
