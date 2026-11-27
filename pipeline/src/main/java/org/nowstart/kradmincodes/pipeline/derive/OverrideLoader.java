package org.nowstart.kradmincodes.pipeline.derive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.nowstart.kradmincodes.pipeline.PipelineException;
import org.nowstart.kradmincodes.pipeline.write.Tsv;

/** {@code overrides/successions.tsv}를 읽는다. 열: from_code, to_code, effective_date, note. */
public final class OverrideLoader {

    public static final List<String> HEADER = List.of("from_code", "to_code", "effective_date", "note");

    private OverrideLoader() {
    }

    /** 파일이 없으면 오류다. 없는 채로 진행하면 수동 변환이 소리 없이 빠진다. */
    public static List<SuccessionOverride> load(Path file) {
        if (!Files.exists(file)) {
            throw new PipelineException("overrides 파일이 없습니다: " + file
                    + " (수동 변환이 없으면 헤더만 있는 파일을 두세요: " + String.join("\t", HEADER) + ")");
        }
        List<SuccessionOverride> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Tsv.Row row : Tsv.read(file, HEADER)) {
            SuccessionOverride override = parse(file, row);
            String pair = override.fromCode() + "→" + override.toCode();
            if (!seen.add(pair)) {
                throw new PipelineException(file + " " + row.lineNumber() + "번째 줄: 중복된 항목입니다 " + pair);
            }
            result.add(override);
        }
        return List.copyOf(result);
    }

    private static SuccessionOverride parse(Path file, Tsv.Row row) {
        List<String> cells = row.cells();
        try {
            return new SuccessionOverride(cells.get(0), cells.get(1), LocalDate.parse(cells.get(2)), cells.get(3));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new PipelineException(file + " " + row.lineNumber() + "번째 줄: " + e.getMessage(), e);
        }
    }
}
