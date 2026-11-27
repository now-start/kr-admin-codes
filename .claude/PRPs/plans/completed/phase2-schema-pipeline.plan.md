# Plan: Phase 2 — 스키마 + 파이프라인

## Summary
행안부 `jscodeYYYYMMDD(말소코드포함).zip`을 받아 행정동·법정동·매핑을 정규화하고, 옛→새 코드 변환표를 자동 도출·검증해 `data/v1/`에 결정적(deterministic) TSV로 쓰는 Java 파이프라인을 만든다. Gradle 멀티모듈(`model`, `pipeline`)의 첫 뼈대이기도 하다.

## User Story
As a 데이터셋 관리자(작성자), I want 명령 한 번으로 최신 원천을 받아 검증된 정규화 데이터를 만들고, so that 매달 바뀌는 행정구역 코드를 손으로 정리하지 않고 PR 리뷰만으로 반영할 수 있다.

## Problem → Solution
cp949 고정폭 텍스트 3개가 든 zip을 사람이 받아 열어봐야 함 → `./gradlew :pipeline:run --args="build ..."` 한 번으로 정렬된 UTF-8 TSV + 변환표 + 검증 리포트가 나오고, 이상이 있으면 0이 아닌 종료 코드로 멈춘다.

## Metadata
- **Complexity**: Large (빈 레포에서 시작, 약 40개 파일)
- **Source PRD**: `.claude/PRPs/prds/kr-admin-codes.prd.md`
- **PRD Phase**: 2 — 스키마 + 파이프라인
- **Estimated Files**: 약 40 (빌드 설정 8, main 22, test 12, 데이터·overrides 6)

---

## UX Design
N/A — 내부 도구. 사용 형태는 CLI 3개:

```
./gradlew :pipeline:run --args="check-source"                      # 최신 게시글·첨부만 확인 (zip 다운로드 없음)
./gradlew :pipeline:run --args="build --archive <zip경로>"          # 로컬 zip으로 data/v1 생성
./gradlew :pipeline:run --args="build --from-mois"                 # 최신 zip을 받아 data/v1 생성
```

---

## Mandatory Reading

| Priority | File | Why |
|---|---|---|
| P0 | `docs/research/phase1-sources.md` | 원천 구조, 행 수, 알려진 사례, 도출 시험 결과 |
| P0 | `.claude/PRPs/prds/kr-admin-codes.prd.md` | 범위, 리스크, 결정 사항 |

빈 레포라 따라 할 기존 코드가 없다. 아래 규칙이 이 레포의 첫 관례가 된다.

## External Facts (Phase 1에서 실측)

| 항목 | 값 |
|---|---|
| zip 엔트리 | `KIKcd_H.YYYYMMDD(말소코드포함)`, `KIKcd_B.…`, `KIKmix.…` + 각 `.xlsx` (xlsx는 무시) |
| 엔트리 이름 인코딩 | UTF-8 플래그 없음 → `new ZipInputStream(in, Charset.forName("MS949"))` |
| 본문 인코딩 | cp949(MS949), 줄바꿈 LF, 첫 줄은 헤더 |
| 고정폭 | **바이트 기준**. 한 줄 바이트 수: H·B 300, mix 164. 열 시작 위치 = 헤더 행에서 각 라벨의 바이트 위치 |
| 컬럼 | H: 행정동코드 시도명 시군구명 읍면동명 생성일자 말소일자 / B: 법정동코드 시도명 시군구명 읍면동명 동리명 생성일자 말소일자 / mix: 행정동코드 시도명 시군구명 읍면동명 법정동코드 동리명 생성일자 말소일자 |
| 날짜 | `yyyyMMdd`, 말소일자는 빈 값 가능 |
| 유효기간 의미 | `[생성일자, 말소일자)` 반열린 구간 (강원도 말소 20230611 = 강원특별자치도 생성 20230611) |
| 품질 이슈 | 생성=말소(0일) H 401 / B 9,041, 말소<생성 H 1 / B 2 / mix 2 → 에러가 아니라 경고 |
| 행 수 (20261111) | H 9,614 · B 53,407 · mix 58,967 |
| 게시판 목록 | `table.table_style1` 의 `tr`: 2번째 `td`의 `a[href*=commonSelectBoardArticle]`(제목, `nttId`), 5번째 `td`가 게시일 `2026.11.18.` |
| 게시글 첨부 | `ul li a[href*=FileDown.do]`, 텍스트 예: `jscode20261111(말소코드포함).zip  [ 5.2 MB ]` |
| href 주의 | `;jsessionid=...`가 붙음 → 제거. 기준 URL `https://www.mois.go.kr` |
| 다운로드 | 게시글 페이지를 먼저 열어 받은 쿠키 + `Referer: <게시글 URL>`로 요청하면 200 |

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| Gradle | 9.2.1 (로컬 설치됨, wrapper 생성) | 빌드 |
| JDK | model 17, pipeline 21 (로컬에 17·21·25 있음) | toolchain |
| jsoup | 1.24.1 | 게시판 HTML 파싱 |
| JUnit | 6.1.3 (BOM) | 테스트 |
| AssertJ | 3.27.7 | 단언 |
| foojay-resolver-convention | 1.0.0 | CI에서 toolchain 자동 설치 |

