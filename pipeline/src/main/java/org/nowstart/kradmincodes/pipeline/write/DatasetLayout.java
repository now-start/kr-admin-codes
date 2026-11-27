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
    /** SDK가 갱신할 때 먼저 받는 파일. 파일별 sha256과 내용 기반 version을 담는다. 마지막에 쓴다. */
    public static final String META_FILE = "meta.tsv";
    public static final String META_FORMAT = "1";

    /** SDK 스냅샷을 이루는 파일. {@code meta.tsv}에 각각의 sha256이 들어간다. */
    public static final List<String> SNAPSHOT_FILES = List.of(ADMIN_FILE, LEGAL_FILE, LINK_FILE, SUCCESSION_FILE);

    public static final List<String> ADMIN_HEADER =
            List.of("code", "sido", "sigungu", "dong", "valid_from", "valid_to");
    public static final List<String> LEGAL_HEADER =
            List.of("code", "sido", "sigungu", "eupmyeondong", "ri", "valid_from", "valid_to");
    public static final List<String> LINK_HEADER = List.of("admin_code", "legal_code", "valid_from", "valid_to");
    public static final List<String> SUCCESSION_HEADER = List.of("from_code", "to_code", "effective_date", "method");
    public static final List<String> UNRESOLVED_HEADER = List.of("code", "sido", "sigungu", "dong", "abolished_on");
    public static final List<String> SOURCE_HEADER = List.of("key", "value");
    public static final List<String> META_HEADER = List.of("key", "value");

    private DatasetLayout() {
    }
}
