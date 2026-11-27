package org.nowstart.kradmincodes.pipeline.parse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * 원천과 같은 형식(cp949, 바이트 기준 고정폭, LF)의 합성 데이터를 만든다. 실제 원천 행은 쓰지 않는다.
 */
final class FixedWidthFixture {

    static final Charset CP949 = Charset.forName("MS949");

    static final List<String> ADMIN_LABELS = List.of("행정동코드", "시도명", "시군구명", "읍면동명", "생성일자", "말소일자");
    static final int[] ADMIN_WIDTHS = {11, 31, 31, 31, 9, 9};

    static final List<String> LEGAL_LABELS =
            List.of("법정동코드", "시도명", "시군구명", "읍면동명", "동리명", "생성일자", "말소일자");
    static final int[] LEGAL_WIDTHS = {11, 31, 31, 31, 31, 9, 9};

    static final List<String> LINK_LABELS =
            List.of("행정동코드", "시도명", "시군구명", "읍면동명", "법정동코드", "동리명", "생성일자", "말소일자");
    static final int[] LINK_WIDTHS = {11, 31, 31, 31, 11, 31, 9, 9};

    private FixedWidthFixture() {
    }

    static byte[] build(List<String> labels, int[] widths, List<List<String>> rows) {
        var out = new ByteArrayOutputStream();
        writeLine(out, labels, widths);
        for (List<String> row : rows) {
            writeLine(out, row, widths);
        }
        return out.toByteArray();
    }

    static byte[] admin(List<List<String>> rows) {
        return build(ADMIN_LABELS, ADMIN_WIDTHS, rows);
    }

    static byte[] legal(List<List<String>> rows) {
        return build(LEGAL_LABELS, LEGAL_WIDTHS, rows);
    }

    static byte[] link(List<List<String>> rows) {
        return build(LINK_LABELS, LINK_WIDTHS, rows);
    }

    private static void writeLine(ByteArrayOutputStream out, List<String> cells, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            byte[] cell = i < cells.size() ? cells.get(i).getBytes(CP949) : new byte[0];
            if (cell.length >= widths[i]) {
                throw new IllegalArgumentException("셀이 열 너비보다 깁니다: " + cells.get(i));
            }
            byte[] padded = Arrays.copyOf(cell, widths[i]);
            Arrays.fill(padded, cell.length, widths[i], (byte) ' ');
            out.writeBytes(padded);
        }
        out.write('\n');
    }
}
