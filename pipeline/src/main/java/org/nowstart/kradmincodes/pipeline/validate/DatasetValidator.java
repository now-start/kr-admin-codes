package org.nowstart.kradmincodes.pipeline.validate;

import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Validity;
import org.nowstart.kradmincodes.pipeline.Dataset;
import org.nowstart.kradmincodes.pipeline.validate.KnownCases.ExpectedSuccession;
import org.nowstart.kradmincodes.pipeline.validate.KnownCases.ExpectedValidity;

/**
 * 쓰기 전에 데이터를 검사한다. 오류는 원천이 깨졌거나 파싱이 틀렸다는 신호라 쓰지 않고 멈춘다. 경고는 데이터에 원래 있는 품질 문제나
 * 사람이 확인할 변화라서 PR에 남긴다.
 */
public final class DatasetValidator {

    /** 정확도를 보장하는 범위의 시작. 이전 데이터의 품질 문제는 경고로만 남긴다. */
    public static final LocalDate GUARANTEED_FROM = LocalDate.of(2023, 1, 1);

    /** 유효 행정동 수가 이 비율보다 크게 바뀌면 경고한다. */
    static final double ACTIVE_CHANGE_WARNING_RATIO = 0.10;

    private static final int EXAMPLE_LIMIT = 5;

    private final LocalDate guaranteedFrom;
    private final KnownCases knownCases;

    public DatasetValidator(LocalDate guaranteedFrom, KnownCases knownCases) {
        this.guaranteedFrom = Objects.requireNonNull(guaranteedFrom, "guaranteedFrom");
        this.knownCases = Objects.requireNonNull(knownCases, "knownCases");
    }

    /** 이전 데이터가 없을 때(첫 생성). */
    public ValidationReport validate(Dataset current) {
        var issues = new Issues();
        checkBaseline(current, issues);
        return issues.toReport();
    }

    /** 이전 데이터와 비교까지 한다. {@code asOf}는 유효 행정동 수를 세는 기준일이다. */
    public ValidationReport validate(Dataset current, Dataset previous, LocalDate asOf) {
        var issues = new Issues();
        checkBaseline(current, issues);
        checkAgainstPrevious(current, previous, asOf, issues);
        return issues.toReport();
    }

    private void checkBaseline(Dataset current, Issues issues) {
        checkUnique("행정동", current.adminDongs(), AdminDong::code, issues);
        checkUnique("법정동", current.legalDongs(), LegalDong::code, issues);
        checkLinks(current, issues);
        checkEmptyValidity(current, issues);
        checkKnownCases(current, issues);
        if (!current.unresolved().isEmpty()) {
            issues.warnings.add("자동으로 잇지 못한 말소 행정동 " + current.unresolved().size() + "건 (" + guaranteedFrom
                    + " 이후): unresolved-succession.tsv를 보고 overrides/successions.tsv에 채우세요");
        }
    }

