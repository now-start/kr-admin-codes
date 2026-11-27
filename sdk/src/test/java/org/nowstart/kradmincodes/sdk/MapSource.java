package org.nowstart.kradmincodes.sdk;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** 메모리에 든 가짜 스냅샷 소스. 내용을 바꾸거나 장애를 흉내 낼 수 있다. */
final class MapSource implements SnapshotSource {

    private final String name;
    private volatile Map<String, byte[]> files;
    private volatile boolean down;

    MapSource(String name, Map<String, byte[]> files) {
        this.name = name;
        this.files = Map.copyOf(files);
    }

    void replace(Map<String, byte[]> files) {
        this.files = Map.copyOf(files);
    }

    void down(boolean down) {
        this.down = down;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public byte[] fetch(String file, Duration timeout) throws IOException {
        if (down) {
            throw new IOException(name + " 연결 실패");
        }
        byte[] bytes = files.get(file);
        if (bytes == null) {
            throw new IOException(name + "에 " + file + "이(가) 없습니다");
        }
        return bytes.clone();
    }
}
