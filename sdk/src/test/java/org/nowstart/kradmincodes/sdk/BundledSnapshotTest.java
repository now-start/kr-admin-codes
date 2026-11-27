package org.nowstart.kradmincodes.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nowstart.kradmincodes.sdk.AdminCodesQueryTest.codesOf;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** jar에 들어가는 실제 data/v1 스냅샷으로, 원천에서 확인한 개편 사례가 변환되는지 본다. */
class BundledSnapshotTest {

    @Test
    void knownReorganizationsConvertWithBundledSnapshot() {
        try (AdminCodes codes = AdminCodes.builder().autoRefresh(false).build()) {
            assertThat(codes.info().origin()).isEqualTo("bundled");
            // 강원도 → 강원특별자치도 (2023-06-11)
            assertThat(codesOf(codes.convert("4200000000", LocalDate.of(2024, 1, 1)))).containsExactly("5100000000");
            // 광주 동구 → 전남광주통합특별시 동구 (2026-07-01)
            assertThat(codesOf(codes.convert("2911000000", LocalDate.of(2026, 8, 1)))).containsExactly("1221000000");
            // 경북 군위군 군위읍 → 대구 군위군 군위읍 (2023-07-01, 상위 수동 연결을 따라 자동)
            assertThat(codesOf(codes.convert("4772025000", LocalDate.of(2024, 1, 1)))).containsExactly("2772025000");
            // 서울 종로구 청운효자동이 관할하는 법정동
            assertThat(codes.legalDongsOf("1111051500", LocalDate.of(2024, 1, 1))).isNotEmpty();
        }
    }
}
