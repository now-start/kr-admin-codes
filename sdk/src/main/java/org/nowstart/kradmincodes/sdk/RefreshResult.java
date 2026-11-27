package org.nowstart.kradmincodes.sdk;

/** {@link AdminCodes#refreshNow()} 결과. */
public enum RefreshResult {
    /** 새 스냅샷으로 바꿨다. */
    UPDATED,
    /** 원격과 같은 버전이라 바꿀 것이 없다. */
    UNCHANGED,
    /** 모든 원격 소스에서 실패했다. 가진 스냅샷을 그대로 쓴다(원인은 경고 로그). */
    FAILED
}
