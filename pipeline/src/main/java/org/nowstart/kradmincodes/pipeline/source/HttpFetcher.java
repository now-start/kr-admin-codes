package org.nowstart.kradmincodes.pipeline.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** 타임아웃·크기 제한·쿠키를 갖춘 얇은 HTTP 래퍼. 실패하면 재시도하지 않고 바로 멈춘다(다음 날 다시 돈다). */
public final class HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TOTAL_TIMEOUT = Duration.ofMinutes(3);
    private static final long MAX_PAGE_BYTES = 5L * 1024 * 1024;
    private static final String USER_AGENT = "kr-admin-codes-pipeline";
    private static final int OK = 200;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .build();

    public String getText(URI uri) {
        return new String(get(uri, null, MAX_PAGE_BYTES), StandardCharsets.UTF_8);
    }

    /** @param referer 다운로드를 허용받기 위해 붙이는 게시글 주소 */
    public byte[] getBytes(URI uri, URI referer, long maxBytes) {
        return get(uri, referer, maxBytes);
    }

    private byte[] get(URI uri, URI referer, long maxBytes) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET();
        if (referer != null) {
            request.header("Referer", referer.toString());
        }
        // HttpRequest.timeout은 응답 헤더까지만 걸린다. 본문을 느리게 보내도 멈추지 않도록 전체 시간에 상한을 둔다.
        CompletableFuture<byte[]> body = client
                .sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> readBody(uri, response, maxBytes));
        try {
            return body.get(TOTAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            body.cancel(true);
            throw new PipelineException(uri + " 요청이 " + TOTAL_TIMEOUT.toSeconds() + "초 안에 끝나지 않았습니다", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PipelineException failure) {
                throw failure;
            }
            throw new PipelineException(uri + " 요청에 실패했습니다: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PipelineException(uri + " 요청 중 중단되었습니다", e);
        }
    }

    private static byte[] readBody(URI uri, HttpResponse<InputStream> response, long maxBytes) {
        try (InputStream in = response.body()) {
            if (response.statusCode() != OK) {
                throw new PipelineException(uri + " 응답 코드가 " + response.statusCode() + "입니다");
            }
            byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, maxBytes + 1));
            if (data.length > maxBytes) {
                throw new PipelineException(uri + " 응답이 상한(" + maxBytes + "바이트)을 넘습니다");
            }
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
