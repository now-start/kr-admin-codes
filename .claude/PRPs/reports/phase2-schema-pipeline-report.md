# Implementation Report: Phase 2 — 스키마 + 파이프라인

## Summary
Gradle 멀티모듈(`model` Java 17, `pipeline` Java 21)을 만들고, 행안부 `jscode(말소코드포함).zip`을 읽어 행정동·법정동·매핑·옛→새 코드 변환표를 검증한 뒤 `data/v1/`에 결정적 TSV로 쓰는 CLI를 구현했다. 실제 원천(2026-11-11자)으로 첫 데이터를 생성했다.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files | 약 40 | main Java 36, test Java 14, 빌드·설정 9, 데이터 6 + overrides 1 |
| Lines | — | main 약 1,860, test 약 1,140 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | 빌드 뼈대 | Complete | 루트 `build.gradle.kts` 대신 `gradle.properties`로 group·version 지정 |
| 2 | model | Complete | |
| 3 | 고정폭 파서 | Complete | |
| 4 | zip 읽기 + 레코드 변환 | Complete | `JscodeArchive`가 바이트 대신 파싱된 테이블을 들고 있음(불변) |
| 5 | 변환표 도출 + overrides | Complete | **설계 변경** — 아래 Deviations |
| 6 | 검증 | Complete | 출장소 자기 참조 매핑을 경고로 분리 (실제 원천에서 발견) |
| 7 | TSV 쓰기·읽기 | Complete | `source.properties` 대신 `source.tsv` |
| 8 | 원천 수집 | Complete | fixture는 리소스 파일 대신 테스트 안의 합성 HTML |
| 9 | CLI + 첫 데이터 생성 | Complete | `build --from-mois`(zip 다운로드 경로)는 실행하지 않음 |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `-Xlint:all -Werror` (컴파일 경고 0) |
| Unit Tests | Pass | 77개 (model 23, pipeline 54) — 코드 리뷰 반영 후 |
| Coverage | Pass | model 라인 100%·분기 97.1%, pipeline 라인 95.1%·분기 89.8% (네트워크·CLI 3개 클래스 제외) |
| Build | Pass | `./gradlew build` |
| Integration | Pass (부분) | 실제 zip으로 `build --archive` 실행, `check-source` 실제 게시판 조회 성공. `build --from-mois`는 미실행 |
| Determinism | Pass | 같은 zip으로 두 번 실행 → 6개 파일 SHA-256 동일 |

실제 원천 실행 결과: 행정동 9,614 · 법정동 53,407 · 매핑 58,967 (Phase 1 실측과 일치), 변환 1,019 (수동 1), 미해결 122, 알려진 사례 9건·변환 5건 검증 통과.

## Deviations from Plan

1. **변환 도출 규칙 강화**: 계획(그리고 Phase 1 시험)은 시군구명·읍면동명만 비교했다. 그러면 2026-07-01 인천 동구가 같은 날 생긴 전남광주통합특별시 동구로 잘못 이어진다. 상위 단위 코드가 같거나 상위 단위가 그날 이어진 경우에만 잇도록 바꾸고, 상위부터(시도 → 시군구 → 읍면동) 차례로 풀게 했다. 그 결과 시도를 옮긴 군위군은 자동으로 잇지 않으므로, `overrides/successions.tsv`에 시군구 한 줄을 넣었고 그 아래 읍면동은 자동으로 따라온다.
2. **출장소 자기 참조 매핑**: 실제 원천에서 2023년 이후 유효한 매핑 9건이 법정동 파일에 없는 코드를 가리켜 검증이 멈췄다. 전부 출장소가 자기 코드를 법정동 코드로 가리키는 원천 특성이라, 이 형태만 경고로 분리했다(테스트 추가).
3. 게시일 파싱 버그를 테스트에서 발견해 고쳤다: 제목의 "(2026.10.23. 시행)"이 게시일로 잡혔다. 칸 전체가 날짜인 경우만 게시일로 본다.

