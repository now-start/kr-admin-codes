package org.nowstart.kradmincodes.pipeline.parse;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Validity;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** 고정폭 테이블 행을 model record로 바꾼다. 열은 위치가 아니라 헤더 라벨로 찾는다. */
public final class RecordMapper {

    private static final DateTimeFormatter SOURCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String ADMIN_CODE = "행정동코드";
    private static final String LEGAL_CODE = "법정동코드";
    private static final String SIDO = "시도명";
    private static final String SIGUNGU = "시군구명";
    private static final String EUPMYEONDONG = "읍면동명";
    private static final String RI = "동리명";
    private static final String CREATED = "생성일자";
    private static final String ABOLISHED = "말소일자";

    private RecordMapper() {
    }

    public static List<AdminDong> adminDongs(FixedWidthTable table) {
        int code = table.columnIndex(ADMIN_CODE);
        int sido = table.columnIndex(SIDO);
        int sigungu = table.columnIndex(SIGUNGU);
        int dong = table.columnIndex(EUPMYEONDONG);
        int created = table.columnIndex(CREATED);
        int abolished = table.columnIndex(ABOLISHED);
        return mapRows(table, row -> new AdminDong(
                row.get(code), row.get(sido), row.get(sigungu), row.get(dong),
                validity(row.get(created), row.get(abolished))));
    }

    public static List<LegalDong> legalDongs(FixedWidthTable table) {
        int code = table.columnIndex(LEGAL_CODE);
        int sido = table.columnIndex(SIDO);
        int sigungu = table.columnIndex(SIGUNGU);
        int eupmyeondong = table.columnIndex(EUPMYEONDONG);
        int ri = table.columnIndex(RI);
        int created = table.columnIndex(CREATED);
        int abolished = table.columnIndex(ABOLISHED);
        return mapRows(table, row -> new LegalDong(
                row.get(code), row.get(sido), row.get(sigungu), row.get(eupmyeondong), row.get(ri),
                validity(row.get(created), row.get(abolished))));
    }

    public static List<AdminLegalLink> links(FixedWidthTable table) {
        int adminCode = table.columnIndex(ADMIN_CODE);
        int legalCode = table.columnIndex(LEGAL_CODE);
        int created = table.columnIndex(CREATED);
        int abolished = table.columnIndex(ABOLISHED);
        return mapRows(table, row -> new AdminLegalLink(
                row.get(adminCode), row.get(legalCode), validity(row.get(created), row.get(abolished))));
    }

    private static <T> List<T> mapRows(FixedWidthTable table, Function<List<String>, T> mapper) {
        List<List<String>> rows = table.rows();
        List<T> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            try {
                result.add(mapper.apply(rows.get(i)));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new PipelineException((i + 1) + "행을 변환할 수 없습니다: " + e.getMessage(), e);
            }
        }
        return List.copyOf(result);
    }

    private static Validity validity(String created, String abolished) {
        if (created.isEmpty()) {
            throw new IllegalArgumentException(CREATED + "가 비어 있습니다");
        }
        LocalDate to = abolished.isEmpty() ? null : LocalDate.parse(abolished, SOURCE_DATE);
        return new Validity(LocalDate.parse(created, SOURCE_DATE), to);
    }
}
