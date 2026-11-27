package org.nowstart.kradmincodes.pipeline.write;

import java.util.List;

/** {@code data/v1/}의 파일 이름과 헤더. 이 열 구성을 바꾸려면 v2를 새로 연다(기존 사용자를 깨지 않도록). */
public final class DatasetLayout {

    public static final String ADMIN_FILE = "admin-dong.tsv";
    public static final String LEGAL_FILE = "legal-dong.tsv";
    public static final String LINK_FILE = "admin-legal-link.tsv";
    public static final String SUCCESSION_FILE = "succession.tsv";
    public static final String UNRESOLVED_FILE = "unresolved-succession.tsv";
    public static final String SOURCE_FILE = "source.tsv";

    public static final List<String> ADMIN_HEADER =
            List.of("code", "sido", "sigungu", "dong", "valid_from", "valid_to");
    public static final List<String> LEGAL_HEADER =
            List.of("code", "sido", "sigungu", "eupmyeondong", "ri", "valid_from", "valid_to");
    public static final List<String> LINK_HEADER = List.of("admin_code", "legal_code", "valid_from", "valid_to");
    public static final List<String> SUCCESSION_HEADER = List.of("from_code", "to_code", "effective_date", "method");
    public static final List<String> UNRESOLVED_HEADER = List.of("code", "sido", "sigungu", "dong", "abolished_on");
    public static final List<String> SOURCE_HEADER = List.of("key", "value");

    private DatasetLayout() {
    }
}
