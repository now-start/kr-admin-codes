# Plan: Phase 4 — Java SDK

## Summary
`data/v1` 스냅샷을 jar에 내장하고, 시점 기준 조회·옛→새 코드 변환·행정동↔법정동 매핑을 제공하며, 원격 스냅샷으로 스스로 갱신하는 의존성 0개(외부 라이브러리 기준) Java 17 SDK를 만든다. 갱신이 실패해도 예외를 던지지 않고 가진 데이터로 계속 동작한다.

## User Story
As a 주소·지역을 다루는 Java 백엔드 개발자, I want 의존성 하나로 코드 조회와 변환을 하고, so that 행정구역 개편 때 손대지 않아도 시행일에 맞춰 바뀐 코드를 쓴다.

## Metadata
- **Complexity**: Large
- **Source PRD**: `.claude/PRPs/prds/kr-admin-codes.prd.md`
- **PRD Phase**: 4 — Java SDK
- **Depends on**: Phase 2 (`data/v1` 형식). Phase 3 이전에는 원격 기본 주소가 아직 비어 있다

---

## Snapshot Contract (Phase 3도 이 계약대로 배포한다)

스냅샷 = 디렉터리 하나. 파이프라인이 `data/v1/`에 그대로 쓴다.

| 파일 | 내용 |
|---|---|
| `meta.tsv` | `key\tvalue` — `format`(1), `data_date`, `version`, `sha256.<파일>` |
| `admin-dong.tsv`, `legal-dong.tsv`, `admin-legal-link.tsv`, `succession.tsv` | Phase 2 형식 그대로 |

- `version` = `"<파일명>:<sha256>\n"`을 파일명 순으로 이은 문자열의 sha256. 내용이 같으면 같은 값.
- SDK는 `meta.tsv`를 먼저 받아 `version`이 같으면 끝. 다르면 네 파일을 받아 각각 sha256을 확인한 뒤 전부 맞을 때만 교체한다.
- 기본 원격 주소(순서대로 시도): `https://now-start.github.io/kr-admin-codes/v1/`(Phase 3에서 배포), `https://cdn.jsdelivr.net/gh/now-start/kr-admin-codes@main/data/v1/`(main 머지 후 동작).

## Public API

```java
try (var codes = AdminCodes.builder().build()) {          // 내장 스냅샷으로 즉시 사용, 백그라운드 갱신 시작
    codes.adminDong("1111051500");                         // Optional<AdminDong>, 유효기간 무관 코드 조회
    codes.adminDongs(LocalDate.of(2023, 1, 1));            // 그날 유효한 행정동 전체
    codes.convert("4211051000", LocalDate.now());          // List<AdminDong>, 그날 유효한 후계 코드(1:N 가능)
    codes.legalDongsOf("1111051500", date);                // 그날 그 행정동이 관할하는 법정동
    codes.adminDongsOf("1111010100", date);                // 그날 그 법정동을 관할하는 행정동
    codes.refreshNow();                                    // RefreshResult: UPDATED / UNCHANGED / FAILED
    codes.info();                                          // SnapshotInfo: version, dataDate, origin, lastCheckedAt
}
```

Builder: `autoRefresh(boolean)`(기본 true), `refreshInterval(Duration)`(기본 6시간), `sources(List<URI>)`(기본 위 두 곳), `clock(Clock)`(기본 Asia/Seoul).

- 날짜 없는 오버로드는 `clock` 기준 오늘.
- `convert`: 코드가 그날 유효하면 자기 자신. 아니면 효력일이 그날 이전인 변환을 따라가며(순환 방지) 그날 유효한 코드를 모은다. 모르는 코드나 후계가 없는 코드는 빈 목록.
- 이름 검색은 이번 범위가 아니다(PRD Must 아님).

## Design

