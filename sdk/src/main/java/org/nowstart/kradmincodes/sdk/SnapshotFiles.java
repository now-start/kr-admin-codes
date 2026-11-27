package org.nowstart.kradmincodes.sdk;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 스냅샷 계약: 파일 이름, 헤더, TSV 읽기, 해시. 파이프라인의 {@code DatasetLayout}과 같은 내용이어야 한다(SDK는 파이프라인에
 * 의존하지 않으므로 여기 따로 둔다).
 */
final class SnapshotFiles {

    static final String META = "meta.tsv";
    static final String ADMIN = "admin-dong.tsv";
    static final String LEGAL = "legal-dong.tsv";
    static final String LINK = "admin-legal-link.tsv";
    static final String SUCCESSION = "succession.tsv";
    static final List<String> DATA = List.of(ADMIN, LEGAL, LINK, SUCCESSION);
    static final String FORMAT = "1";

    static final List<String> META_HEADER = List.of("key", "value");
    static final List<String> ADMIN_HEADER = List.of("code", "sido", "sigungu", "dong", "valid_from", "valid_to");
    static final List<String> LEGAL_HEADER =
            List.of("code", "sido", "sigungu", "eupmyeondong", "ri", "valid_from", "valid_to");
    static final List<String> LINK_HEADER = List.of("admin_code", "legal_code", "valid_from", "valid_to");
    static final List<String> SUCCESSION_HEADER = List.of("from_code", "to_code", "effective_date", "method");

    private SnapshotFiles() {
    }

    /** 헤더가 정확히 같아야 하고 모든 줄의 열 수가 헤더와 같아야 한다. 빈 줄은 건너뛴다. */
    static List<List<String>> rows(String file, String content, List<String> header) {
        String[] lines = content.split("\n", -1);
        if (!List.of(lines[0].split("\t", -1)).equals(header)) {
            throw new SnapshotException(file + "의 헤더가 다릅니다. 기대: " + header);
        }
        List<List<String>> rows = new ArrayList<>(lines.length);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            List<String> cells = List.of(lines[i].split("\t", -1));
            if (cells.size() != header.size()) {
                throw new SnapshotException(file + " " + (i + 1) + "번째 줄의 열 수가 다릅니다: 기대 "
                        + header.size() + ", 실제 " + cells.size());
            }
            rows.add(cells);
        }
        return rows;
    }

    static LocalDate date(String value) {
        return value.isEmpty() ? null : LocalDate.parse(value);
    }

    /** 잘못된 바이트를 U+FFFD로 바꾸지 않고 거부한다. */
    static String utf8(String file, byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new SnapshotException(file + "이(가) UTF-8이 아닙니다", e);
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    /** {@code "이름:sha256\n"}을 파일 이름 순으로 이은 문자열의 sha256. 파이프라인이 meta.tsv의 version을 만드는 방식과 같다. */
    static String version(List<String> sortedFiles, java.util.function.Function<String, String> hashOf) {
        var input = new ByteArrayOutputStream();
        for (String file : sortedFiles) {
            input.writeBytes((file + ":" + hashOf.apply(file) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return sha256Hex(input.toByteArray());
    }
}
