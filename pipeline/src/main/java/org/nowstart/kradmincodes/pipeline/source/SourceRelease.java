package org.nowstart.kradmincodes.pipeline.source;

import java.util.Objects;

/** 내려받을 원천: 게시글과 그 안의 말소코드포함 zip 첨부. */
public record SourceRelease(BoardPost post, Attachment attachment) {

    public SourceRelease {
        Objects.requireNonNull(post, "post");
        Objects.requireNonNull(attachment, "attachment");
    }
}
