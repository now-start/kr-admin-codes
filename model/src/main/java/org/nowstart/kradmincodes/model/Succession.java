package org.nowstart.kradmincodes.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 옛 코드가 {@code effectiveDate}에 새 코드로 이어졌다는 사실. 하나의 옛 코드가 여러 새 코드로 나뉘면(1:N) 여러 행이
 * 된다.
 */
public record Succession(String fromCode, String toCode, LocalDate effectiveDate, SuccessionMethod method) {

    public Succession {
        Codes.requireCode(fromCode);
        Codes.requireCode(toCode);
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(method, "method");
        if (fromCode.equals(toCode)) {
            throw new IllegalArgumentException("옛 코드와 새 코드가 같습니다: " + fromCode);
        }
    }
}
