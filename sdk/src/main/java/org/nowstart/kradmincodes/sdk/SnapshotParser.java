package org.nowstart.kradmincodes.sdk;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.model.Validity;

/**
 * 스냅샷 TSV 네 개를 {@link Snapshot}으로 바꾼다. 한 행이라도 형식이 틀리면 스냅샷 전체를 거부한다.
 *
 * <p>12만 행에서 같은 시도명·날짜·유효기간이 수없이 반복되므로, 같은 값은 한 객체를 공유하게 해서 힙을 줄인다.
 */
final class SnapshotParser {

    private SnapshotParser() {
    }

    static Snapshot parse(SnapshotMeta meta, Map<String, String> files) {
        var pool = new Pool();
        List<AdminDong> admins = map(files, SnapshotFiles.ADMIN, SnapshotFiles.ADMIN_HEADER, c -> new AdminDong(
                pool.text(c.get(0)), pool.text(c.get(1)), pool.text(c.get(2)), pool.text(c.get(3)),
                pool.validity(c.get(4), c.get(5))));
        List<LegalDong> legals = map(files, SnapshotFiles.LEGAL, SnapshotFiles.LEGAL_HEADER, c -> new LegalDong(
                pool.text(c.get(0)), pool.text(c.get(1)), pool.text(c.get(2)), pool.text(c.get(3)), pool.text(c.get(4)),
                pool.validity(c.get(5), c.get(6))));
        List<AdminLegalLink> links = map(files, SnapshotFiles.LINK, SnapshotFiles.LINK_HEADER, c -> new AdminLegalLink(
                pool.text(c.get(0)), pool.text(c.get(1)), pool.validity(c.get(2), c.get(3))));
        List<Succession> successions = map(files, SnapshotFiles.SUCCESSION, SnapshotFiles.SUCCESSION_HEADER,
                c -> new Succession(pool.text(c.get(0)), pool.text(c.get(1)), pool.date(c.get(2)),
                        SuccessionMethod.valueOf(c.get(3))));
        return new Snapshot(meta, admins, legals, links, successions);
    }

    private static <T> List<T> map(
            Map<String, String> files, String file, List<String> header, Function<List<String>, T> mapper) {
        String content = files.get(file);
        if (content == null) {
            throw new SnapshotException(file + " 파일이 없습니다");
        }
        List<List<String>> rows = SnapshotFiles.rows(file, content, header);
        List<T> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            try {
                result.add(mapper.apply(rows.get(i)));
            } catch (IllegalArgumentException | NullPointerException | DateTimeException e) {
                throw new SnapshotException(file + "의 " + (i + 1) + "번째 데이터 행을 읽을 수 없습니다: " + e.getMessage(), e);
            }
        }
        return result;
    }

    /** 파싱 한 번 동안만 쓰는 값 공유 풀. */
    private static final class Pool {
        private final Map<String, String> texts = new HashMap<>();
        private final Map<String, LocalDate> dates = new HashMap<>();
        private final Map<String, Validity> validities = new HashMap<>();

        String text(String value) {
            return texts.computeIfAbsent(value, Function.identity());
        }

        /** 빈 값은 null(말소되지 않음). 생성일자는 비어 있으면 안 되므로 {@link #validity}에서 걸린다. */
        LocalDate date(String value) {
            return value.isEmpty() ? null : dates.computeIfAbsent(value, LocalDate::parse);
        }

        Validity validity(String from, String to) {
            return validities.computeIfAbsent(from + "|" + to, key -> new Validity(date(from), date(to)));
        }
    }
}
