package org.nowstart.kradmincodes.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nowstart.kradmincodes.pipeline.parse.FixedWidthFixture.CP949;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Validity;
import org.nowstart.kradmincodes.pipeline.PipelineException;

class RecordMapperTest {

    @Test
    void mapsAdminRowsWithOpenAndClosedValidity() {
        var table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of(
                List.of("4200000000", "강원도", "", "", "19880423", "20230611"),
                List.of("5100000000", "강원특별자치도", "", "", "20230611", ""))), CP949);

        List<AdminDong> rows = RecordMapper.adminDongs(table);

        assertThat(rows).containsExactly(
                new AdminDong("4200000000", "강원도", "", "",
                        new Validity(LocalDate.of(1988, 4, 23), LocalDate.of(2023, 6, 11))),
                new AdminDong("5100000000", "강원특별자치도", "", "",
                        Validity.openFrom(LocalDate.of(2023, 6, 11))));
    }

    @Test
    void mapsLegalRowsWithRi() {
        var table = FixedWidthParser.parse(FixedWidthFixture.legal(List.of(
                List.of("5113025021", "강원특별자치도", "원주시", "문막읍", "건등리", "20230611", ""))), CP949);

        List<LegalDong> rows = RecordMapper.legalDongs(table);

        assertThat(rows).singleElement().extracting(LegalDong::ri).isEqualTo("건등리");
    }

    @Test
    void mapsLinksUsingCodesOnly() {
        var table = FixedWidthParser.parse(FixedWidthFixture.link(List.of(
                List.of("5111051000", "강원특별자치도", "춘천시", "교동", "5111010100", "교동", "20230611", ""))),
                CP949);

        List<AdminLegalLink> rows = RecordMapper.links(table);

        assertThat(rows).containsExactly(new AdminLegalLink(
                "5111051000", "5111010100", Validity.openFrom(LocalDate.of(2023, 6, 11))));
    }

    @Test
    void reportsRowNumberForBadDate() {
        var table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of(
                List.of("1100000000", "서울특별시", "", "", "19880423", ""),
                List.of("1111000000", "서울특별시", "종로구", "", "1988-04", ""))), CP949);

        assertThatThrownBy(() -> RecordMapper.adminDongs(table))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("2행")
                .hasMessageContaining("1988-04");
    }

    @Test
    void reportsRowNumberForBadCode() {
        var table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of(
                List.of("11000", "서울특별시", "", "", "19880423", ""))), CP949);

        assertThatThrownBy(() -> RecordMapper.adminDongs(table))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("1행");
    }

    @Test
    void requiresCreationDate() {
        var table = FixedWidthParser.parse(FixedWidthFixture.admin(List.of(
                List.of("1100000000", "서울특별시", "", "", "", ""))), CP949);

        assertThatThrownBy(() -> RecordMapper.adminDongs(table))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("생성일자");
    }

    @Test
    void failsWhenExpectedColumnIsMissing() {
        var table = FixedWidthParser.parse("행정동코드 시도명\n1100000000 서울특별시\n".getBytes(CP949), CP949);

        assertThatThrownBy(() -> RecordMapper.adminDongs(table))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("시군구명");
    }
}
