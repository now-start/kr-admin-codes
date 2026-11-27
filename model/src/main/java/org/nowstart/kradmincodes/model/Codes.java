package org.nowstart.kradmincodes.model;

/** 행정동·법정동 코드(10자리 숫자) 검증 유틸. */
public final class Codes {

    /** 코드 길이. */
    public static final int LENGTH = 10;

    private Codes() {
    }

    /**
     * 코드가 ASCII 숫자 10자리인지 확인한다.
     *
     * @return 검증을 통과한 코드 그대로
     * @throws IllegalArgumentException 형식이 맞지 않을 때
     */
    public static String requireCode(String code) {
        if (code == null || code.length() != LENGTH || !isAsciiDigits(code)) {
            throw new IllegalArgumentException("코드는 10자리 숫자여야 합니다: " + code);
        }
        return code;
    }

    private static boolean isAsciiDigits(String value) {
        return value.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
