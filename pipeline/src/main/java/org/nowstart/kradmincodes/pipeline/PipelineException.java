package org.nowstart.kradmincodes.pipeline;

/** 파이프라인이 더 진행할 수 없는 오류. {@code PipelineApp}에서만 잡아서 종료 코드 1로 끝낸다. */
public class PipelineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