GOTCHA: Gradle 9는 `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`가 필요하다. `run` 태스크의 작업 디렉터리는 기본이 서브프로젝트라 `workingDir = rootProject.projectDir`로 바꿔야 `data/`, `overrides/` 상대 경로가 맞는다.

---

## Conventions (이 레포의 첫 관례)

- 패키지: `org.nowstart.kradmincodes.model`, `org.nowstart.kradmincodes.pipeline.{source,parse,derive,validate,write}`
- 불변: 도메인 타입은 전부 `record`, 컬렉션은 `List.copyOf`로 방어 복사. setter 없음.
- 검증: record의 compact constructor에서 형식 검증(코드 10자리 숫자, 필수 값 non-null). 데이터 품질 문제(0일 구간 등)는 생성자에서 막지 않고 `DatasetValidator`가 보고한다.
- 에러: 복구 불가 오류는 `PipelineException`(unchecked, 원인 포함). `PipelineApp.main`만 잡아서 로그 후 `System.exit(1)`. 삼키지 않는다.
- 로깅: 외부 의존성 없이 `System.Logger` (`System.getLogger(Xxx.class.getName())`).
- 날짜 출력: ISO `yyyy-MM-dd`, null은 빈 칸.
- 매직 넘버 금지: `GUARANTEED_FROM = 2023-01-01`, 타임아웃, 최대 크기 등은 상수.
- 함수 50줄, 파일 400줄 이하 유지.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `settings.gradle.kts` | CREATE | 모듈 등록, foojay 플러그인 |
| `build.gradle.kts` | CREATE | 루트 (공통 설정 최소) |
| `gradle/libs.versions.toml` | CREATE | 버전 카탈로그 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | CREATE | `gradle wrapper --gradle-version 9.2.1` |
| `.gitignore` | CREATE | `.gradle/`, `build/`, `.idea/`, `*.iml`, `work/`, `.claude/settings.local.json` |
| `model/build.gradle.kts` | CREATE | java-library, toolchain 17, jacoco |
| `model/src/main/java/.../model/Level.java` | CREATE | 코드 구조로 단위 판별 (SIDO/SIGUNGU/EUPMYEONDONG/RI) |
| `model/src/main/java/.../model/Validity.java` | CREATE | `[from, to)` 구간, `isValidOn`, `isEmpty` |
| `model/src/main/java/.../model/AdminDong.java` | CREATE | 행정동 record |
| `model/src/main/java/.../model/LegalDong.java` | CREATE | 법정동 record |
| `model/src/main/java/.../model/AdminLegalLink.java` | CREATE | 행정동↔법정동 매핑 record |
| `model/src/main/java/.../model/Succession.java` | CREATE | 옛→새 코드 record (+ `SuccessionMethod` enum: NAME_MATCH, MANUAL) |
| `model/src/main/java/.../model/Codes.java` | CREATE | 10자리 코드 검증 유틸 |
| `model/src/test/java/.../model/*Test.java` | CREATE | Level, Validity, record 검증 |
| `pipeline/build.gradle.kts` | CREATE | application, toolchain 21, jsoup, jacoco 80% |
| `pipeline/.../PipelineApp.java` | CREATE | CLI 진입점 (`check-source`, `build`) |
| `pipeline/.../PipelineException.java` | CREATE | 공통 예외 |
| `pipeline/.../Dataset.java` | CREATE | 정규화 결과 묶음 record |
| `pipeline/.../source/BoardPost.java`, `Attachment.java`, `SourceRelease.java` | CREATE | 게시글·첨부 값 객체 |
| `pipeline/.../source/MoisBoardParser.java` | CREATE | 목록·게시글 HTML → 값 객체 (순수 함수) |
| `pipeline/.../source/MoisBoardClient.java` | CREATE | 최신 릴리스 찾기, zip 다운로드 (네트워크) |
| `pipeline/.../source/HttpFetcher.java` | CREATE | HttpClient 래퍼 (쿠키, Referer, 타임아웃, 크기 제한) |
| `pipeline/.../parse/FixedWidthParser.java` | CREATE | 바이트 기준 고정폭 → `FixedWidthTable` |
| `pipeline/.../parse/FixedWidthTable.java` | CREATE | 헤더 + 행 (불변) |
| `pipeline/.../parse/JscodeArchiveReader.java` | CREATE | zip → H/B/mix 바이트 + 데이터 기준일 |
| `pipeline/.../parse/RecordMapper.java` | CREATE | 테이블 행 → model record |
| `pipeline/.../derive/SuccessionDeriver.java` | CREATE | 이름 매칭 자동 도출 + 미해결 목록 |
| `pipeline/.../derive/OverrideLoader.java` | CREATE | `overrides/successions.tsv` 읽기·병합 |
| `pipeline/.../validate/DatasetValidator.java` | CREATE | 형식·무결성·이력 보존·알려진 사례 검사 |
| `pipeline/.../validate/ValidationReport.java` | CREATE | errors / warnings |
| `pipeline/.../validate/KnownCases.java` | CREATE | Phase 1에서 확인한 사례 상수 |
| `pipeline/.../write/TsvWriter.java`, `TsvReader.java` | CREATE | 결정적 TSV 쓰기, 이전 데이터 읽기 |
| `pipeline/src/test/...` | CREATE | 각 컴포넌트 단위 테스트 + 합성 고정폭 fixture 빌더 |
| `pipeline/src/test/resources/mois/*.html` | CREATE | 실제 구조를 본뜬 **합성** HTML (실제 페이지 복사 금지) |
| `overrides/successions.tsv` | CREATE | 헤더만 (수동 보완은 후속 작업) |
| `data/v1/*.tsv`, `data/v1/source.properties` | CREATE | 첫 생성 결과 |

