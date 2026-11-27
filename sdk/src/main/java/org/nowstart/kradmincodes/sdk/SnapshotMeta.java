package org.nowstart.kradmincodes.sdk;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code meta.tsv}: 형식, 데이터 기준일, 내용 기반 version, 파일별 sha256. */
record SnapshotMeta(String version, LocalDate dataDate, Map<String, String> sha256ByFile) {

    SnapshotMeta {
        sha256ByFile = Map.copyOf(sha256ByFile);
    }

    static SnapshotMeta parse(String content) {
        Map<String, String> values = new HashMap<>();
        for (List<String> row : SnapshotFiles.rows(SnapshotFiles.META, content, SnapshotFiles.META_HEADER)) {
            if (values.put(row.get(0), row.get(1)) != null) {
                throw new SnapshotException("meta.tsv에 같은 키가 두 번 있습니다: " + row.get(0));
            }
        }
        String format = require(values, "format");
        if (!SnapshotFiles.FORMAT.equals(format)) {
            throw new SnapshotException("지원하지 않는 스냅샷 형식입니다: " + format);
        }
        Map<String, String> hashes = new HashMap<>();
        for (String file : SnapshotFiles.DATA) {
            hashes.put(file, require(values, "sha256." + file));
        }
        String version = require(values, "version");
        String expected = SnapshotFiles.version(SnapshotFiles.DATA.stream().sorted().toList(), hashes::get);
        if (!version.equals(expected)) {
            throw new SnapshotException("meta.tsv의 version이 파일 해시와 맞지 않습니다");
        }
        return new SnapshotMeta(version, dataDate(require(values, "data_date")), hashes);
    }

    String sha256Of(String file) {
        return sha256ByFile.get(file);
    }

    private static LocalDate dataDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new SnapshotException("meta.tsv의 data_date를 읽을 수 없습니다: " + value, e);
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isEmpty()) {
            throw new SnapshotException("meta.tsv에 " + key + "가 없습니다");
        }
        return value;
    }
}
