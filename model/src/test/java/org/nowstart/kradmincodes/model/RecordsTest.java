package org.nowstart.kradmincodes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecordsTest {

    private static final Validity OPEN = Validity.openFrom(LocalDate.of(2023, 6, 11));

    @Test
    void adminDongExposesLevelFromCode() {
        var sido = new AdminDong("5100000000", "강원특별자치도", "", "", OPEN);
        var dong = new AdminDong("5111051000", "강원특별자치도", "춘천시", "교동", OPEN);

        assertThat(sido.level()).isEqualTo(Level.SIDO);
        assertThat(dong.level()).isEqualTo(Level.EUPMYEONDONG);
    }

    @Test
    void adminDongRejectsBadCodeAndNullFields() {
        assertThatThrownBy(() -> new AdminDong("51", "강원특별자치도", "", "", OPEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminDong("5100000000", null, "", "", OPEN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdminDong("5100000000", "강원특별자치도", "", "", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void legalDongKeepsRiName() {
        var ri = new LegalDong("5113025021", "강원특별자치도", "원주시", "문막읍", "건등리", OPEN);

        assertThat(ri.level()).isEqualTo(Level.RI);
        assertThat(ri.ri()).isEqualTo("건등리");
        assertThatThrownBy(() -> new LegalDong("5113025021", "강원특별자치도", "원주시", "문막읍", null, OPEN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void linkRequiresBothCodes() {
        var link = new AdminLegalLink("5111051000", "5111010100", OPEN);

        assertThat(link.adminCode()).isEqualTo("5111051000");
        assertThatThrownBy(() -> new AdminLegalLink("5111051000", "bad", OPEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successionMustPointToDifferentCode() {
        var date = LocalDate.of(2023, 6, 11);
        var succession = new Succession("4200000000", "5100000000", date, SuccessionMethod.NAME_MATCH);

        assertThat(succession.method()).isEqualTo(SuccessionMethod.NAME_MATCH);
        assertThatThrownBy(() -> new Succession("4200000000", "4200000000", date, SuccessionMethod.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Succession("4200000000", "5100000000", null, SuccessionMethod.MANUAL))
                .isInstanceOf(NullPointerException.class);
    }
}
