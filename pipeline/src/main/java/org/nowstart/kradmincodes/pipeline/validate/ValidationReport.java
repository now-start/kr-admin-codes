package org.nowstart.kradmincodes.pipeline.validate;

import java.util.List;

/** 검증 결과. 오류가 하나라도 있으면 데이터를 쓰지 않는다. 경고는 PR 리뷰 때 사람이 본다. */
public record ValidationReport(List<String> errors, List<String> warnings) {

    public ValidationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
