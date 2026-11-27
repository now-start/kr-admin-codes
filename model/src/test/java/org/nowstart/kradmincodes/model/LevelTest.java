package org.nowstart.kradmincodes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LevelTest {

    @ParameterizedTest
    @CsvSource({
        "1100000000, SIDO",
        "1111000000, SIGUNGU",
        "1111051500, EUPMYEONDONG",
        "1111010100, EUPMYEONDONG",
        "4182025021, RI",
    })
    void detectsLevelFromCodeStructure(String code, Level expected) {
        assertThat(Level.of(code)).isEqualTo(expected);
    }

    @Test
    void rejectsMalformedCode() {
        assertThatThrownBy(() -> Level.of("11000"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
