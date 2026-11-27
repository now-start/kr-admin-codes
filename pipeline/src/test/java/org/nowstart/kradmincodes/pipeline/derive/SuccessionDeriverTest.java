package org.nowstart.kradmincodes.pipeline.derive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nowstart.kradmincodes.pipeline.TestRows.admin;
import static org.nowstart.kradmincodes.pipeline.TestRows.date;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.pipeline.PipelineException;

class SuccessionDeriverTest {

    private static final LocalDate GUARANTEED_FROM = LocalDate.of(2023, 1, 1);
    private final SuccessionDeriver deriver = new SuccessionDeriver(GUARANTEED_FROM);

    @Test
    void cascadesSidoRenameToSigunguAndDong() {
        List<AdminDong> rows = List.of(
                admin("4200000000", "강원도", "", "", "1988-04-23", "2023-06-11"),
                admin("5100000000", "강원특별자치도", "", "", "2023-06-11", null),
                admin("4211000000", "강원도", "춘천시", "", "1988-04-23", "2023-06-11"),
                admin("5111000000", "강원특별자치도", "춘천시", "", "2023-06-11", null),
                admin("4211051000", "강원도", "춘천시", "교동", "1988-04-23", "2023-06-11"),
                admin("5111051000", "강원특별자치도", "춘천시", "교동", "2023-06-11", null));

        DerivationResult result = deriver.derive(rows, List.of());

        assertThat(result.successions()).containsExactlyInAnyOrder(
                auto("4200000000", "5100000000", "2023-06-11"),
                auto("4211000000", "5111000000", "2023-06-11"),
                auto("4211051000", "5111051000", "2023-06-11"));
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    void breaksTiesBySidoNameForBranchOffices() {
        List<AdminDong> rows = List.of(
                admin("4200000000", "강원도", "", "", "1988-04-23", "2023-06-11"),
                admin("5100000000", "강원특별자치도", "", "", "2023-06-11", null),
                admin("4210500000", "동해출장소", "", "", "1988-04-23", "2023-06-11"),
                admin("5110500000", "동해출장소", "", "", "2023-06-11", null),
                admin("5110600000", "태백출장소", "", "", "2023-06-11", null));

        DerivationResult result = deriver.derive(rows, List.of());

        assertThat(result.successions()).contains(auto("4210500000", "5110500000", "2023-06-11"));
    }

    @Test
    void mergesTwoSidosButDoesNotLinkSameNamedDistrictInAnotherSido() {
        List<AdminDong> rows = List.of(
                admin("2900000000", "광주광역시", "", "", "1995-01-01", "2026-07-01"),
                admin("4600000000", "전라남도", "", "", "1988-04-23", "2026-07-01"),
                admin("1200000000", "전남광주통합특별시", "", "", "2026-07-01", null),
                admin("2911000000", "광주광역시", "동구", "", "1995-01-01", "2026-07-01"),
                admin("1211000000", "전남광주통합특별시", "동구", "", "2026-07-01", null),
                admin("2800000000", "인천광역시", "", "", "1988-04-23", null),
                admin("2814000000", "인천광역시", "동구", "", "1988-04-23", "2026-07-01"));

        DerivationResult result = deriver.derive(rows, List.of());

        assertThat(result.successions()).containsExactlyInAnyOrder(
                auto("2900000000", "1200000000", "2026-07-01"),
                auto("4600000000", "1200000000", "2026-07-01"),
                auto("2911000000", "1211000000", "2026-07-01"));
        assertThat(result.unresolved()).extracting(Unresolved::code).containsExactly("2814000000");
    }

    @Test
    void crossSidoMoveNeedsManualParentThenChildrenFollow() {
        List<AdminDong> rows = List.of(
                admin("4700000000", "경상북도", "", "", "1988-04-23", null),
                admin("2700000000", "대구광역시", "", "", "1988-04-23", null),
                admin("4772000000", "경상북도", "군위군", "", "1988-04-23", "2023-07-01"),
                admin("2772000000", "대구광역시", "군위군", "", "2023-07-01", null),
                admin("4772025000", "경상북도", "군위군", "군위읍", "1988-04-23", "2023-07-01"),
                admin("2772025000", "대구광역시", "군위군", "군위읍", "2023-07-01", null));

        DerivationResult withoutOverride = deriver.derive(rows, List.of());
        DerivationResult withOverride = deriver.derive(rows, List.of(
                new SuccessionOverride("4772000000", "2772000000", date("2023-07-01"), "군위군 대구 편입")));

        assertThat(withoutOverride.successions()).isEmpty();
        assertThat(withoutOverride.unresolved()).extracting(Unresolved::code)
                .containsExactlyInAnyOrder("4772000000", "4772025000");
        assertThat(withOverride.successions()).containsExactlyInAnyOrder(
                new Succession("4772000000", "2772000000", date("2023-07-01"), SuccessionMethod.MANUAL),
                auto("4772025000", "2772025000", "2023-07-01"));
        assertThat(withOverride.unresolved()).isEmpty();
    }

    @Test
    void reportsRenamesOnlyWithinGuaranteedRangeAndIgnoresEmptyRows() {
        List<AdminDong> rows = List.of(
                admin("1123053600", "서울특별시", "동대문구", "용신동", "2009-01-01", "2025-07-01"),
                admin("1123053700", "서울특별시", "동대문구", "용두동", "2025-07-01", null),
                admin("1111051000", "서울특별시", "종로구", "청운동", "1988-04-23", "2008-11-01"),
                admin("1111099900", "서울특별시", "종로구", "가짜동", "2024-01-01", "2024-01-01"));

        DerivationResult result = deriver.derive(rows, List.of());

        assertThat(result.successions()).isEmpty();
        assertThat(result.unresolved()).containsExactly(
                new Unresolved("1123053600", "서울특별시", "동대문구", "용신동", date("2025-07-01")));
    }

    @Test
    void overrideWithoutSuccessorMarksCodeAsChecked() {
        List<AdminDong> rows = List.of(
                admin("1123053600", "서울특별시", "동대문구", "용신동", "2009-01-01", "2025-07-01"));

        DerivationResult result = deriver.derive(rows, List.of(
                new SuccessionOverride("1123053600", "", date("2025-07-01"), "통폐합, 후계 없음")));

        assertThat(result.successions()).isEmpty();
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    void rejectsOverrideForUnknownOrActiveCode() {
        List<AdminDong> rows = List.of(admin("5100000000", "강원특별자치도", "", "", "2023-06-11", null));

        assertThatThrownBy(() -> deriver.derive(rows, List.of(
                new SuccessionOverride("9999999999", "5100000000", date("2023-06-11"), ""))))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("9999999999");
        assertThatThrownBy(() -> deriver.derive(rows, List.of(
                new SuccessionOverride("5100000000", "", date("2023-06-11"), ""))))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("5100000000");
    }

    @Test
    void rejectsOverrideWithUnknownTargetOrDateOtherThanAbolition() {
        List<AdminDong> rows = List.of(
                admin("4772000000", "경상북도", "군위군", "", "1988-04-23", "2023-07-01"),
                admin("2772000000", "대구광역시", "군위군", "", "2023-07-01", null));

        assertThatThrownBy(() -> deriver.derive(rows, List.of(
                new SuccessionOverride("4772000000", "2772099999", date("2023-07-01"), "오타"))))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("2772099999");
        assertThatThrownBy(() -> deriver.derive(rows, List.of(
                new SuccessionOverride("4772000000", "2772000000", date("2023-06-30"), "날짜 오류"))))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("2023-06-30");
    }

    private static Succession auto(String from, String to, String date) {
        return new Succession(from, to, date(date), SuccessionMethod.NAME_MATCH);
    }
}
