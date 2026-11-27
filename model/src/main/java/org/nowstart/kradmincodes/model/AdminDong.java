package org.nowstart.kradmincodes.model;

import java.util.Objects;

/**
 * 행정동(행정기관) 코드 한 행. 원천 {@code KIKcd_H}에 대응한다.
 *
 * <p>상위 단위 행은 하위 이름이 빈 문자열이다 (예: 시도 행은 sigungu·dong이 "").
 */
public record AdminDong(String code, String sido, String sigungu, String dong, Validity validity) {

    public AdminDong {
        Codes.requireCode(code);
        Objects.requireNonNull(sido, "sido");
        Objects.requireNonNull(sigungu, "sigungu");
        Objects.requireNonNull(dong, "dong");
        Objects.requireNonNull(validity, "validity");
    }

    public Level level() {
        return Level.of(code);
    }
}
