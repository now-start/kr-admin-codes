package org.nowstart.kradmincodes.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 원격 스냅샷 디렉터리(GitHub Pages, jsDelivr 등). {@code meta.tsv}는 ETag로 조건부 요청을 보내고, 304면 마지막으로 받은 내용을
 * 그대로 돌려준다(버전 비교는 {@link Refresher}가 한다).
 */
final class HttpSource implements SnapshotSource {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_REQUEST_TIME = Duration.ofMinutes(2);
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final String USER_AGENT = "kr-admin-codes-sdk";
    private static final int OK = 200;
    private static final int NOT_MODIFIED = 304;

    private final URI base;
    private final Supplier<HttpClient> client;
    private String metaEtag;
    private byte[] metaBody;

    HttpSource(URI base, Supplier<HttpClient> client) {
        this.base = directory(Objects.requireNonNull(base, "base"));
        this.client = Objects.requireNonNull(client, "client");
    }

    static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 처음 요청할 때 만든다. 원격을 쓰지 않으면 HttpClient(와 그 스레드)를 만들지 않는다. */
    static Supplier<HttpClient> lazyClient() {
        return new Supplier<>() {
            private HttpClient created;

            @Override
            public synchronized HttpClient get() {
                if (created == null) {
                    created = newClient();
                }
                return created;
            }
        };
    }

    @Override
    public String name() {
        return base.toString();
    }

    @Override
    public byte[] fetch(String file, Duration timeout) throws IOException {
        boolean meta = SnapshotFiles.META.equals(file);
        Duration limit = timeout.compareTo(MAX_REQUEST_TIME) < 0 ? timeout : MAX_REQUEST_TIME;
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(file))
                .timeout(limit)
                .header("User-Agent", USER_AGENT)
                .GET();
        String etag;
        byte[] cached;
        synchronized (this) {
            etag = metaEtag;
            cached = metaBody;
        }
        if (meta && etag != null) {
            request.header("If-None-Match", etag);
        }
        Response response = send(request.build(), limit);
        if (meta && response.status() == NOT_MODIFIED && cached != null) {
            return cached.clone();
        }
        if (response.status() != OK) {
            throw new IOException(base.resolve(file) + " 응답 코드가 " + response.status() + "입니다");
        }
        if (meta) {
            remember(response);
        }
        return response.body();
    }

    /** ETag가 없는 응답이면 캐시를 비운다. 남겨두면 다음 304에 옛 내용을 돌려줄 수 있다. */
    private synchronized void remember(Response response) {
        metaEtag = response.etag();
        metaBody = response.etag() == null ? null : response.body().clone();
    }

    /**
     * {@code HttpRequest.timeout}은 응답 헤더까지만 걸리므로 본문까지 포함한 전체 시간에 상한을 둔다. 시간이 넘으면 요청을 취소하고
     * 읽던 스트림을 닫아 연결과 스레드가 남지 않게 한다.
     */
    private Response send(HttpRequest request, Duration limit) throws IOException {
        AtomicReference<InputStream> stream = new AtomicReference<>();
        CompletableFuture<HttpResponse<InputStream>> sent =
                client.get().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        CompletableFuture<Response> read = sent.thenApply(response -> {
            stream.set(response.body());
            return read(response);
        });
        try {
            return read.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            sent.cancel(true);
            read.cancel(true);
            closeQuietly(stream.get());
            throw new IOException(request.uri() + " 요청이 " + limit.toMillis() + "ms 안에 끝나지 않았습니다", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UncheckedIOException unchecked) {
                throw unchecked.getCause();
            }
            throw new IOException(request.uri() + " 요청에 실패했습니다: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sent.cancel(true);
            closeQuietly(stream.get());
            throw new IOException(request.uri() + " 요청 중 중단되었습니다", e);
        }
    }

    private static Response read(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            byte[] body = in.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                throw new IOException(response.uri() + " 응답이 상한(" + MAX_BYTES + "바이트)을 넘습니다");
            }
            return new Response(response.statusCode(), response.headers().firstValue("ETag").orElse(null), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 이미 실패 경로라 닫다가 난 오류는 원래 오류를 가리지 않도록 버린다. */
    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // 원래 시간 초과·중단 오류를 그대로 던진다
        }
    }

    private static URI directory(URI uri) {
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("http(s) 주소만 쓸 수 있습니다: " + uri);
        }
        return uri.getPath().endsWith("/") ? uri : URI.create(uri + "/");
    }

    /** @param etag 없으면 null */
    private record Response(int status, String etag, byte[] body) {
    }
}
