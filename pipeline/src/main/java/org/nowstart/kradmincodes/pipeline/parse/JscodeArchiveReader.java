package org.nowstart.kradmincodes.pipeline.parse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/**
 * 행안부 jscode zip에서 {@code KIKcd_H}·{@code KIKcd_B}·{@code KIKmix} 텍스트 파일을 꺼내 읽는다. 같은 내용의
 * {@code .xlsx}는 무시한다.
 */
public final class JscodeArchiveReader {

    /** zip 항목 하나의 최대 크기. 원천은 16MB 안팎이라 넉넉하게 잡고, 압축 폭탄은 막는다. */
    public static final long DEFAULT_MAX_ENTRY_BYTES = 100L * 1024 * 1024;

    /** 항목 이름과 본문 모두 cp949. 항목 이름에 UTF-8 플래그가 없다. */
    private static final Charset CP949 = Charset.forName("MS949");

    private static final String ADMIN = "KIKcd_H";
    private static final String LEGAL = "KIKcd_B";
    private static final String LINK = "KIKmix";
    private static final List<String> KINDS = List.of(ADMIN, LEGAL, LINK);
    private static final Pattern ENTRY_NAME = Pattern.compile("^(KIKcd_H|KIKcd_B|KIKmix)\\.(\\d{8}).*$");
    private static final String XLSX = ".xlsx";

    private final long maxEntryBytes;

    public JscodeArchiveReader() {
        this(DEFAULT_MAX_ENTRY_BYTES);
    }

    public JscodeArchiveReader(long maxEntryBytes) {
        if (maxEntryBytes <= 0) {
            throw new IllegalArgumentException("maxEntryBytes는 양수여야 합니다: " + maxEntryBytes);
        }
        this.maxEntryBytes = maxEntryBytes;
    }

    public JscodeArchive read(byte[] zipBytes) {
        Map<String, byte[]> contents = new HashMap<>();
        Map<String, String> dates = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), CP949)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                collect(zip, entry, contents, dates);
            }
        } catch (IOException e) {
            throw new PipelineException("zip을 읽을 수 없습니다", e);
        }
        for (String kind : KINDS) {
            if (!contents.containsKey(kind)) {
                throw new PipelineException("zip에 " + kind + " 파일이 없습니다 (찾은 파일: " + dates.keySet() + ")");
            }
        }
        return new JscodeArchive(
                dataDate(dates),
                FixedWidthParser.parse(contents.get(ADMIN), CP949),
                FixedWidthParser.parse(contents.get(LEGAL), CP949),
                FixedWidthParser.parse(contents.get(LINK), CP949));
    }

    private void collect(InputStream zip, ZipEntry entry, Map<String, byte[]> contents, Map<String, String> dates)
            throws IOException {
        String name = baseName(entry.getName());
        if (entry.isDirectory() || name.endsWith(XLSX)) {
            return;
        }
        Matcher matcher = ENTRY_NAME.matcher(name);
        if (!matcher.matches()) {
            return;
        }
        String kind = matcher.group(1);
        if (contents.containsKey(kind)) {
            throw new PipelineException("zip에 " + kind + " 파일이 두 개 이상 있습니다: " + name);
        }
        contents.put(kind, readLimited(zip, name));
        dates.put(kind, matcher.group(2));
    }

    private byte[] readLimited(InputStream in, String name) throws IOException {
        int limit = (int) Math.min(Integer.MAX_VALUE - 8L, maxEntryBytes + 1);
        byte[] data = in.readNBytes(limit);
        if (data.length > maxEntryBytes) {
            throw new PipelineException("zip 항목 크기가 상한(" + maxEntryBytes + "바이트)을 넘습니다: " + name);
        }
        return data;
    }

    private static LocalDate dataDate(Map<String, String> dates) {
        if (new HashSet<>(dates.values()).size() != 1) {
            throw new PipelineException("파일마다 기준일이 다릅니다: " + dates);
        }
        String date = dates.values().iterator().next();
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new PipelineException("파일 이름의 기준일을 읽을 수 없습니다: " + date, e);
        }
    }

    private static String baseName(String entryName) {
        return entryName.substring(entryName.lastIndexOf('/') + 1);
    }
}
