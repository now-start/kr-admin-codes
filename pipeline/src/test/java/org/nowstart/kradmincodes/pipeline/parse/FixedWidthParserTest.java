package org.nowstart.kradmincodes.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nowstart.kradmincodes.pipeline.parse.FixedWidthFixture.CP949;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.pipeline.PipelineException;

class FixedWidthParserTest {

    @Test
    void slicesColumnsByBytePositionsSoKoreanNamesDoNotShiftDates() {
        byte[] content = FixedWidthFixture.admin(List.of(
                List.of("1111051500", "서울특별시", "종로구", "청운효자동", "20081101", ""),
                List.of("1111051000", "서울특별시", "종로구", "청운동", "19880423", "20081101")));

        FixedWidthTable table = FixedWidthParser.parse(content, CP949);

        assertThat(table.header()).containsExactlyElementsOf(FixedWidthFixture.ADMIN_LABELS);
        assertThat(table.rows()).containsExactly(
                List.of("1111051500", "서울특별시", "종로구", "청운효자동", "20081101", ""),
                List.of("1111051000", "서울특별시", "종로구", "청운동", "19880423", "20081101"));
    }

    @Test
    void keepsSpacesInsideACell() {
        byte[] content = FixedWidthFixture.admin(List.of(
                List.of("4111100000", "경기도", "수원시 장안구", "", "19930301", "")));

        FixedWidthTable table = FixedWidthParser.parse(content, CP949);

        assertThat(table.rows().get(0).get(2)).isEqualTo("수원시 장안구");
    }

    @Test
    void fillsMissingTrailingColumnsAndSkipsBlankLines() {
        byte[] header = FixedWidthFixture.admin(List.of());
        byte[] shortRow = "1100000000 \n\n   \n".getBytes(CP949);
        byte[] content = new byte[header.length + shortRow.length];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(shortRow, 0, content, header.length, shortRow.length);

        FixedWidthTable table = FixedWidthParser.parse(content, CP949);

        assertThat(table.rows()).containsExactly(List.of("1100000000", "", "", "", "", ""));
    }

    @Test
    void acceptsCrlfLineEndings() {
        // "코드 " = 5바이트이므로 둘째 열은 5바이트 위치에서 시작한다
        byte[] content = "코드 이름\r\n11   서울\r\n".getBytes(CP949);

        FixedWidthTable table = FixedWidthParser.parse(content, CP949);

        assertThat(table.header()).containsExactly("코드", "이름");
        assertThat(table.rows()).containsExactly(List.of("11", "서울"));
    }

    @Test
    void rejectsBytesThatAreNotValidCp949InsteadOfReplacingThem() {
        byte[] header = "코드 이름\n".getBytes(CP949);
        byte[] row = {'1', '1', ' ', ' ', ' ', (byte) 0xFF, (byte) 0xFF, '\n'};
        byte[] content = new byte[header.length + row.length];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(row, 0, content, header.length, row.length);

        assertThatThrownBy(() -> FixedWidthParser.parse(content, CP949))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("2번째 줄");
    }

    @Test
    void headerOnlyMeansNoRows() {
        FixedWidthTable table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of()), CP949);

        assertThat(table.rows()).isEmpty();
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> FixedWidthParser.parse(new byte[0], CP949))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("헤더");
    }

    @Test
    void findsColumnIndexByLabel() {
        FixedWidthTable table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of()), CP949);

        assertThat(table.columnIndex("생성일자")).isEqualTo(4);
        assertThatThrownBy(() -> table.columnIndex("없는열"))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("없는열");
    }
}
