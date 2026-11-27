package org.nowstart.kradmincodes.model;

/** 옛 코드 → 새 코드 연결을 어떻게 얻었는지. */
public enum SuccessionMethod {
    /** 말소일과 같은 날 생긴 같은 이름의 코드로 자동 도출. */
    NAME_MATCH,
    /** 변경내역 문서를 근거로 사람이 입력. */
    MANUAL
}
