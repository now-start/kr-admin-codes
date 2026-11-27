package org.nowstart.kradmincodes.pipeline.write;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.model.Validity;
import org.nowstart.kradmincodes.pipeline.Dataset;
import org.nowstart.kradmincodes.pipeline.PipelineException;
import org.nowstart.kradmincodes.pipeline.derive.Unresolved;

/** 이전에 쓴 {@code data/v1/}을 다시 읽는다. 새 데이터와 비교해 코드가 사라지지 않았는지 확인할 때 쓴다. */
public final class TsvReader {

    private TsvReader() {
    }

    /** 행정동 파일이 없으면 이전 데이터가 없는 것(첫 생성)으로 본다. */
    public static Optional<Dataset> read(Path dir) {
        if (!Files.exists(dir.resolve(DatasetLayout.ADMIN_FILE))) {
            return Optional.empty();
        }
        return Optional.of(new Dataset(
                map(dir.resolve(DatasetLayout.ADMIN_FILE), DatasetLayout.ADMIN_HEADER, c -> new AdminDong(
                        c.get(0), c.get(1), c.get(2), c.get(3), validity(c.get(4), c.get(5)))),
                map(dir.resolve(DatasetLayout.LEGAL_FILE), DatasetLayout.LEGAL_HEADER, c -> new LegalDong(
                        c.get(0), c.get(1), c.get(2), c.get(3), c.get(4), validity(c.get(5), c.get(6)))),
                map(dir.resolve(DatasetLayout.LINK_FILE), DatasetLayout.LINK_HEADER, c -> new AdminLegalLink(
                        c.get(0), c.get(1), validity(c.get(2), c.get(3)))),
                map(dir.resolve(DatasetLayout.SUCCESSION_FILE), DatasetLayout.SUCCESSION_HEADER, c -> new Succession(
                        c.get(0), c.get(1), LocalDate.parse(c.get(2)), SuccessionMethod.valueOf(c.get(3)))),
                map(dir.resolve(DatasetLayout.UNRESOLVED_FILE), DatasetLayout.UNRESOLVED_HEADER, c -> new Unresolved(
                        c.get(0), c.get(1), c.get(2), c.get(3), LocalDate.parse(c.get(4))))));
    }

    private static <T> List<T> map(Path file, List<String> header, Function<List<String>, T> mapper) {
        List<T> result = new ArrayList<>();
        for (Tsv.Row row : Tsv.read(file, header)) {
            try {
                result.add(mapper.apply(row.cells()));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new PipelineException(file + " " + row.lineNumber() + "번째 줄을 읽을 수 없습니다: "
                        + e.getMessage(), e);
            }
        }
        return List.copyOf(result);
    }

    private static Validity validity(String from, String to) {
        return new Validity(LocalDate.parse(from), Tsv.parseDate(to));
    }
}
