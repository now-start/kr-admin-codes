package org.nowstart.kradmincodes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CodesTest {

    @Test
    void acceptsTenDigitCode() {
        assertThat(Codes.requireCode("1111051500")).isEqualTo("1111051500");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"111105150", "11110515000", "11110515a0", "１１１１０５１５００"})
    void rejectsAnythingButTenAsciiDigits(String code) {
        assertThatThrownBy(() -> Codes.requireCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10자리");
    }
}
