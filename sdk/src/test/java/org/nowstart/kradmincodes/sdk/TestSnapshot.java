package org.nowstart.kradmincodes.sdk;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 테스트용 합성 스냅샷. 파이프라인과 같은 방식으로 meta.tsv(파일별 sha256, version)를 만든다. */
final class TestSnapshot {

    private final List<String> admin = new ArrayList<>();
    private final List<String> legal = new ArrayList<>();
    private final List<String> link = new ArrayList<>();
    private final List<String> succession = new ArrayList<>();
    private String dataDate = "2026-11-11";

    TestSnapshot admin(String code, String sido, String sigungu, String dong, String from, String to) {
        admin.add(String.join("\t", code, sido, sigungu, dong, from, to));
        return this;
    }

    TestSnapshot legal(String code, String from, String to) {
        legal.add(String.join("\t", code, "시도", "시군구", "읍면동", "", from, to));
        return this;
    }

    TestSnapshot link(String adminCode, String legalCode, String from, String to) {
        link.add(String.join("\t", adminCode, legalCode, from, to));
        return this;
    }

    TestSnapshot succession(String from, String to, String date, String method) {
        succession.add(String.join("\t", from, to, date, method));
        return this;
    }

    TestSnapshot dataDate(String dataDate) {
        this.dataDate = dataDate;
        return this;
    }

    Map<String, byte[]> files() {
        Map<String, byte[]> files = new HashMap<>();
        files.put(SnapshotFiles.ADMIN, table(SnapshotFiles.ADMIN_HEADER, admin));
        files.put(SnapshotFiles.LEGAL, table(SnapshotFiles.LEGAL_HEADER, legal));
        files.put(SnapshotFiles.LINK, table(SnapshotFiles.LINK_HEADER, link));
        files.put(SnapshotFiles.SUCCESSION, table(SnapshotFiles.SUCCESSION_HEADER, succession));
        files.put(SnapshotFiles.META, meta(files));
        return files;
    }

    private byte[] meta(Map<String, byte[]> files) {
        List<String> sorted = SnapshotFiles.DATA.stream().sorted().toList();
        String version = SnapshotFiles.version(sorted, file -> SnapshotFiles.sha256Hex(files.get(file)));
        var out = new StringBuilder("key\tvalue\nformat\t1\ndata_date\t" + dataDate + "\nversion\t" + version + "\n");
        for (String file : sorted) {
            out.append("sha256.").append(file).append('\t').append(SnapshotFiles.sha256Hex(files.get(file))).append('\n');
        }
        return out.toString().getBytes(UTF_8);
    }

    private static byte[] table(List<String> header, List<String> rows) {
        var out = new StringBuilder(String.join("\t", header)).append('\n');
        rows.forEach(row -> out.append(row).append('\n'));
        return out.toString().getBytes(UTF_8);
    }
}
