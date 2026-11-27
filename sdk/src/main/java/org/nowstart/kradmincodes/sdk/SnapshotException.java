package org.nowstart.kradmincodes.sdk;

/** 스냅샷이 깨졌거나 형식이 맞지 않음. 내장 스냅샷이면 {@link AdminCodes.Builder#build()}에서, 원격이면 갱신 거부로 드러난다. */
public class SnapshotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SnapshotException(String message) {
        super(message);
    }

    public SnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