## NOT Building

- 미해결 변환 120건의 수동 보완 — 변경내역 hwpx를 읽는 데이터 작업이라 별도 작업으로 뺀다 (작업 목록 `unresolved-succession.tsv`만 만든다)
- JSON 출력, `meta.json`, `changes.json` — Phase 3 (배포 형식)
- GitHub Actions 워크플로 — Phase 3 (GitHub 미러 이후)
- SDK — Phase 4
- xlsx 파싱 (같은 내용의 텍스트가 있으므로 Apache POI 불필요)
- 변경내역 hwpx 파싱
- 재시도·백오프 (실패하면 명확히 실패하고 다음 날 다시 돈다)

---

## Output Format (`data/v1/`)

UTF-8, LF, 탭 구분, 첫 줄 헤더, 지정 키로 정렬. 값에 탭·개행이 있으면 에러.

| 파일 | 컬럼 | 정렬 키 |
|---|---|---|
| `admin-dong.tsv` | code, sido, sigungu, dong, valid_from, valid_to | code |
| `legal-dong.tsv` | code, sido, sigungu, eupmyeondong, ri, valid_from, valid_to | code |
| `admin-legal-link.tsv` | admin_code, legal_code, valid_from, valid_to | admin_code, legal_code, valid_from |
| `succession.tsv` | from_code, to_code, effective_date, method | from_code, effective_date, to_code |
| `unresolved-succession.tsv` | code, sido, sigungu, dong, abolished_on | abolished_on, code (`GUARANTEED_FROM` 이후만) |
| `source.properties` | data_date, post_url, post_title, posted_date, archive_sha256 | — |

