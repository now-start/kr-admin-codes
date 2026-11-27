package org.nowstart.kradmincodes.pipeline.parse;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 행안부 {@code jscodeYYYYMMDD(말소코드포함).zip} 한 개를 읽은 결과.
 *
 * @param dataDate 파일 이름에 적힌 데이터 기준일
 * @param admin {@code KIKcd_H} 행정동
 * @param legal {@code KIKcd_B} 법정동
 * @param link {@code KIKmix} 행정동↔법정동
 */
public record JscodeArchive(LocalDate dataDate, FixedWidthTable admin, FixedWidthTable legal, FixedWidthTable link) {

    public JscodeArchive {
        Objects.requireNonNull(dataDate, "dataDate");
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(legal, "legal");
        Objects.requireNonNull(link, "link");
    }
}
