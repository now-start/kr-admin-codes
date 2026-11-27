package org.nowstart.kradmincodes.pipeline.parse;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/**
 * 바이트 기준 고정폭 텍스트를 읽는다.
 *
 * <p>열 경계는 헤더 행에서 각 라벨이 시작하는 바이트 위치다. cp949 한글은 2바이트라 문자 단위로 자르면 열이 밀리므로,
 * 반드시 바이트로 자른 뒤 디코드한다. cp949의 멀티바이트 문자에는 0x20이 나오지 않아 공백으로 라벨 경계를 찾을 수 있다.
 */
public final class FixedWidthParser {

    private static final byte SPACE = ' ';
    private static final byte LF = '\n';
    private static final byte CR = '\r';

    private FixedWidthParser() {
    }

    public static FixedWidthTable parse(byte[] content, Charset charset) {
        List<byte[]> lines = splitLines(content);
        if (lines.isEmpty() || isBlank(lines.get(0))) {
            throw new PipelineException("헤더 행이 없습니다");
        }
        // 잘못된 바이트를 U+FFFD로 바꾸지 않고 멈춘다. 이름이 깨진 채로 통과하면 변환 도출이 조용히 틀어진다.
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        byte[] headerLine = lines.get(0);
        int[] starts = columnStarts(headerLine);
        List<String> header = slice(headerLine, 1, starts, decoder);

        List<List<String>> rows = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            byte[] line = lines.get(i);
            if (!isBlank(line)) {
                rows.add(slice(line, i + 1, starts, decoder));
            }
        }
        return new FixedWidthTable(header, rows);
    }

    private static List<byte[]> splitLines(byte[] content) {
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= content.length; i++) {
            boolean atEnd = i == content.length;
            if (!atEnd && content[i] != LF) {
                continue;
            }
            int end = (i > start && content[i - 1] == CR) ? i - 1 : i;
            if (!atEnd || end > start) {
                lines.add(Arrays.copyOfRange(content, start, end));
            }
            start = i + 1;
        }
        return lines;
    }

    private static int[] columnStarts(byte[] header) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < header.length; i++) {
            boolean labelStarts = header[i] != SPACE && (i == 0 || header[i - 1] == SPACE);
            if (labelStarts) {
                starts.add(i);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<String> slice(byte[] line, int lineNumber, int[] starts, CharsetDecoder decoder) {
        List<String> cells = new ArrayList<>(starts.length);
        for (int k = 0; k < starts.length; k++) {
            int from = starts[k];
            int to = k + 1 < starts.length ? starts[k + 1] : line.length;
            cells.add(from >= line.length ? "" : decode(line, lineNumber, from, Math.min(to, line.length), decoder));
        }
        return cells;
    }

    private static String decode(byte[] line, int lineNumber, int from, int to, CharsetDecoder decoder) {
        try {
            return decoder.decode(ByteBuffer.wrap(line, from, to - from)).toString().strip();
        } catch (CharacterCodingException e) {
            throw new PipelineException(lineNumber + "번째 줄의 " + from + "~" + to + "바이트를 "
                    + decoder.charset() + "로 읽을 수 없습니다", e);
        }
    }

    private static boolean isBlank(byte[] line) {
        for (byte b : line) {
            if (b != SPACE) {
                return false;
            }
        }
        return true;
    }
}
