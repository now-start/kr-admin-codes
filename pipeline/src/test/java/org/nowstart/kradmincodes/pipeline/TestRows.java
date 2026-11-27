package org.nowstart.kradmincodes.pipeline;

import java.time.LocalDate;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Validity;

/** 테스트용 합성 행. 날짜는 ISO 문자열, 말소일이 없으면 null. */
public final class TestRows {

    private TestRows() {
    }

    public static AdminDong admin(String code, String sido, String sigungu, String dong, String from, String to) {
        return new AdminDong(code, sido, sigungu, dong, validity(from, to));
    }

    public static LegalDong legal(String code, String from, String to) {
        return new LegalDong(code, "시도", "시군구", "읍면동", "", validity(from, to));
    }

    public static AdminLegalLink link(String adminCode, String legalCode, String from, String to) {
        return new AdminLegalLink(adminCode, legalCode, validity(from, to));
    }

    public static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    private static Validity validity(String from, String to) {
        return new Validity(LocalDate.parse(from), date(to));
    }
}
