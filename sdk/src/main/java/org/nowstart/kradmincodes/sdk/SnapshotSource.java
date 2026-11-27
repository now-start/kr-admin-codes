package org.nowstart.kradmincodes.sdk;

import java.io.IOException;
import java.time.Duration;

/** 스냅샷 파일을 가져오는 곳: jar 내장 또는 원격 주소. */
interface SnapshotSource {

    /** 로그와 {@link SnapshotInfo#origin()}에 쓰는 이름. */
    String name();

    /** @param timeout 이 요청에 쓸 수 있는 남은 시간 (본문까지 포함) */
    byte[] fetch(String file, Duration timeout) throws IOException;
}
