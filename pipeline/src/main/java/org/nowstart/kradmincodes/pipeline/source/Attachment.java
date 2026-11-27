package org.nowstart.kradmincodes.pipeline.source;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/** 게시글의 {@code jscodeYYYYMMDD(말소코드포함).zip} 첨부. */
public record Attachment(String fileName, LocalDate dataDate, URI uri) {

    public Attachment {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(dataDate, "dataDate");
        Objects.requireNonNull(uri, "uri");
    }
}