- 패키지 `org.nowstart.kradmincodes.sdk`. `model` 모듈을 `api`로 노출(record를 그대로 반환). 그 외 런타임 의존성 없음.
- `Snapshot`(package-private, 불변): 코드→행, 코드→매핑 목록, from→변환 목록 인덱스. `AtomicReference<Snapshot>`으로 통째 교체.
- `SnapshotMeta`·`SnapshotParser`: TSV를 엄격히 읽는다(헤더·열 수·형식). 내장 스냅샷이 깨졌으면 `build()`에서 바로 실패(패키징 오류), 원격이 깨졌으면 거부하고 기존 유지.
- `SnapshotSource` 인터페이스: `ClasspathSource`(jar 내장), `HttpSource`(기본 주소 + `HttpClient`, 타임아웃, 크기 상한, meta ETag로 304 처리).
- `Refresher`: 단일 데몬 스레드(`kr-admin-codes-refresh`)에서 `refreshNow()`를 주기 실행. 소스를 순서대로 시도하고 실패는 `System.Logger` 경고만 남긴다. `close()`로 멈춘다.
- 내장: Gradle `processResources`가 루트 `data/v1`의 다섯 파일을 `org/nowstart/kradmincodes/sdk/snapshot/`에 넣는다.

## Files to Change

| File | Action |
|---|---|
| `settings.gradle.kts` | UPDATE — `sdk` 모듈 추가 |
| `pipeline/.../write/DatasetLayout.java`, `TsvWriter.java` | UPDATE — `meta.tsv` 쓰기 |
| `pipeline/.../write/TsvRoundTripTest.java` | UPDATE — meta 검증 |
| `data/v1/meta.tsv` | CREATE (파이프라인 재실행) |
| `sdk/build.gradle.kts` | CREATE — java-library, Java 17, 스냅샷 리소스 복사, jacoco 80% |
| `sdk/src/main/java/.../sdk/*.java` | CREATE — AdminCodes, Builder, Snapshot, SnapshotMeta, SnapshotParser, SnapshotSource, ClasspathSource, HttpSource, Refresher, RefreshResult, SnapshotInfo, SnapshotException |
| `sdk/src/test/java/.../sdk/*Test.java` | CREATE — 파서, 조회·변환, 갱신(JDK 내장 `HttpServer`), 내장 스냅샷 통합 |

## NOT Building
- 이름 검색, Spring Boot auto-configuration (PRD Could)
- Maven Central 배포 (Phase 5)
- Pages 배포·워크플로 (Phase 3)
- 법정동 코드의 옛→새 변환 (원천에 변환 근거가 행정동만 있음)

## Testing Strategy
- 파서: 정상, 헤더 다름, 열 수 다름, 날짜 오류, meta 누락 키
- 조회: 시점 경계(반열린 구간), 빈 구간 행은 어떤 날짜에도 안 나옴
- 변환: 유효하면 자기 자신, 연쇄(A→B→C), 1:N, 효력일 이후 변환은 안 따라감, 순환 방지, 모르는 코드
- 갱신: version 같으면 UNCHANGED, 바뀌면 UPDATED 후 새 데이터 조회, sha256 불일치 거부, 서버 다운 → FAILED 후 기존 유지, 첫 소스 실패 시 두 번째 소스, 304
- 통합: 내장 스냅샷으로 강원 4200000000 → 5100000000, 광주 동구 → 전남광주 동구, 군위 읍면동 연쇄 확인

## Validation Commands
```bash
./gradlew build
```
EXPECT: 전체 테스트 통과, sdk 커버리지 80% 이상

## Risks
| Risk | Mitigation |
|---|---|
| 내장 스냅샷 메모리 (법정동 5만·매핑 6만 행) | 로드 후 힙 사용량 측정해 보고 |
| 기본 원격 주소가 Phase 3 전엔 404 → 6시간마다 경고 | 실패해도 동작에 영향 없음, 문서화. 끄려면 `autoRefresh(false)` |
| 라이브러리가 기본으로 외부 통신 | README·Javadoc에 명시, 끄는 옵션 제공 |
