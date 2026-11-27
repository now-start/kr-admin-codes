package org.nowstart.kradmincodes.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/** 빌드할 때 jar에 넣은 스냅샷. 네트워크 없이 바로 쓸 수 있다. */
final class ClasspathSource implements SnapshotSource {

    static final String NAME = "bundled";
    private static final String BASE = "/org/nowstart/kradmincodes/sdk/snapshot/";

    @Override
    public String name() {
        return NAME;
    }

    /** 로컬 리소스라 {@code timeout}은 쓰지 않는다. */
    @Override
    public byte[] fetch(String file, Duration timeout) throws IOException {
        try (InputStream in = ClasspathSource.class.getResourceAsStream(BASE + file)) {
            if (in == null) {
                throw new IOException("jar에 내장 스냅샷 파일이 없습니다: " + BASE + file);
            }
            return in.readAllBytes();
        }
    }
}
