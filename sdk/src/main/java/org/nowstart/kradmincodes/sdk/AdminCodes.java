package org.nowstart.kradmincodes.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.LegalDong;

/**
 * 행정동·법정동 코드 조회 SDK의 진입점.
 *
 * <p>jar에 내장된 스냅샷으로 바로 동작하고, 기본으로 6시간마다 원격 스냅샷을 확인해 새 버전이면 통째로 바꾼다. 갱신이 실패해도
 * 예외를 던지지 않고 가진 데이터로 계속 동작한다(원인은 {@code System.Logger} 경고). 외부 통신을 원하지 않으면
 * {@code autoRefresh(false)}로 끈다.
 *
 * <p>모든 조회는 호출 시점의 스냅샷 하나만 보므로 갱신 도중에도 결과가 섞이지 않는다. 스레드에 안전하다. 코드·날짜 인자에 null을
 * 넘기면 {@link NullPointerException}.
 *
 * <p>원격의 데이터 기준일이 지금보다 이르면(오래된 CDN 캐시 등) 건너뛰므로, 내장 스냅샷이 더 새것이면 그대로 쓴다.
 *
 * <pre>{@code
 * try (var codes = AdminCodes.builder().build()) {
 *     List<AdminDong> now = codes.convert("4211051000");   // 강원도 춘천시 교동 → 강원특별자치도 춘천시 교동
 * }
 * }</pre>
 */
public final class AdminCodes implements AutoCloseable {

    public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofHours(6);

    /** 순서대로 시도한다. GitHub Pages(Phase 3에서 배포), jsDelivr(레포 main 브랜치의 data/v1). */
    public static final List<URI> DEFAULT_SOURCES = List.of(
            URI.create("https://now-start.github.io/kr-admin-codes/v1/"),
            URI.create("https://cdn.jsdelivr.net/gh/now-start/kr-admin-codes@main/data/v1/"));

    /** 행정구역 시행일은 한국 날짜 기준이다. */
    public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final Refresher refresher;
    private final Clock clock;

    private AdminCodes(Refresher refresher, Clock clock) {
        this.refresher = refresher;
        this.clock = clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    static AdminCodes create(
            SnapshotSource bundled, List<SnapshotSource> remotes, Clock clock, boolean autoRefresh, Duration interval) {
        Refresher refresher = Refresher.start(bundled, remotes, clock);
        if (autoRefresh && !remotes.isEmpty()) {
            refresher.schedule(interval);
        }
        return new AdminCodes(refresher, clock);
    }

    /** 유효기간과 상관없이 코드로 찾는다(말소된 코드도 나온다). */
    public Optional<AdminDong> adminDong(String code) {
        return refresher.current().adminDong(Objects.requireNonNull(code, "code"));
    }

    /** 유효기간과 상관없이 코드로 찾는다. */
    public Optional<LegalDong> legalDong(String code) {
        return refresher.current().legalDong(Objects.requireNonNull(code, "code"));
    }

    /** 그날 유효한 행정동 전체 (시도·시군구 단위 행 포함), 코드 순. */
    public List<AdminDong> adminDongs(LocalDate date) {
        return refresher.current().adminDongs(Objects.requireNonNull(date, "date"));
    }

    public List<AdminDong> adminDongs() {
        return adminDongs(today());
    }

    /**
     * 그날 유효한 코드로 바꾼다. 이미 유효하면 자기 자신, 개편으로 바뀌었으면 후계 코드(나뉘었으면 여러 개). 모르는 코드이거나 후계가
     * 정리되지 않은 코드면 빈 목록이다. 새 코드에서 옛 코드로 거꾸로는 바꾸지 않는다.
     */
    public List<AdminDong> convert(String code, LocalDate date) {
        return refresher.current().convert(Objects.requireNonNull(code, "code"), Objects.requireNonNull(date, "date"));
    }

    public List<AdminDong> convert(String code) {
        return convert(code, today());
    }

    /** 그날 그 행정동이 관할하는 법정동, 코드 순. */
    public List<LegalDong> legalDongsOf(String adminCode, LocalDate date) {
        return refresher.current().legalDongsOf(
                Objects.requireNonNull(adminCode, "adminCode"), Objects.requireNonNull(date, "date"));
    }

    public List<LegalDong> legalDongsOf(String adminCode) {
        return legalDongsOf(adminCode, today());
    }

    /** 그날 그 법정동을 관할하는 행정동, 코드 순. */
    public List<AdminDong> adminDongsOf(String legalCode, LocalDate date) {
        return refresher.current().adminDongsOf(
                Objects.requireNonNull(legalCode, "legalCode"), Objects.requireNonNull(date, "date"));
    }

    public List<AdminDong> adminDongsOf(String legalCode) {
        return adminDongsOf(legalCode, today());
    }

    /** 원격을 지금 확인한다. 예외를 던지지 않는다. 진행 중인 갱신이 있으면 기다리므로 최대 약 3분 막힐 수 있다. */
    public RefreshResult refreshNow() {
        return refresher.refreshNow();
    }

    public SnapshotInfo info() {
        return refresher.info();
    }

    /** 백그라운드 갱신을 멈춘다. 조회는 닫은 뒤에도 마지막 스냅샷으로 계속 된다. */
    @Override
    public void close() {
        refresher.close();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** {@link AdminCodes} 설정. 한 번 만들고 버리는 용도라 가변이다. */
    public static final class Builder {

        private boolean autoRefresh = true;
        private Duration refreshInterval = DEFAULT_REFRESH_INTERVAL;
        private List<URI> sources = DEFAULT_SOURCES;
        private Clock clock = Clock.system(KOREA);

        private Builder() {
        }

        /** 백그라운드 갱신 여부. 기본 true. 끄면 외부 통신을 하지 않는다({@link #refreshNow()}는 계속 쓸 수 있다). */
        public Builder autoRefresh(boolean autoRefresh) {
            this.autoRefresh = autoRefresh;
            return this;
        }

        public Builder refreshInterval(Duration refreshInterval) {
            Objects.requireNonNull(refreshInterval, "refreshInterval");
            if (refreshInterval.isNegative() || refreshInterval.isZero()) {
                throw new IllegalArgumentException("refreshInterval은 양수여야 합니다: " + refreshInterval);
            }
            this.refreshInterval = refreshInterval;
            return this;
        }

        /** 원격 스냅샷 주소 목록. 비우면 내장 스냅샷만 쓴다. */
        public Builder sources(List<URI> sources) {
            this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            return this;
        }

        /** 날짜 없는 조회의 "오늘" 기준. 기본은 Asia/Seoul 시스템 시계. */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** 내장 스냅샷을 읽는다(수백 ms). 내장 스냅샷이 깨졌으면 {@link SnapshotException}. */
        public AdminCodes build() {
            Supplier<HttpClient> http = HttpSource.lazyClient();
            List<SnapshotSource> remotes = sources.stream()
                    .<SnapshotSource>map(uri -> new HttpSource(uri, http))
                    .toList();
            return create(new ClasspathSource(), remotes, clock, autoRefresh, refreshInterval);
        }
    }
}
