package org.nowstart.kradmincodes.model;

import java.util.Objects;

/** 법정동 코드 한 행. 원천 {@code KIKcd_B}에 대응한다. 읍·면 아래의 리는 {@code ri}에 들어간다. */
public record LegalDong(
        String code, String sido, String sigungu, String eupmyeondong, String ri, Validity validity) {

    public LegalDong {
        Codes.requireCode(code);
        Objects.requireNonNull(sido, "sido");
        Objects.requireNonNull(sigungu, "sigungu");
        Objects.requireNonNull(eupmyeondong, "eupmyeondong");
        Objects.requireNonNull(ri, "ri");
        Objects.requireNonNull(validity, "validity");
    }

    public Level level() {
        return Level.of(code);
    }
}
