package org.nowstart.kradmincodes.pipeline.source;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/** 행안부 주민등록 게시판 목록의 게시글 한 줄. */
public record BoardPost(String nttId, String title, LocalDate postedDate, URI uri) {

    public BoardPost {
        Objects.requireNonNull(nttId, "nttId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(postedDate, "postedDate");
        Objects.requireNonNull(uri, "uri");
    }
}
