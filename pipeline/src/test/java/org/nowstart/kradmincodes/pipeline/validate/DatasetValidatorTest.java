package org.nowstart.kradmincodes.pipeline.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nowstart.kradmincodes.pipeline.TestRows.admin;
import static org.nowstart.kradmincodes.pipeline.TestRows.date;
import static org.nowstart.kradmincodes.pipeline.TestRows.legal;
import static org.nowstart.kradmincodes.pipeline.TestRows.link;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.pipeline.Dataset;
import org.nowstart.kradmincodes.pipeline.derive.Unresolved;
import org.nowstart.kradmincodes.pipeline.validate.KnownCases.ExpectedSuccession;
import org.nowstart.kradmincodes.pipeline.validate.KnownCases.ExpectedValidity;

class DatasetValidatorTest {

    private static final LocalDate GUARANTEED_FROM = LocalDate.of(2023, 1, 1);
    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 11);
    private final DatasetValidator validator = new DatasetValidator(GUARANTEED_FROM, KnownCases.NONE);

    @Test
    void cleanDatasetHasNoErrorsOrWarnings() {
        ValidationReport report = validator.validate(clean());

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
        assertThat(report.hasErrors()).isFalse();
    }

    @Test
    void recentLinkToMissingCodeIsErrorButOldOneIsWarning() {
        Dataset dataset = new Dataset(
                List.of(admin("1111051500", "서울특별시", "종로구", "청운효자동", "2008-11-01", null)),
                List.of(legal("1111010100", "1988-04-23", null)),
                List.of(
                        link("1111051500", "1111099900", "2008-11-01", null),
                        link("1111099800", "1111010100", "1988-04-23", "2000-01-01")),
                List.of(), List.of());

        ValidationReport report = validator.validate(dataset);

        assertThat(report.errors()).singleElement().asString().contains("1111051500→1111099900");
        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("1건"));
    }

    @Test
    void branchOfficeSelfLinkMissingFromLegalFileIsOnlyWarning() {
        // 원천 특성: 출장소는 법정 구역이 아니라서 매핑이 자기 코드를 법정동 코드로 가리키지만 법정동 파일에는 없다
        Dataset dataset = new Dataset(
                List.of(admin("4122200000", "경기도", "송탄출장소", "", "2004-10-25", null)),
                List.of(),
                List.of(link("4122200000", "4122200000", "2004-10-25", null)),
                List.of(), List.of());

        ValidationReport report = validator.validate(dataset);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).singleElement().asString().contains("출장소").contains("1건");
    }

    @Test
    void duplicateCodeIsError() {
        Dataset dataset = new Dataset(
                List.of(
                        admin("1100000000", "서울특별시", "", "", "1988-04-23", null),
                        admin("1100000000", "서울특별시", "", "", "1988-04-23", null)),
                List.of(), List.of(), List.of(), List.of());

        assertThat(validator.validate(dataset).errors()).singleElement().asString().contains("중복");
    }

    @Test
    void emptyValidityAndUnresolvedAreWarnings() {
        Dataset dataset = new Dataset(
                List.of(admin("1111099900", "서울특별시", "종로구", "가짜동", "2024-01-01", "2024-01-01")),
                List.of(), List.of(), List.of(),
                List.of(new Unresolved("1111099900", "서울특별시", "종로구", "가짜동", date("2024-01-01"))));

        ValidationReport report = validator.validate(dataset);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).hasSize(2);
    }

    @Test
    void knownCaseMismatchAndMissingSuccessionAreErrors() {
        var known = new KnownCases(
                List.of(new ExpectedValidity("4200000000", date("1988-04-23"), date("2023-06-11"))),
                List.of(new ExpectedSuccession("4200000000", "5100000000")));
        Dataset dataset = new Dataset(
                List.of(admin("4200000000", "강원도", "", "", "1988-04-23", "2023-06-12")),
                List.of(), List.of(), List.of(), List.of());

        ValidationReport report = new DatasetValidator(GUARANTEED_FROM, known).validate(dataset);

        assertThat(report.errors()).hasSize(2);
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("4200000000"));
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("4200000000→5100000000"));
    }

    @Test
    void knownCasesPassWhenPresent() {
        var known = new KnownCases(
                List.of(new ExpectedValidity("4200000000", date("1988-04-23"), date("2023-06-11"))),
                List.of(new ExpectedSuccession("4200000000", "5100000000")));
        Dataset dataset = new Dataset(
                List.of(
                        admin("4200000000", "강원도", "", "", "1988-04-23", "2023-06-11"),
                        admin("5100000000", "강원특별자치도", "", "", "2023-06-11", null)),
                List.of(), List.of(),
                List.of(new Succession("4200000000", "5100000000", date("2023-06-11"), SuccessionMethod.NAME_MATCH)),
                List.of());

        assertThat(new DatasetValidator(GUARANTEED_FROM, known).validate(dataset).errors()).isEmpty();
    }

    @Test
    void codesMustNotDisappearAndRowCountsMustNotShrink() {
        Dataset previous = clean();
        Dataset current = new Dataset(
                List.of(admin("1100000000", "서울특별시", "", "", "1988-04-23", null)),
                List.of(), List.of(), List.of(), List.of());

        ValidationReport report = validator.validate(current, previous, AS_OF);

        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("행정동").contains("1111051500"));
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("법정동"));
        assertThat(report.errors()).anySatisfy(e -> assertThat(e).contains("매핑"));
    }

    @Test
    void successionsMustNotDisappear() {
        var succession = new Succession("4772000000", "2772000000", date("2023-07-01"), SuccessionMethod.MANUAL);
        Dataset previous = new Dataset(clean().adminDongs(), clean().legalDongs(), clean().links(),
                List.of(succession), List.of());

        ValidationReport report = validator.validate(clean(), previous, AS_OF);

        assertThat(report.errors()).singleElement().asString().contains("변환").contains("4772000000→2772000000");
    }

    @Test
    void largeChangeInActiveAdminCountIsWarning() {
        List<AdminDong> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(admin("11110" + (510 + i) + "00", "서울특별시", "종로구", "동" + i, "2008-11-01", null));
        }
        Dataset previous = new Dataset(many, List.of(), List.of(), List.of(), List.of());
        List<AdminDong> fewerActive = new ArrayList<>(many.subList(0, 8));
        fewerActive.add(admin("1111051800", "서울특별시", "종로구", "동8", "2008-11-01", "2026-01-01"));
        fewerActive.add(admin("1111051900", "서울특별시", "종로구", "동9", "2008-11-01", "2026-01-01"));
        Dataset current = new Dataset(fewerActive, List.of(), List.of(), List.of(), List.of());

        ValidationReport report = validator.validate(current, previous, AS_OF);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).singleElement().asString().contains("10 → 8");
    }

    private static Dataset clean() {
        return new Dataset(
                List.of(
                        admin("1100000000", "서울특별시", "", "", "1988-04-23", null),
                        admin("1111051500", "서울특별시", "종로구", "청운효자동", "2008-11-01", null)),
                List.of(legal("1111010100", "1988-04-23", null)),
                List.of(link("1111051500", "1111010100", "2008-11-01", null)),
                List.of(), List.of());
    }
}
