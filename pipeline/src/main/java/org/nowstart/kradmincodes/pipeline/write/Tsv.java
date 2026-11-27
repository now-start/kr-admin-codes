package org.nowstart.kradmincodes.pipeline.write;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** UTF-8, 탭 구분, 첫 줄 헤더인 TSV 읽기·쓰기 도우미. 날짜는 ISO, 없으면 빈 칸. */
public final class Tsv {

    private static final String SEPARATOR = "\t";

    private Tsv() {
    }

    /** 읽은 한 줄. {@code lineNumber}는 파일 기준 1부터(헤더가 1). */
    public record Row(int lineNumber, List<String> cells) {
        public Row {
            cells = List.copyOf(cells);
        }
    }

    public static String line(List<String> cells) {
        for (String cell : cells) {
            if (cell.indexOf('\t') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
                throw new PipelineException("TSV 값에 탭이나 줄바꿈이 들어 있습니다: " + cell);
            }
        }
        return String.join(SEPARATOR, cells);
    }

    /** 헤더가 정확히 같아야 하고, 모든 줄의 열 수가 헤더와 같아야 한다. 빈 줄은 건너뛴다. */
    public static List<Row> read(Path file, List<String> header) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PipelineException(file + "을(를) 읽을 수 없습니다", e);
        }
        if (lines.isEmpty() || !split(lines.get(0)).equals(header)) {
            throw new PipelineException(file + "의 헤더가 다릅니다. 기대: " + header + ", 실제: "
                    + (lines.isEmpty() ? "(빈 파일)" : lines.get(0)));
        }
        List<Row> rows = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isEmpty()) {
                continue;
            }
            int lineNumber = i + 1;
            List<String> cells = split(lines.get(i));
            if (cells.size() != header.size()) {
                throw new PipelineException(file + " " + lineNumber + "번째 줄의 열 수가 다릅니다: 기대 "
                        + header.size() + ", 실제 " + cells.size());
            }
            rows.add(new Row(lineNumber, cells));
        }
        return List.copyOf(rows);
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    public static LocalDate parseDate(String value) {
        return value.isEmpty() ? null : LocalDate.parse(value);
    }

    private static List<String> split(String line) {
        return List.of(line.split(SEPARATOR, -1));
    }
}
