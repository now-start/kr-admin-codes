package org.nowstart.kradmincodes.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 유효기간 {@code [from, toExclusive)}. 원천의 생성일자·말소일자에 대응한다.
 *
 * <p>{@code toExclusive}가 null이면 아직 말소되지 않은 열린 구간이다. 원천에는 생성일자와 말소일자가 같거나
 * 말소일자가 더 이른 행이 있는데, 이런 구간은 비어 있고 어떤 날짜에도 유효하지 않다.
 *
 * @param from 생성일자 (포함)
 * @param toExclusive 말소일자 (제외), 없으면 null
 */
public record Validity(LocalDate from, LocalDate toExclusive) {

    public Validity {
        Objects.requireNonNull(from, "from");
    }

    public static Validity openFrom(LocalDate from) {
        return new Validity(from, null);
    }

    /** 0일이거나 역전된 구간이면 true. */
    public boolean isEmpty() {
        return toExclusive != null && !toExclusive.isAfter(from);
    }

    public boolean isValidOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(from) && (toExclusive == null || date.isBefore(toExclusive));
    }

    /** {@code date} 이후 어느 날이라도 유효하면 true. */
    public boolean isValidOnOrAfter(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !isEmpty() && (toExclusive == null || toExclusive.isAfter(date));
    }
}
