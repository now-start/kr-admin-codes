package org.nowstart.kradmincodes.sdk;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Succession;

/** 불변 스냅샷과 조회용 인덱스. 갱신하면 통째로 새 인스턴스로 바뀐다. */
final class Snapshot {

    private final SnapshotMeta meta;
    private final List<AdminDong> admins;
    private final Map<String, AdminDong> adminByCode;
    private final Map<String, LegalDong> legalByCode;
    private final Map<String, List<AdminLegalLink>> linksByAdmin;
    private final Map<String, List<AdminLegalLink>> linksByLegal;
    private final Map<String, List<Succession>> successionsByFrom;

    Snapshot(SnapshotMeta meta, List<AdminDong> admins, List<LegalDong> legals, List<AdminLegalLink> links,
            List<Succession> successions) {
        this.meta = meta;
        this.admins = admins.stream().sorted(comparing(AdminDong::code)).toList();
        this.adminByCode = index("행정동", admins, AdminDong::code);
        this.legalByCode = index("법정동", legals, LegalDong::code);
        this.linksByAdmin = Map.copyOf(links.stream().collect(groupingBy(AdminLegalLink::adminCode, toUnmodifiableList())));
        this.linksByLegal = Map.copyOf(links.stream().collect(groupingBy(AdminLegalLink::legalCode, toUnmodifiableList())));
        this.successionsByFrom =
                Map.copyOf(successions.stream().collect(groupingBy(Succession::fromCode, toUnmodifiableList())));
    }

    SnapshotMeta meta() {
        return meta;
    }

    Optional<AdminDong> adminDong(String code) {
        return Optional.ofNullable(adminByCode.get(code));
    }

    Optional<LegalDong> legalDong(String code) {
        return Optional.ofNullable(legalByCode.get(code));
    }

    List<AdminDong> adminDongs(LocalDate date) {
        return admins.stream().filter(row -> row.validity().isValidOn(date)).toList();
    }

    /** 그날 유효한 매핑으로 이어진 법정동. 출장소처럼 법정동 파일에 없는 코드를 가리키는 매핑은 건너뛴다. */
    List<LegalDong> legalDongsOf(String adminCode, LocalDate date) {
        return linksByAdmin.getOrDefault(adminCode, List.of()).stream()
                .filter(link -> link.validity().isValidOn(date))
                .map(link -> legalByCode.get(link.legalCode()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(comparing(LegalDong::code))
                .toList();
    }

    List<AdminDong> adminDongsOf(String legalCode, LocalDate date) {
        return linksByLegal.getOrDefault(legalCode, List.of()).stream()
                .filter(link -> link.validity().isValidOn(date))
                .map(link -> adminByCode.get(link.adminCode()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(comparing(AdminDong::code))
                .toList();
    }

    /**
     * 그날 유효한 코드로 바꾼다. 이미 유효하면 자기 자신. 아니면 효력일이 그날 이전인 변환을 따라가며 그날 유효한 코드를 모은다
     * (1:N이면 여러 개). 과거 방향(새 코드 → 옛 코드)은 따라가지 않는다. 모르는 코드나 후계가 없으면 빈 목록.
     */
    List<AdminDong> convert(String code, LocalDate date) {
        AdminDong start = adminByCode.get(code);
        if (start == null) {
            return List.of();
        }
        if (start.validity().isValidOn(date)) {
            return List.of(start);
        }
        Map<String, AdminDong> found = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>(Set.of(code));
        Deque<String> queue = new ArrayDeque<>(List.of(code));
        while (!queue.isEmpty()) {
            for (Succession succession : successionsByFrom.getOrDefault(queue.poll(), List.of())) {
                AdminDong target = adminByCode.get(succession.toCode());
                if (succession.effectiveDate().isAfter(date) || target == null || !visited.add(target.code())) {
                    continue;
                }
                if (target.validity().isValidOn(date)) {
                    found.put(target.code(), target);
                } else {
                    queue.add(target.code());
                }
            }
        }
        return found.values().stream().sorted(comparing(AdminDong::code)).toList();
    }

    private static <T> Map<String, T> index(String label, List<T> rows, Function<T, String> code) {
        Map<String, T> byCode = new HashMap<>(rows.size() * 2);
        for (T row : rows) {
            if (byCode.put(code.apply(row), row) != null) {
                throw new SnapshotException(label + " 코드가 중복됩니다: " + code.apply(row));
            }
        }
        return Map.copyOf(byCode);
    }
}
