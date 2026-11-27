package org.nowstart.kradmincodes.pipeline.validate;

import java.time.LocalDate;
import java.util.List;

/**
 * 원천에서 직접 확인한 사례 (docs/research/phase1-sources.md 「알려진 사례 검증」). 결과가 이와 다르면 원천 형식이 바뀌었거나
 * 파싱·도출이 틀린 것이므로 멈춘다. 나중에 이 코드들이 실제로 바뀌면 사람이 확인하고 여기를 고친다.
 */
public record KnownCases(List<ExpectedValidity> codes, List<ExpectedSuccession> successions) {

    public static final KnownCases NONE = new KnownCases(List.of(), List.of());

    public static final KnownCases DEFAULT = new KnownCases(
            List.of(
                    validity("4200000000", "1988-04-23", "2023-06-11"),
                    validity("5100000000", "2023-06-11", null),
                    validity("4500000000", "1988-04-23", "2024-01-18"),
                    validity("5200000000", "2024-01-18", null),
                    validity("4772000000", "1988-04-23", "2023-07-01"),
                    validity("2772000000", "2023-07-01", null),
                    validity("2900000000", "1995-01-01", "2026-07-01"),
                    validity("4600000000", "1988-04-23", "2026-07-01"),
                    validity("1200000000", "2026-07-01", null)),
            List.of(
                    new ExpectedSuccession("4200000000", "5100000000"),
                    new ExpectedSuccession("4500000000", "5200000000"),
                    new ExpectedSuccession("4772000000", "2772000000"),
                    new ExpectedSuccession("2900000000", "1200000000"),
                    new ExpectedSuccession("4600000000", "1200000000")));

    public KnownCases {
        codes = List.copyOf(codes);
        successions = List.copyOf(successions);
    }

    /** 행정동 코드 하나의 기대 유효기간. 말소되지 않았으면 {@code toExclusive}는 null. */
    public record ExpectedValidity(String code, LocalDate from, LocalDate toExclusive) {
    }

    /** 반드시 있어야 하는 옛→새 코드 연결. */
    public record ExpectedSuccession(String fromCode, String toCode) {
    }

    private static ExpectedValidity validity(String code, String from, String to) {
        return new ExpectedValidity(code, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
    }
}