단위(level)는 코드에서 계산되므로 저장하지 않는다.

---

## Step-by-Step Tasks

### Task 1: 빌드 뼈대
- **ACTION**: `settings.gradle.kts`(모듈 `model`, `pipeline`, foojay 1.0.0), 루트 `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `gradle wrapper --gradle-version 9.2.1`
- **GOTCHA**: 버전은 카탈로그에만. `run`의 `workingDir`을 루트로.
- **VALIDATE**: `./gradlew projects`에 `:model`, `:pipeline` 표시

### Task 2: model (TDD)
- **IMPLEMENT**:
  - `Codes.requireCode(String)`: 정확히 10자리 숫자가 아니면 `IllegalArgumentException`
  - `Level.of(code)`: `code[2..]=="00000000"` → SIDO, `code[5..]=="00000"` → SIGUNGU, `code[8..]=="00"` → EUPMYEONDONG, 그 외 RI
  - `Validity(LocalDate from, LocalDate toExclusive)`: from 필수, to nullable. `isValidOn(d) = !d.isBefore(from) && (to == null || d.isBefore(to))`, `isEmpty() = to != null && !to.isAfter(from)` (0일·역전 구간 모두 비어 있음)
  - `AdminDong(code, sido, sigungu, dong, Validity)`, `LegalDong(code, sido, sigungu, eupmyeondong, ri, Validity)`, `AdminLegalLink(adminCode, legalCode, Validity)`, `Succession(fromCode, toCode, effectiveDate, method)` — 이름은 빈 문자열 허용, null 불가
- **VALIDATE**: `./gradlew :model:test`

### Task 3: 고정폭 파서 (TDD)
- **IMPLEMENT**: `FixedWidthParser.parse(byte[] content, Charset)` — 첫 줄을 바이트 단위로 스캔해 공백이 아닌 구간(라벨)의 시작 바이트 위치를 열 경계로 삼는다. 각 행을 `[start_i, start_{i+1})` 바이트로 자르고(마지막 열은 줄 끝까지) 디코드 후 trim. 빈 줄은 건너뛴다. 행 길이가 헤더보다 짧으면 남은 열은 빈 값.
- **GOTCHA**: 문자 단위로 자르면 한글(2바이트) 때문에 열이 밀린다. 반드시 바이트로 자른 뒤 디코드.
- **TEST FIXTURE**: 테스트 헬퍼가 cp949로 한글 이름을 넣은 고정폭 줄을 직접 만든다(실제 원천 행을 복사하지 않음).
- **VALIDATE**: 한글 열 뒤의 날짜가 정확히 잘리는지, LF/빈 줄/짧은 행

### Task 4: zip 읽기 + 레코드 변환 (TDD)
- **IMPLEMENT**: `JscodeArchiveReader.read(byte[] zip)` → `JscodeArchive(LocalDate dataDate, byte[] admin, byte[] legal, byte[] link)`. 엔트리 이름이 `KIKcd_H.`/`KIKcd_B.`/`KIKmix.`로 시작하고 `.xlsx`로 끝나지 않는 것. 날짜는 이름의 8자리. 하나라도 없으면 `PipelineException`. 엔트리 크기 상한(100MB) 초과 시 예외(zip bomb 방지). `RecordMapper`가 헤더 라벨로 열을 찾아 record로 변환 (라벨이 없으면 예외).
- **VALIDATE**: 테스트에서 MS949 이름의 zip을 만들어 읽기

### Task 5: 변환표 도출 + overrides (TDD)
- **IMPLEMENT**: `SuccessionDeriver.derive(List<AdminDong>, LocalDate guaranteedFrom)`:
  - 대상: `validTo != null`이고 구간이 비어 있지 않은 행
  - 후보: `validFrom == 대상.validTo`, 같은 `Level`(코드 기준), 비어 있지 않은 구간. SIDO는 그날 생긴 SIDO 전부, 그 외는 `sigungu`·`dong` 이름이 모두 같은 행
  - 후보 1개 → `Succession(NAME_MATCH)`, 0개·2개 이상 → 미해결 (`guaranteedFrom` 이후 말소분만 보고)
  - `OverrideLoader`: `from_code, to_code, effective_date, note` TSV. `to_code`가 비면 "후계 없음 확인"으로 미해결에서만 제거. override가 같은 from_code의 자동 결과를 대체.
- **GOTCHA**: 2026-07-01 광주·전남 → 통합특별시처럼 N:1은 각각 후보 1개라 정상 도출된다. 출장소 행(예: 4210500000)은 코드상 SIGUNGU라 SIDO 후보에서 자동으로 빠진다.
- **VALIDATE**: 강원 4200000000→5100000000, 군위 4772000000→2772000000, 동 단위 이름 변경 미해결, override 적용

### Task 6: 검증 (TDD)
- **IMPLEMENT**: `DatasetValidator.validate(Dataset current, Optional<Dataset> previous)` → `ValidationReport`
  - error: 매핑의 코드가 H/B에 없음(유효 구간이 `GUARANTEED_FROM` 이후와 겹치는 링크만), 이전 데이터에 있던 코드가 사라짐, 전체 행 수 감소, `KnownCases` 불일치
  - warning: 빈 구간 행 수, 미해결 변환 수, 유효 행정동 수가 이전 대비 10% 넘게 변함, 오래된 링크 무결성 문제
- **VALIDATE**: 각 규칙별 실패·통과 케이스

### Task 7: TSV 쓰기·읽기 (TDD)
- **IMPLEMENT**: `TsvWriter`가 위 Output Format대로 정렬·직렬화, 임시 파일에 쓴 뒤 `Files.move(ATOMIC_MOVE)`. `TsvReader`가 이전 `data/v1`을 `Dataset`으로 읽음(없으면 `Optional.empty()`).
- **VALIDATE**: 쓰기→읽기 왕복이 같음, 정렬 결정성, 탭 포함 값 에러

### Task 8: 원천 수집 (파서는 TDD, 네트워크는 수동 확인)
- **IMPLEMENT**: `MoisBoardParser.parseList(html)` → `List<BoardPost>`, `parseAttachments(html)` → `List<Attachment>`(정규식 `jscode(\d{8})\(말소코드포함\)\.zip`). `MoisBoardClient.findLatestRelease()`: 목록 1페이지의 게시글을 최신순으로 최대 10개 열어 첫 매칭을 반환. `download(release)`: 게시글을 먼저 열어 쿠키를 받고 Referer를 붙여 zip 요청, 최대 50MB. `HttpFetcher`는 `HttpClient`(CookieManager, connect 10s, request 60s, User-Agent `kr-admin-codes-pipeline`).
- **GOTCHA**: 제목으로 찾지 않는다(kr-juso가 제목 형식 변경으로 멈춤). 200이 아니거나 zip 시그니처(`PK`)가 아니면 예외.
- **VALIDATE**: 합성 HTML fixture로 파서 테스트, `check-source`를 실제로 한 번 실행 (HTML만 읽음)

### Task 9: CLI + 첫 데이터 생성
- **IMPLEMENT**: `PipelineApp` — `check-source`, `build --archive <path> | --from-mois [--out data/v1] [--overrides overrides/successions.tsv]`. 흐름: 원천 → 파싱 → 변환표 → 이전 데이터 읽기 → 검증 → 에러면 종료 코드 1(파일 안 씀) → 쓰기 → 요약 로그(행 수, 경고 수).
- **VALIDATE**: Phase 1에서 받은 zip으로 `build --archive` 실행 → `data/v1` 생성, 두 번째 실행 시 diff 없음(결정성)

---

## Testing Strategy

| Test | Input | Expected |
|---|---|---|
| Level 판별 | 1100000000 / 1111000000 / 1111051500 / 1111010100 / 1111025021 | SIDO / SIGUNGU / EUPMYEONDONG / EUPMYEONDONG / RI |
| Validity | from=to, to<from, to=null | 비어 있음, 비어 있음, 열린 구간 |
| 고정폭 한글 | 이름 열 다음 날짜 | 날짜가 정확히 잘림 |
| 짧은 행 | 헤더보다 짧은 줄 | 남은 열 빈 값 |
| zip 엔트리 누락 | mix 없음 | PipelineException |
| 변환 도출 | 강원·군위·통합특별시 합성 데이터 | NAME_MATCH 1건씩 |
| 미해결 | 동 이름 변경 | 미해결 목록 |
| override | 미해결 + override | MANUAL |
| 이력 보존 | 이전에 있던 코드가 없음 | error |
| TSV 왕복 | Dataset | 동일 |
| 게시판 파서 | 합성 목록·첨부 HTML | nttId·날짜·jsessionid 제거·dataDate |

### Edge Cases Checklist
- [x] 빈 입력 (헤더만 있는 파일)
- [x] 0일·역전 구간
- [x] zip 엔트리 누락·과대 크기
- [x] 첨부 없는 게시글만 있는 목록
- [ ] 동시성 — 해당 없음 (단일 실행)
- [x] 네트워크 실패 — 예외로 종료 코드 1

---

## Validation Commands

```bash
./gradlew build
```
EXPECT: 컴파일, 테스트, jacoco 80% 검증 통과

```bash
./gradlew :pipeline:run --args="check-source"
```
EXPECT: 최신 게시글 제목·게시일·zip 파일명 출력

```bash
./gradlew :pipeline:run --args="build --archive <Phase 1 zip>"
git status --short data/
./gradlew :pipeline:run --args="build --archive <Phase 1 zip>"
git diff --stat data/
```
EXPECT: 첫 실행에 `data/v1` 생성, 두 번째 실행 후 diff 없음, 행 수가 Phase 1 실측(H 9,614 · B 53,407 · mix 58,967)과 일치

## Acceptance Criteria
- [ ] 모든 Task 완료, `./gradlew build` 통과
- [ ] 커버리지 80% 이상 (네트워크 클래스 `HttpFetcher`, `MoisBoardClient`, `PipelineApp` 제외)
- [ ] `data/v1` 생성, 행 수가 실측과 일치, 재실행 결정적
- [ ] 강원·전북·군위·광주전남 사례가 KnownCases 검증 통과
- [ ] 미해결 변환 목록이 `unresolved-succession.tsv`로 나옴

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 데이터 파일이 커서 git diff가 무거움 (B·mix 각 수 MB) | M | L | 정렬 고정으로 월별 diff는 작게 유지, 크기 측정 후 Phase 3에서 재판단 |
| 게시판 HTML 구조 변경 | M | M | 파서를 순수 함수로 분리해 fixture만 바꿔 고치기 쉽게, 실패 시 명확한 예외 |
| 합성 fixture가 실제와 어긋남 | M | M | `check-source` 실제 실행으로 교차 확인 |
| 행정동 데이터 이용 조건 미확정 | M | H | 레포 비공개 유지 (Phase 5 전 행안부 문의) |

## Notes
- 결정(기본값으로 진행, 바꾸려면 알려주세요): 출장소 행은 원천 그대로 포함 / 0일·역전 구간은 포함하되 어떤 날짜에도 유효하지 않음 / 1:N 변환은 목록으로만 표현(가중치 없음) / 정확도 보장 범위 2023-01-01 이후 / 패키지 `org.nowstart.kradmincodes`
- 실제 원천 파일·페이지는 테스트 리소스에 넣지 않는다 (이용 조건 미확정).
