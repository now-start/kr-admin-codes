package org.nowstart.kradmincodes.model;

import java.util.Objects;

/** 행정동이 관할하는 법정동 연결 한 행. 원천 {@code KIKmix}에 대응한다. 이름은 코드로 조인해서 얻는다. */
public record AdminLegalLink(String adminCode, String legalCode, Validity validity) {

    public AdminLegalLink {
        Codes.requireCode(adminCode);
        Codes.requireCode(legalCode);
        Objects.requireNonNull(validity, "validity");
    }
}
