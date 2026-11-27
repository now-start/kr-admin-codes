package org.nowstart.kradmincodes.pipeline.derive;

import java.time.LocalDate;
import java.util.Objects;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.Codes;

/** 자동으로 후계 코드를 찾지 못한 말소 행정동. {@code overrides/successions.tsv}로 채워야 할 작업 목록이다. */
public record Unresolved(String code, String sido, String sigungu, String dong, LocalDate abolishedOn) {

    public Unresolved {
        Codes.requireCode(code);
        Objects.requireNonNull(sido, "sido");
        Objects.requireNonNull(sigungu, "sigungu");
        Objects.requireNonNull(dong, "dong");
        Objects.requireNonNull(abolishedOn, "abolishedOn");
    }

    static Unresolved of(AdminDong row) {
        return new Unresolved(row.code(), row.sido(), row.sigungu(), row.dong(), row.validity().toExclusive());
    }
}
