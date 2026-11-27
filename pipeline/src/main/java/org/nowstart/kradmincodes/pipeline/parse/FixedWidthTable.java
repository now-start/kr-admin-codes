package org.nowstart.kradmincodes.pipeline.parse;

import java.util.List;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** 고정폭 파일을 읽은 결과. 헤더 라벨과, 헤더와 같은 길이의 행들. */
public record FixedWidthTable(List<String> header, List<List<String>> rows) {

    public FixedWidthTable {
        header = List.copyOf(header);
        rows = rows.stream().map(List::copyOf).toList();
    }

    /** 헤더에서 라벨의 위치를 찾는다. 없으면 원천 형식이 바뀐 것이므로 멈춘다. */
    public int columnIndex(String label) {
        int index = header.indexOf(label);
        if (index < 0) {
            throw new PipelineException("열을 찾을 수 없습니다: " + label + " (헤더: " + header + ")");
        }
        return index;
    }
}
