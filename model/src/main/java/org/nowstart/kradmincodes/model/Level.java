package org.nowstart.kradmincodes.model;

/**
 * 코드가 가리키는 행정구역 단위. 코드 구조(시도 2 + 시군구 3 + 읍면동 3 + 리 2)로 판별한다.
 *
 * <p>출장소처럼 이름 열과 단위가 어긋나는 행도 있어서, 이름이 아니라 코드로만 판단한다.
 */
public enum Level {
    SIDO,
    SIGUNGU,
    EUPMYEONDONG,
    RI;

    private static final String SIDO_SUFFIX = "00000000";
    private static final String SIGUNGU_SUFFIX = "00000";
    private static final String EUPMYEONDONG_SUFFIX = "00";

    public static Level of(String code) {
        Codes.requireCode(code);
        if (code.endsWith(SIDO_SUFFIX)) {
            return SIDO;
        }
        if (code.endsWith(SIGUNGU_SUFFIX)) {
            return SIGUNGU;
        }
        if (code.endsWith(EUPMYEONDONG_SUFFIX)) {
            return EUPMYEONDONG;
        }
        return RI;
    }
}
