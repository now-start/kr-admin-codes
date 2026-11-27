# Implementation Report: Phase 4 — Java SDK

## Summary
`sdk` 모듈(Java 17, 외부 런타임 의존성 0개)을 만들었다. 레포의 `data/v1`을 jar에 그대로 넣어 바로 쓰고, 시점 조회·옛→새 코드 변환(연쇄·분할)·행정동↔법정동 매핑을 제공하며, 원격 스냅샷으로 스스로 갱신한다. 이를 위해 파이프라인이 `data/v1/meta.tsv`(파일별 sha256, 내용 기반 version)를 쓰도록 했다.

## Assessment vs Reality

| Metric | Plan | Actual |
|---|---|---|
| Complexity | Large | Large |
| 메모리 (내장 스냅샷, Java 21) | 측정 후 보고 | 처음 약 36MB → 값 공유 후 약 11MB |
| 로드 시간 | — | 약 0.33초 |
| jar 크기 | — | 0.8MB |

## Delivered

- **API**: `AdminCodes` — `adminDong`·`legalDong`(코드 조회), `adminDongs(date)`, `convert(code, date)`, `legalDongsOf`·`adminDongsOf(code, date)`, 날짜 없는 오버로드(Asia/Seoul 오늘), `refreshNow()`, `info()`, `close()`
- **스냅샷 계약** (Phase 3도 이대로 배포): 디렉터리 = `meta.tsv` + 데이터 TSV 4개. `version`은 `"파일명:sha256\n"`을 파일명 순으로 이은 문자열의 sha256
- **갱신**: 단일 데몬 스레드, 기본 6시간(첫 확인은 최대 1분 뒤), 소스 순서 Pages → jsDelivr. 파일별 sha256·version 일치·전체 파싱이 모두 통과해야 교체. 실패하면 예외 없이 기존 유지
- **파이프라인**: `TsvWriter`가 `meta.tsv`를 마지막에 씀. 기존 데이터 파일은 바이트 그대로
- `.gitattributes`: `data/**`·`overrides/**`를 LF로 고정(CRLF 변환이 해시를 깨지 않도록), `*.bat` CRLF

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `-Xlint:all -Werror` |
| Unit Tests | Pass | 전체 115개 (model 23, pipeline 55, sdk 37) |
| Coverage | Pass | sdk 라인 92.4%·분기 84.7%, pipeline 라인 94.9%, model 라인 100% |
| Integration | Pass | JDK 내장 `HttpServer`로 원격 갱신(UPDATED → 304 → UNCHANGED), 실제 내장 스냅샷으로 강원·광주 동구·군위 변환 |
| Mutation check | Pass | 효력일 검사 제거, sha256 검사 제거 두 변형 모두 테스트가 잡음 (처음엔 효력일 변형을 못 잡아 흡수 통합 테스트 추가) |

## Code Review (java-reviewer, CRITICAL 0 · HIGH 2 · MEDIUM 8)

| # | 지적 | 조치 |
|---|---|---|
| 1 | 원격이 더 오래된 데이터여도 버전만 다르면 교체(다운그레이드) | 기준일이 지금보다 이르면 건너뛰고 다음 소스. 기준일이 같고 버전만 다른 건 **허용** — overrides만 고쳐 다시 만들면 그렇게 되는 정상 갱신이라서 |
| 2 | `refreshNow()`가 최대 20분 막힐 수 있음 | 갱신 한 번 전체 3분 예산, 남은 시간을 요청마다 전달 |
| 3 | 시간 초과 후에도 본문 읽기가 남음 | `sendAsync` 취소 + 스트림 닫기, 느린 본문 테스트 추가 |
| 4 | 원격을 안 써도 HttpClient 생성 | 처음 요청할 때 생성 |
| 5 | 예약 작업에서 Error가 나면 조용히 멈춤 | Error를 ERROR 로그로 남긴 뒤 던짐 |
| 6 | 닫은 뒤에도 교체·경고 로그 가능 | closed 플래그로 루프·교체 차단, 닫힌 뒤 경고 억제 |
| 7 | `adminDongs` 정렬을 파일 순서에 의존 | 생성자에서 코드 순 정렬 |
| 8 | Windows CRLF로 해시 불일치 가능 | `.gitattributes` |
| 9 | null 인자 NPE가 문서화 안 됨 | `requireNonNull` + Javadoc |
| 10 | ETag 없는 200 뒤 옛 캐시가 남음 | ETag가 없으면 캐시 비움, 테스트 추가 |

남은 테스트 빈틈: 동시성(교체 중 조회, 갱신 중 close) 테스트, 64MB 상한·리다이렉트 테스트는 없다. 백그라운드 갱신 테스트는 `Thread.sleep` 폴링(5초 상한)이다.

## Deviations from Plan
- `SnapshotSource.fetch`에 남은 시간 인자를 추가(리뷰 2·3번).
- 계획에 없던 값 공유 풀(문자열·날짜·유효기간)을 파서에 넣음 — 힙 36MB를 11MB로.
- SDK는 테스트를 구현과 함께 썼다(테스트 먼저 실패시키지 않음). 대신 변형 테스트로 테스트가 버그를 잡는지 확인했다.

## Known Limitations
- 기본 원격 주소는 아직 비어 있다. Pages는 Phase 3 배포 후, jsDelivr는 main 머지 후 동작한다. 그 전엔 6시간마다 경고 한 줄이 남는다(`autoRefresh(false)`로 끔).
- 기준일이 같은 두 버전 사이에서는 어느 쪽이 최신인지 알 수 없다(CDN이 같은 날 이전 버전을 주면 그리로 바뀔 수 있음).
- 법정동 코드의 옛→새 변환은 없다(원천에 근거가 행정동만 있음).

## Next Steps
- [ ] 커밋
- [ ] Phase 3: `data/v1`을 Pages `/v1/`에 배포, 매일 수집 워크플로
- [ ] Phase 5: Maven Central 배포, README
