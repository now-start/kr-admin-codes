package org.nowstart.kradmincodes.sdk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JDK 내장 HttpServer로 원격 스냅샷 디렉터리를 흉내 낸다 (외부 네트워크 없음). */
class HttpSourceTest {

    private static final String PREFIX = "/snap/";
    private static final HttpClient CLIENT = HttpSource.newClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, byte[]> served = new ConcurrentHashMap<>();
    private final List<String> ifNoneMatch = new CopyOnWriteArrayList<>();
    private volatile boolean sendEtag = true;
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(PREFIX, exchange -> {
            String file = exchange.getRequestURI().getPath().substring(PREFIX.length());
            String condition = exchange.getRequestHeaders().getFirst("If-None-Match");
            if (condition != null) {
                ifNoneMatch.add(condition);
            }
            byte[] body = served.get(file);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else if (etag(body).equals(condition)) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                if (sendEtag) {
                    exchange.getResponseHeaders().add("ETag", etag(body));
                }
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void conditionalMetaRequestReusesBodyOnNotModified() throws IOException {
        served.put(SnapshotFiles.META, "meta-body".getBytes(UTF_8));
        var source = new HttpSource(base(PREFIX), () -> CLIENT);

        byte[] first = source.fetch(SnapshotFiles.META, TIMEOUT);
        byte[] second = source.fetch(SnapshotFiles.META, TIMEOUT);

        assertThat(second).isEqualTo(first).isEqualTo("meta-body".getBytes(UTF_8));
        assertThat(ifNoneMatch).hasSize(1);
    }

    @Test
    void metaResponseWithoutEtagClearsCacheSoNextRequestIsUnconditional() throws IOException {
        served.put(SnapshotFiles.META, "v1".getBytes(UTF_8));
        var source = new HttpSource(base(PREFIX), () -> CLIENT);
        source.fetch(SnapshotFiles.META, TIMEOUT);

        sendEtag = false;
        served.put(SnapshotFiles.META, "v2".getBytes(UTF_8));
        assertThat(source.fetch(SnapshotFiles.META, TIMEOUT)).isEqualTo("v2".getBytes(UTF_8));
        source.fetch(SnapshotFiles.META, TIMEOUT);

        assertThat(ifNoneMatch).hasSize(1);
    }

    @Test
    void slowBodyIsCutOffAtDeadline() {
        server.createContext("/slow/", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            OutputStream out = exchange.getResponseBody();
            out.write(new byte[10]);
            out.flush();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        var source = new HttpSource(base("/slow/"), () -> CLIENT);
        long start = System.nanoTime();

        assertThatThrownBy(() -> source.fetch(SnapshotFiles.ADMIN, Duration.ofMillis(300)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("300ms");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void missingFileIsIOException() {
        var source = new HttpSource(base(PREFIX), () -> CLIENT);

        assertThatThrownBy(() -> source.fetch("none.tsv", TIMEOUT))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    void baseWithoutTrailingSlashIsTreatedAsDirectory() throws IOException {
        served.put(SnapshotFiles.ADMIN, "admin".getBytes(UTF_8));
        var source = new HttpSource(base("/snap"), () -> CLIENT);

        assertThat(source.fetch(SnapshotFiles.ADMIN, TIMEOUT)).isEqualTo("admin".getBytes(UTF_8));
        assertThat(source.name()).endsWith("/snap/");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> new HttpSource(URI.create("file:///tmp/"), () -> CLIENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderRefreshesFromHttpSourceThenSeesNoChange() {
        served.putAll(AdminCodesQueryTest.sample()
                .admin("1111077000", "서울특별시", "종로구", "새동", "2026-11-01", "")
                .files());

        try (AdminCodes codes = AdminCodes.builder().autoRefresh(false).sources(List.of(base(PREFIX))).build()) {
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UPDATED);
            assertThat(codes.adminDong("1111077000")).isPresent();
            assertThat(codes.refreshNow()).isEqualTo(RefreshResult.UNCHANGED);
        }
    }

    private URI base(String path) {
        InetSocketAddress address = server.getAddress();
        return URI.create("http://" + address.getHostString() + ":" + address.getPort() + path);
    }

    private static String etag(byte[] body) {
        return "\"" + SnapshotFiles.sha256Hex(body) + "\"";
    }
}
