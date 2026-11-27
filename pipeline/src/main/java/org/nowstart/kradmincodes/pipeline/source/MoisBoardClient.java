package org.nowstart.kradmincodes.pipeline.source;

import java.util.List;
import java.util.Objects;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** 행안부 게시판에서 최신 말소코드포함 zip을 찾고 내려받는다. 판단 로직은 {@link MoisBoardParser}에 있다. */
public final class MoisBoardClient {

    /** 원천 zip은 5MB 안팎. */
    public static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024;

    private static final int MAX_POSTS_TO_SCAN = 10;

    private final HttpFetcher fetcher;

    public MoisBoardClient(HttpFetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    public SourceRelease findLatestRelease() {
        List<BoardPost> posts = MoisBoardParser.parseList(fetcher.getText(MoisBoardParser.LIST_URI));
        if (posts.isEmpty()) {
            throw new PipelineException("게시판 목록에서 게시글을 찾지 못했습니다. 페이지 구조가 바뀌었을 수 있습니다: "
                    + MoisBoardParser.LIST_URI);
        }
        return MoisBoardParser.selectRelease(posts, MAX_POSTS_TO_SCAN,
                post -> MoisBoardParser.parseAttachments(fetcher.getText(post.uri())));
    }

    public byte[] download(SourceRelease release) {
        // 게시글을 먼저 열어 세션 쿠키를 받아야 첨부 다운로드가 허용된다
        fetcher.getText(release.post().uri());
        byte[] zip = fetcher.getBytes(release.attachment().uri(), release.post().uri(), MAX_ARCHIVE_BYTES);
        if (zip.length < 2 || zip[0] != 'P' || zip[1] != 'K') {
            throw new PipelineException("받은 파일이 zip이 아닙니다: " + release.attachment().fileName());
        }
        return zip;
    }
}