## Code Review (독립 리뷰 반영)

java-reviewer 서브에이전트의 지적 7건(CRITICAL 0, HIGH 2, MEDIUM 5)을 검토했다.

| # | 지적 | 조치 |
|---|---|---|
| 1 | overrides 파일이 없으면 경고만 하고 진행 → 수동 변환이 소리 없이 빠짐 | 파일이 없으면 오류. 이전 데이터에 있던 변환이 사라지면 오류(검증 추가) |
| 2 | override의 to_code·effective_date 미검증 | to_code가 원천에 있는지, effective_date가 말소일과 같은지 확인 |
| 3 | 잘못된 cp949 바이트가 U+FFFD로 조용히 바뀜 | REPORT 디코더로 바꾸고 줄 번호와 함께 오류 |
| 4 | 여러 파일을 한꺼번에 원자적으로 바꾸지 않음 | **반영 안 함**: 실패는 종료 코드 1로 드러나고 `data/v1`은 git으로 되돌릴 수 있음. 한계를 주석에 명시 |
| 5 | HTTP 타임아웃이 본문 읽기에는 안 걸림 | 요청 전체에 3분 상한 |
| 6 | HTML이 준 다른 호스트 주소를 따라감 | `https://www.mois.go.kr` 밖이면 오류 |
| 7 | 리 단위 행정동 코드를 처리하지 않음 | 실제 데이터에 0건이지만 리 단위까지 처리하도록 추가 |

추가한 테스트 5개(각 지적의 실패 사례)를 먼저 실패시킨 뒤 고쳤다. 반영 후 테스트 77개 통과, 실제 원천 재실행 결과 동일.

## Issues Encountered

- **TLS 인증서**: 이 환경의 Java 기본 truststore가 네트워크 인증서를 신뢰하지 않아 Gradle 플러그인 다운로드와 Java HttpClient가 실패했다. macOS 키체인을 쓰는 옵션(`-Djavax.net.ssl.trustStoreType=KeychainStore`)을 명령에만 붙여 우회했다. 사용자 설정 파일은 바꾸지 않았다.

## Tests Written

| Test File | Tests | Area |
|---|---|---|
| `model/.../LevelTest` | 6 | 코드 구조 → 단위 |
| `model/.../CodesTest` | 7 | 코드 형식 |
| `model/.../ValidityTest` | 5 | 반열린 구간, 빈 구간 |
| `model/.../RecordsTest` | 5 | record 검증 |
| `pipeline/.../FixedWidthParserTest` | 7 | 바이트 기준 슬라이스, CRLF, 짧은 행 |
| `pipeline/.../JscodeArchiveReaderTest` | 5 | MS949 엔트리, 누락·기준일 불일치·크기 상한 |
| `pipeline/.../RecordMapperTest` | 7 | 행 변환, 행 번호가 담긴 오류 |
| `pipeline/.../SuccessionDeriverTest` | 7 | 연쇄 도출, 통합, 다른 시도 동명 구, 수동 상위, overrides |
| `pipeline/.../OverrideLoaderTest` | 4 | TSV 읽기, 줄 번호, 중복 |
| `pipeline/.../DatasetValidatorTest` | 9 | 무결성, 출장소, 알려진 사례, 이전 데이터 비교 |
| `pipeline/.../TsvRoundTripTest` | 6 | 정렬·결정성·왕복·깨진 파일 |
| `pipeline/.../MoisBoardParserTest` | 5 | 목록·첨부 파싱, 릴리스 선택 |

## Next Steps
- [ ] `build --from-mois`로 다운로드 경로 실제 확인
- [ ] 미해결 122건을 변경내역 hwpx로 `overrides/`에 채우기 (데이터 작업)
- [x] 코드 리뷰 반영
- [ ] 커밋
- [ ] Phase 3 (배포 자동화) · Phase 4 (SDK) 계획
