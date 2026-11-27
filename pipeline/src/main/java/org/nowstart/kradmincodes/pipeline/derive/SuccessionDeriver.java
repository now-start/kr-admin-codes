package org.nowstart.kradmincodes.pipeline.derive;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.Level;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/**
 * 말소된 행정동 코드마다 같은 날 생긴 후계 코드를 찾는다.
 *
 * <p>상위 단위부터 차례로 푼다. 후보는 말소일에 생긴 같은 단위 코드 중 시군구명·읍면동명이 같고, 상위 단위 코드가 같거나 상위
 * 단위가 그날 그 코드로 이어진 것이다. 그래서 같은 날 다른 시도에 생긴 같은 이름의 구(인천 동구 ↔ 전남광주 동구)는 잇지
 * 않는다. 후보가 여럿이면 시도명이 같은 것으로 좁힌다(출장소는 이름이 시도명 열에 있다). 그래도 하나가 아니면 미해결로 남긴다.
 *
 * <p>시도 단위는 명칭 자체가 바뀌므로(강원도 → 강원특별자치도) 이름을 비교하지 않고, 말소일에 생긴 시도가 정확히 하나면 그리로
 * 잇는다. 여러 시도가 같은 날 하나로 합쳐지면(광주·전남 → 전남광주통합특별시) 각각 그리로 이어진다.
 *
 * <p>시도를 옮긴 경우(군위군 경북 → 대구)는 자동으로 잇지 않는다. 시군구 한 줄만 overrides로 넣으면 그 아래 읍면동은 상위
 * 연결을 따라 자동으로 이어진다. overrides는 원천과 맞아야 한다: from_code는 말소된 코드, effective_date는 그 말소일,
 * to_code는 원천에 있는 코드.
 */
public final class SuccessionDeriver {

    private static final List<Level> LEVELS_TOP_DOWN =
            List.of(Level.SIDO, Level.SIGUNGU, Level.EUPMYEONDONG, Level.RI);

    private final LocalDate guaranteedFrom;

    /** @param guaranteedFrom 이 날짜 이후 말소분만 미해결로 보고한다 */
    public SuccessionDeriver(LocalDate guaranteedFrom) {
        this.guaranteedFrom = Objects.requireNonNull(guaranteedFrom, "guaranteedFrom");
    }

    public DerivationResult derive(List<AdminDong> rows, List<SuccessionOverride> overrides) {
        Map<String, List<SuccessionOverride>> manual =
                overrides.stream().collect(groupingBy(SuccessionOverride::fromCode));
        requireOverridesMatchSource(rows, overrides);
        Map<LocalDate, List<AdminDong>> createdOn = rows.stream()
                .filter(row -> !row.validity().isEmpty())
                .collect(groupingBy(row -> row.validity().from()));

        List<Succession> successions = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        for (Level level : LEVELS_TOP_DOWN) {
            ParentLinks parents = ParentLinks.of(level, successions);
            for (AdminDong row : rows) {
                if (row.level() == level && isAbolished(row)) {
                    deriveRow(row, manual.get(row.code()), createdOn, parents, successions, unresolved);
                }
            }
        }
        return new DerivationResult(successions, unresolved);
    }

    private void deriveRow(
            AdminDong row,
            List<SuccessionOverride> rowOverrides,
            Map<LocalDate, List<AdminDong>> createdOn,
            ParentLinks parents,
            List<Succession> successions,
            List<Unresolved> unresolved) {
        if (rowOverrides != null) {
            rowOverrides.stream()
                    .filter(SuccessionOverride::hasSuccessor)
                    .map(o -> new Succession(o.fromCode(), o.toCode(), o.effectiveDate(), SuccessionMethod.MANUAL))
                    .forEach(successions::add);
            return;
        }
        LocalDate abolishedOn = row.validity().toExclusive();
        findSuccessor(row, createdOn.getOrDefault(abolishedOn, List.of()), parents).ifPresentOrElse(
                successor -> successions.add(
                        new Succession(row.code(), successor.code(), abolishedOn, SuccessionMethod.NAME_MATCH)),
                () -> {
                    if (!abolishedOn.isBefore(guaranteedFrom)) {
                        unresolved.add(Unresolved.of(row));
                    }
                });
    }

