package org.nowstart.kradmincodes.sdk;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 현재 스냅샷을 들고 있고, 원격 소스에서 새 버전을 받아 통째로 바꾼다. 갱신은 어떤 경우에도 예외를 밖으로 던지지 않는다. 파일마다
 * sha256이 meta와 맞고 전체가 파싱될 때만 바꾼다. 데이터 기준일이 지금보다 이른 원격(오래된 CDN 캐시 등)은 건너뛴다.
 */
final class Refresher implements AutoCloseable {

    /** 갱신 한 번(모든 소스 합계)에 쓸 수 있는 시간. {@link AdminCodes#refreshNow()}는 최대 이만큼 막힐 수 있다. */
    static final Duration REFRESH_BUDGET = Duration.ofMinutes(3);

    private static final System.Logger LOG = System.getLogger(Refresher.class.getName());
    private static final Duration MAX_INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration BUNDLED_LOAD_BUDGET = Duration.ofMinutes(1);
    private static final String THREAD_NAME = "kr-admin-codes-refresh";

    private final List<SnapshotSource> remotes;
    private final Clock clock;
    private final AtomicReference<Loaded> current;
    private final Object refreshLock = new Object();
    private volatile Instant lastCheckedAt;
    private volatile boolean closed;
    private ScheduledExecutorService scheduler;

    private Refresher(List<SnapshotSource> remotes, Clock clock, Loaded initial) {
        this.remotes = List.copyOf(remotes);
        this.clock = clock;
        this.current = new AtomicReference<>(initial);
    }

    /** 내장 스냅샷을 읽어 시작한다. 내장 스냅샷이 깨졌으면 패키징 오류이므로 바로 실패한다. */
    static Refresher start(SnapshotSource bundled, List<SnapshotSource> remotes, Clock clock) {
        try {
            long deadline = System.nanoTime() + BUNDLED_LOAD_BUDGET.toNanos();
            SnapshotMeta meta = SnapshotMeta.parse(SnapshotFiles.utf8(
                    SnapshotFiles.META, bundled.fetch(SnapshotFiles.META, remaining(deadline))));
            Snapshot snapshot = load(bundled, meta, deadline);
            return new Refresher(remotes, clock, new Loaded(snapshot, bundled.name(), clock.instant()));
        } catch (IOException e) {
            throw new SnapshotException("내장 스냅샷을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }

    Snapshot current() {
        return current.get().snapshot();
    }

    SnapshotInfo info() {
        Loaded loaded = current.get();
        SnapshotMeta meta = loaded.snapshot().meta();
        return new SnapshotInfo(meta.version(), meta.dataDate(), loaded.origin(), loaded.loadedAt(), lastCheckedAt);
    }

    /**
     * 소스를 순서대로 시도한다. 같은 버전이거나 새 버전으로 바꿨으면 그 결과로 끝낸다. 더 오래된 원격은 건너뛰고 다음 소스를 본다.
     * 응답한 소스가 하나라도 있으면 UNCHANGED, 하나도 없으면 FAILED. 닫힌 뒤에는 아무것도 하지 않는다.
     */
    RefreshResult refreshNow() {
        synchronized (refreshLock) {
            long deadline = System.nanoTime() + REFRESH_BUDGET.toNanos();
            boolean answered = false;
            for (SnapshotSource source : remotes) {
                if (closed || Thread.currentThread().isInterrupted()) {
                    return RefreshResult.FAILED;
                }
                try {
                    Outcome outcome = refreshFrom(source, deadline);
                    answered = true;
                    lastCheckedAt = clock.instant();
                    if (outcome != Outcome.STALE) {
                        return outcome == Outcome.UPDATED ? RefreshResult.UPDATED : RefreshResult.UNCHANGED;
                    }
                } catch (IOException | RuntimeException e) {
                    if (!closed) {
                        LOG.log(WARNING, "스냅샷 갱신 실패 (" + source.name() + "): " + e.getMessage());
                    }
                }
            }
            return answered ? RefreshResult.UNCHANGED : RefreshResult.FAILED;
        }
    }

    synchronized void schedule(Duration interval) {
        if (scheduler != null) {
            throw new IllegalStateException("이미 갱신을 시작했습니다");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        long initialDelay = Math.min(interval.toMillis(), MAX_INITIAL_DELAY.toMillis());
        scheduler.scheduleWithFixedDelay(this::refreshQuietly, initialDelay, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 백그라운드 갱신을 멈춘다. 진행 중인 요청은 중단하고, 끝나기를 기다리지 않는다. 여러 번 불러도 된다. */
    @Override
    public synchronized void close() {
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 예외가 새어 나가면 예약 작업이 조용히 멈추므로 RuntimeException은 여기서 막는다. Error는 로그를 남기고 그대로 던진다(이후 갱신은
     * 멈춘다).
     */
    private void refreshQuietly() {
        try {
            refreshNow();
        } catch (RuntimeException e) {
            LOG.log(WARNING, "스냅샷 갱신 중 예상하지 못한 오류", e);
        } catch (Error e) {
            LOG.log(ERROR, "스냅샷 갱신 중 심각한 오류로 백그라운드 갱신을 멈춥니다", e);
            throw e;
        }
    }

    private Outcome refreshFrom(SnapshotSource source, long deadline) throws IOException {
        SnapshotMeta meta = SnapshotMeta.parse(SnapshotFiles.utf8(
                SnapshotFiles.META, source.fetch(SnapshotFiles.META, remaining(deadline))));
        SnapshotMeta currentMeta = current().meta();
        if (meta.version().equals(currentMeta.version())) {
            return Outcome.UNCHANGED;
        }
        if (meta.dataDate().isBefore(currentMeta.dataDate())) {
            LOG.log(INFO, "지금보다 오래된 스냅샷이라 건너뜁니다 (" + source.name() + ": " + meta.dataDate()
                    + " < " + currentMeta.dataDate() + ")");
            return Outcome.STALE;
        }
        Snapshot snapshot = load(source, meta, deadline);
        if (closed) {
            return Outcome.UNCHANGED;
        }
        current.set(new Loaded(snapshot, source.name(), clock.instant()));
        LOG.log(INFO, "스냅샷을 갱신했습니다: 기준일 " + meta.dataDate() + ", " + source.name());
        return Outcome.UPDATED;
    }

    private static Snapshot load(SnapshotSource source, SnapshotMeta meta, long deadline) throws IOException {
        Map<String, String> files = new HashMap<>();
        for (String file : SnapshotFiles.DATA) {
            byte[] bytes = source.fetch(file, remaining(deadline));
            if (!SnapshotFiles.sha256Hex(bytes).equals(meta.sha256Of(file))) {
                throw new SnapshotException(file + "의 sha256이 meta.tsv와 다릅니다 (" + source.name() + ")");
            }
            files.put(file, SnapshotFiles.utf8(file, bytes));
        }
        return SnapshotParser.parse(meta, files);
    }

    private static Duration remaining(long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new IOException("갱신에 쓸 수 있는 시간을 다 썼습니다");
        }
        return Duration.ofNanos(nanos);
    }

    private enum Outcome { UPDATED, UNCHANGED, STALE }

    private record Loaded(Snapshot snapshot, String origin, Instant loadedAt) {
    }
}
