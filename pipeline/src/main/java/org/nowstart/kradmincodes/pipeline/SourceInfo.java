package org.nowstart.kradmincodes.pipeline;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 데이터를 만든 원천의 출처 정보. {@code data/v1/source.tsv}로 남겨서 PR 리뷰 때 어느 게시글·파일에서 왔는지 확인한다.
 *
 * @param dataDate zip 파일 이름의 기준일
 * @param archiveSha256 원천 zip의 SHA-256 (16진수)
 * @param postUrl 게시글 주소, 로컬 zip으로 만들면 빈 문자열
 * @param postTitle 게시글 제목, 로컬 zip으로 만들면 빈 문자열
 * @param postedDate 게시일, 모르면 null
 */
public record SourceInfo(
        LocalDate dataDate, String archiveSha256, String postUrl, String postTitle, LocalDate postedDate) {

    public SourceInfo {
        Objects.requireNonNull(dataDate, "dataDate");
        Objects.requireNonNull(archiveSha256, "archiveSha256");
        postUrl = Objects.requireNonNullElse(postUrl, "");
        postTitle = Objects.requireNonNullElse(postTitle, "");
    }

    public static SourceInfo localArchive(LocalDate dataDate, String archiveSha256) {
        return new SourceInfo(dataDate, archiveSha256, "", "", null);
    }
}