    private static Optional<AdminDong> findSuccessor(
            AdminDong row, List<AdminDong> createdSameDay, ParentLinks parents) {
        LocalDate date = row.validity().toExclusive();
        List<AdminDong> candidates = createdSameDay.stream()
                .filter(c -> c.level() == row.level() && !c.code().equals(row.code()))
                .filter(c -> row.level() == Level.SIDO
                        || (sameNames(row, c) && parents.allows(row.code(), c.code(), date)))
                .toList();
        if (candidates.size() > 1) {
            candidates = candidates.stream().filter(c -> c.sido().equals(row.sido())).toList();
        }
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    private static boolean sameNames(AdminDong a, AdminDong b) {
        return a.sigungu().equals(b.sigungu()) && a.dong().equals(b.dong());
    }

    private static boolean isAbolished(AdminDong row) {
        return row.validity().toExclusive() != null && !row.validity().isEmpty();
    }

    private static void requireOverridesMatchSource(List<AdminDong> rows, List<SuccessionOverride> overrides) {
        Map<String, AdminDong> byCode = rows.stream()
                .collect(Collectors.toMap(AdminDong::code, Function.identity(), (first, duplicate) -> first));
        List<String> problems = new ArrayList<>();
        for (SuccessionOverride override : overrides) {
            AdminDong from = byCode.get(override.fromCode());
            if (from == null || !isAbolished(from)) {
                problems.add(override.fromCode() + ": 원천에서 말소된 행정동 코드가 아님");
                continue;
            }
            LocalDate abolishedOn = from.validity().toExclusive();
            if (!override.effectiveDate().equals(abolishedOn)) {
                problems.add(override.fromCode() + ": effective_date " + override.effectiveDate()
                        + "가 말소일 " + abolishedOn + "과 다름");
            }
            if (override.hasSuccessor() && !byCode.containsKey(override.toCode())) {
                problems.add(override.toCode() + ": 원천에 없는 to_code");
            }
        }
        if (!problems.isEmpty()) {
            throw new PipelineException("overrides가 원천과 맞지 않습니다: " + problems);
        }
    }

    /** 상위 단위 코드(앞자리)가 어느 날 어디로 이어졌는지. 상위가 없으면(시도) 무엇이든 허용한다. */
    private record ParentLinks(int prefixLength, Set<String> links) {

        private static final int SIDO_PREFIX = 2;
        private static final int SIGUNGU_PREFIX = 5;
        private static final int EUPMYEONDONG_PREFIX = 8;

        static ParentLinks of(Level level, List<Succession> successions) {
            if (level == Level.SIDO) {
                return new ParentLinks(0, Set.of());
            }
            Level parent = switch (level) {
                case SIGUNGU -> Level.SIDO;
                case EUPMYEONDONG -> Level.SIGUNGU;
                default -> Level.EUPMYEONDONG;
            };
            int length = switch (parent) {
                case SIDO -> SIDO_PREFIX;
                case SIGUNGU -> SIGUNGU_PREFIX;
                default -> EUPMYEONDONG_PREFIX;
            };
            Set<String> links = successions.stream()
                    .filter(s -> Level.of(s.fromCode()) == parent)
                    .map(s -> key(s.fromCode().substring(0, length), s.toCode().substring(0, length), s.effectiveDate()))
                    .collect(toSet());
            return new ParentLinks(length, Set.copyOf(links));
        }

        boolean allows(String fromCode, String toCode, LocalDate date) {
            if (prefixLength == 0) {
                return true;
            }
            String from = fromCode.substring(0, prefixLength);
            String to = toCode.substring(0, prefixLength);
            return from.equals(to) || links.contains(key(from, to, date));
        }

        private static String key(String from, String to, LocalDate date) {
            return from + ">" + to + "@" + date;
        }
    }
}
