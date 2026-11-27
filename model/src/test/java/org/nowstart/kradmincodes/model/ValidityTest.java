package org.nowstart.kradmincodes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValidityTest {

    private static final LocalDate FROM = LocalDate.of(2023, 6, 11);
    private static final LocalDate TO = LocalDate.of(2026, 7, 1);

    @Test
    void isHalfOpenInterval() {
        var validity = new Validity(FROM, TO);

        assertThat(validity.isValidOn(FROM.minusDays(1))).isFalse();
        assertThat(validity.isValidOn(FROM)).isTrue();
        assertThat(validity.isValidOn(TO.minusDays(1))).isTrue();
        assertThat(validity.isValidOn(TO)).isFalse();
    }

    @Test
    void openIntervalHasNoEnd() {
        var validity = Validity.openFrom(FROM);

        assertThat(validity.toExclusive()).isNull();
        assertThat(validity.isValidOn(LocalDate.of(2999, 1, 1))).isTrue();
        assertThat(validity.isEmpty()).isFalse();
    }

    @Test
    void sameDayOrReversedIntervalIsEmptyAndNeverValid() {
        var sameDay = new Validity(FROM, FROM);
        var reversed = new Validity(FROM, FROM.minusDays(3));

        assertThat(sameDay.isEmpty()).isTrue();
        assertThat(reversed.isEmpty()).isTrue();
        assertThat(sameDay.isValidOn(FROM)).isFalse();
        assertThat(reversed.isValidOn(FROM.minusDays(1))).isFalse();
    }

    @Test
    void validOnOrAfterChecksOverlapWithOpenEndedRange() {
        var closed = new Validity(FROM, TO);

        assertThat(closed.isValidOnOrAfter(TO.minusDays(1))).isTrue();
        assertThat(closed.isValidOnOrAfter(TO)).isFalse();
        assertThat(Validity.openFrom(FROM).isValidOnOrAfter(TO)).isTrue();
        assertThat(new Validity(FROM, FROM).isValidOnOrAfter(FROM.minusDays(10))).isFalse();
    }

    @Test
    void requiresStartDate() {
        assertThatThrownBy(() -> new Validity(null, TO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Validity(FROM, TO).isValidOn(null))
                .isInstanceOf(NullPointerException.class);
    }
}