    private static <T> void checkUnique(String label, List<T> rows, Function<T, String> code, Issues issues) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = rows.stream().map(code).filter(c -> !seen.add(c)).toList();
        if (!duplicates.isEmpty()) {
            issues.errors.add(label + " 코드가 중복됩니다 " + duplicates.size() + "건, 예: " + examples(duplicates));
        }
    }

    private void checkLinks(Dataset current, Issues issues) {
        Set<String> adminCodes = current.adminDongs().stream().map(AdminDong::code).collect(toSet());
        Set<String> legalCodes = current.legalDongs().stream().map(LegalDong::code).collect(toSet());
        List<String> recent = new ArrayList<>();
        int old = 0;
        int officeSelfLinks = 0;
        for (AdminLegalLink link : current.links()) {
            if (adminCodes.contains(link.adminCode()) && legalCodes.contains(link.legalCode())) {
                continue;
            }
            if (isOfficeSelfLink(link, adminCodes)) {
                officeSelfLinks++;
            } else if (link.validity().isValidOnOrAfter(guaranteedFrom)) {
                recent.add(link.adminCode() + "→" + link.legalCode());
            } else {
                old++;
            }
        }
        if (!recent.isEmpty()) {
            issues.errors.add("매핑이 원천에 없는 코드를 가리킵니다 (" + guaranteedFrom + " 이후 유효) " + recent.size()
                    + "건, 예: " + examples(recent));
        }
        if (officeSelfLinks > 0) {
            issues.warnings.add("출장소처럼 자기 코드를 법정동 코드로 가리키는데 법정동 파일에 없는 매핑 " + officeSelfLinks
                    + "건 (원천 특성)");
        }
        if (old > 0) {
            issues.warnings.add(guaranteedFrom + " 이전에 끝난 매핑 중 원천에 없는 코드를 가리키는 것 " + old + "건");
        }
    }

    /**
     * 출장소는 법정 구역이 아니라서 원천 매핑이 행정동 코드를 그대로 법정동 코드 자리에 넣는다. 그 코드는 법정동 파일에 없다
     * (2026-11-11 원천 기준 12건 중 9건이 2023년 이후 유효).
     */
    private static boolean isOfficeSelfLink(AdminLegalLink link, Set<String> adminCodes) {
        return link.adminCode().equals(link.legalCode()) && adminCodes.contains(link.adminCode());
    }

    private static void checkEmptyValidity(Dataset current, Issues issues) {
        long admin = countEmpty(current.adminDongs(), AdminDong::validity);
        long legal = countEmpty(current.legalDongs(), LegalDong::validity);
        long link = countEmpty(current.links(), AdminLegalLink::validity);
        if (admin + legal + link > 0) {
            issues.warnings.add("유효기간이 비어 있는 행(생성일자 ≥ 말소일자): 행정동 " + admin + ", 법정동 " + legal
                    + ", 매핑 " + link + " — 어떤 날짜에도 유효하지 않은 것으로 취급합니다");
        }
    }

    private void checkKnownCases(Dataset current, Issues issues) {
        Map<String, AdminDong> byCode = current.adminDongs().stream()
                .collect(Collectors.toMap(AdminDong::code, Function.identity(), (a, b) -> a));
        for (ExpectedValidity expected : knownCases.codes()) {
            AdminDong row = byCode.get(expected.code());
            Validity want = new Validity(expected.from(), expected.toExclusive());
            if (row == null || !row.validity().equals(want)) {
                issues.errors.add("알려진 사례와 다릅니다: " + expected.code() + " 기대 " + want + ", 실제 "
                        + (row == null ? "없음" : row.validity()));
            }
        }
        Set<String> pairs = current.successions().stream()
                .map(s -> s.fromCode() + "→" + s.toCode())
                .collect(toSet());
        for (ExpectedSuccession expected : knownCases.successions()) {
            String pair = expected.fromCode() + "→" + expected.toCode();
            if (!pairs.contains(pair)) {
                issues.errors.add("알려진 변환이 없습니다: " + pair);
            }
        }
    }

    private static void checkAgainstPrevious(Dataset current, Dataset previous, LocalDate asOf, Issues issues) {
        checkNoCodeLost("행정동", previous.adminDongs(), current.adminDongs(), AdminDong::code, issues);
        checkNoCodeLost("법정동", previous.legalDongs(), current.legalDongs(), LegalDong::code, issues);
        if (current.links().size() < previous.links().size()) {
            issues.errors.add("매핑 행 수가 줄었습니다: " + previous.links().size() + " → " + current.links().size());
        }
        checkNoSuccessionLost(current, previous, issues);
        long before = countActive(previous.adminDongs(), asOf);
        long after = countActive(current.adminDongs(), asOf);
        if (before > 0 && Math.abs(after - before) > before * ACTIVE_CHANGE_WARNING_RATIO) {
            issues.warnings.add(asOf + " 기준 유효 행정동 수가 크게 바뀌었습니다: " + before + " → " + after);
        }
    }

    private static <T> void checkNoCodeLost(
            String label, List<T> previous, List<T> current, Function<T, String> code, Issues issues) {
        Set<String> currentCodes = current.stream().map(code).collect(toSet());
        List<String> lost = previous.stream().map(code).filter(c -> !currentCodes.contains(c)).sorted().toList();
        if (!lost.isEmpty()) {
            issues.errors.add("이전 데이터에 있던 " + label + " 코드가 사라졌습니다 " + lost.size() + "건, 예: "
                    + examples(lost));
        }
    }

    /** overrides 파일이 바뀌거나 빠져서 수동 변환이 사라지는 일을 막는다. 의도한 정정이면 이전 줄을 먼저 지운다. */
    private static void checkNoSuccessionLost(Dataset current, Dataset previous, Issues issues) {
        Set<String> currentPairs = current.successions().stream()
                .map(s -> s.fromCode() + "→" + s.toCode())
                .collect(toSet());
        List<String> lost = previous.successions().stream()
                .map(s -> s.fromCode() + "→" + s.toCode())
                .filter(pair -> !currentPairs.contains(pair))
                .sorted()
                .toList();
        if (!lost.isEmpty()) {
            issues.errors.add("이전 데이터에 있던 변환이 사라졌습니다 " + lost.size() + "건, 예: " + examples(lost)
                    + " (의도한 정정이면 data/v1/succession.tsv에서 해당 줄을 먼저 지우세요)");
        }
    }

    private static <T> long countEmpty(List<T> rows, Function<T, Validity> validity) {
        return rows.stream().map(validity).filter(Validity::isEmpty).count();
    }

    private static long countActive(List<AdminDong> rows, LocalDate asOf) {
        return rows.stream().filter(row -> row.validity().isValidOn(asOf)).count();
    }

    private static String examples(List<String> values) {
        return values.stream().limit(EXAMPLE_LIMIT).toList().toString();
    }

    /** 검사 중에만 쓰는 누적 버퍼. 밖으로는 불변인 {@link ValidationReport}만 나간다. */
    private static final class Issues {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        ValidationReport toReport() {
            return new ValidationReport(errors, warnings);
        }
    }
}
