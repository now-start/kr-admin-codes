package org.nowstart.kradmincodes.sdk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RefreshTest {

    private static final Duration INTERVAL = Duration.ofHours(6);

    private final Map<String, byte[]> base = AdminCodesQueryTest.sample().files();
    private final Map<String, byte[]> newer = AdminCodesQueryTest.sample()
            .admin("1111077000", "서울특별시", "종로구", "새동", "2026-11-01", "")
            .dataDate("2026-11-20")
            .files();

    @Test
    void unchangedWhenRemoteHasSameVersion() {
        try (AdminCodes codes = create(new MapSource("remote", base))) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UNCHANGED);
            assertThat(codes.info().origin()).isEqualTo("bundled-test");
            assertThat(codes.info().lastCheckedAt()).isEqualTo(AdminCodesQueryTest.FIXED.instant());
        }
    }

    @Test
    void swapsWholeSnapshotWhenRemoteHasNewVersion() {
        try (AdminCodes codes = create(new MapSource("remote", newer))) {
            assertThat(codes.adminDong("1111077000")).isEmpty();

            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UPDATED);

            assertThat(codes.adminDong("1111077000")).isPresent();
            assertThat(codes.info().origin()).isEqualTo("remote");
            assertThat(codes.info().dataDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        }
    }

    @Test
    void rejectsFileWhoseHashDoesNotMatchMeta() {
        Map<String, byte[]> tampered = new HashMap<>(newer);
        tampered.put(SnapshotFiles.ADMIN, base.get(SnapshotFiles.ADMIN));

        try (AdminCodes codes = create(new MapSource("remote", tampered))) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.FAILED);
            assertThat(codes.adminDong("1111077000")).isEmpty();
            assertThat(codes.info().origin()).isEqualTo("bundled-test");
        }
    }

    @Test
    void rejectsBrokenMeta() {
        Map<String, byte[]> broken = new HashMap<>(newer);
        broken.put(SnapshotFiles.META, "key\tvalue\nformat\t9\n".getBytes(UTF_8));

        try (AdminCodes codes = create(new MapSource("remote", broken))) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.FAILED);
        }
    }

    @Test
    void fallsBackToNextSourceWhenFirstIsDown() {
        var first = new MapSource("first", newer);
        first.down(true);

        try (AdminCodes codes = create(first, new MapSource("second", newer))) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UPDATED);
            assertThat(codes.info().origin()).isEqualTo("second");
        }
    }

    @Test
    void keepsServingWhenAllSourcesFail() {
        var only = new MapSource("only", newer);
        only.down(true);

        try (AdminCodes codes = create(only)) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.FAILED);
            assertThat(codes.info().lastCheckedAt()).isNull();
            assertThat(codes.adminDong("4200000000")).isPresent();
        }
    }

    @Test
    void skipsRemoteOlderThanCurrentAndTriesNext() {
        Map<String, byte[]> newest = AdminCodesQueryTest.sample()
                .admin("1111066000", "서울특별시", "종로구", "최신동", "2026-12-01", "")
                .dataDate("2026-12-01")
                .files();

        try (AdminCodes codes = AdminCodes.create(new MapSource("bundled-test", newer),
                List.of(new MapSource("stale-cdn", base), new MapSource("pages", newest)),
                AdminCodesQueryTest.FIXED, false, INTERVAL)) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UPDATED);
            assertThat(codes.info().origin()).isEqualTo("pages");
        }
    }

    @Test
    void neverDowngradesWhenOnlyAnOlderRemoteAnswers() {
        try (AdminCodes codes = AdminCodes.create(new MapSource("bundled-test", newer),
                List.of(new MapSource("stale-cdn", base)), AdminCodesQueryTest.FIXED, false, INTERVAL)) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UNCHANGED);
            assertThat(codes.adminDong("1111077000")).isPresent();
            assertThat(codes.info().origin()).isEqualTo("bundled-test");
            assertThat(codes.info().lastCheckedAt()).isNotNull();
        }
    }

    @Test
    void closedInstanceKeepsServingButNeverRefreshes() {
        AdminCodes codes = create(new MapSource("remote", newer));
        codes.close();
        codes.close();

        assertThat(codes.refreshNow()).isEqualTo(RefreshResult.FAILED);
        assertThat(codes.adminDong("1111077000")).isEmpty();
        assertThat(codes.adminDong("4200000000")).isPresent();
    }

    @Test
    void brokenBundledSnapshotFailsFast() {
        Map<String, byte[]> tampered = new HashMap<>(base);
        tampered.put(SnapshotFiles.LINK, newer.get(SnapshotFiles.ADMIN));

        assertThatThrownBy(() -> AdminCodes.create(
                new MapSource("bundled-test", tampered), List.of(), AdminCodesQueryTest.FIXED, false, INTERVAL))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("sha256");
    }

    @Test
    void missingBundledFileFailsFast() {
        Map<String, byte[]> missing = new HashMap<>(base);
        missing.remove(SnapshotFiles.SUCCESSION);

        assertThatThrownBy(() -> AdminCodes.create(
                new MapSource("bundled-test", missing), List.of(), AdminCodesQueryTest.FIXED, false, INTERVAL))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining(SnapshotFiles.SUCCESSION);
    }

    @Test
    void backgroundRefreshPicksUpNewVersion() throws InterruptedException {
        var remote = new MapSource("remote", newer);
        try (AdminCodes codes = AdminCodes.create(new MapSource("bundled-test", base), List.of(remote),
                AdminCodesQueryTest.FIXED, true, Duration.ofMillis(20))) {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!"remote".equals(codes.info().origin()) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertThat(codes.info().origin()).isEqualTo("remote");
        }
    }

    private AdminCodes create(SnapshotSource... remotes) {
        return AdminCodes.create(new MapSource("bundled-test", base), List.of(remotes),
                AdminCodesQueryTest.FIXED, false, INTERVAL);
    }
}
