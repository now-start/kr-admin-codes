package org.nowstart.kradmincodes.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.LegalDong;

class AdminCodesQueryTest {

    /** 2024-01-01 12:00 KST */
    static final Clock FIXED = Clock.fixed(Instant.parse("2024-01-01T03:00:00Z"), AdminCodes.KOREA);

    private AdminCodes codes;

    @BeforeEach
    void setUp() {
        codes = AdminCodes.create(new MapSource("test", sample().files()), List.of(), FIXED, false, Duration.ofHours(6));
    }

    @AfterEach
    void tearDown() {
        codes.close();
    }

    static TestSnapshot sample() {
        return new TestSnapshot()
                .admin("4200000000", "강원도", "", "", "1988-04-23", "2023-06-11")
                .admin("5100000000", "강원특별자치도", "", "", "2023-06-11", "")
                .admin("4211051000", "강원도", "춘천시", "교동", "1988-04-23", "2023-06-11")
                .admin("5111051000", "강원특별자치도", "춘천시", "교동", "2023-06-11", "2026-07-01")
                .admin("5111052000", "강원특별자치도", "춘천시", "신교1동", "2026-07-01", "")
                .admin("5111053000", "강원특별자치도", "춘천시", "신교2동", "2026-07-01", "")
                .admin("1111099900", "서울특별시", "종로구", "가짜동", "2024-01-01", "2024-01-01")
                .admin("1111088000", "서울특별시", "종로구", "순환1동", "2000-01-01", "2010-01-01")
                .admin("1111088100", "서울특별시", "종로구", "순환2동", "2000-01-01", "2010-01-01")
                .admin("4122200000", "경기도", "송탄출장소", "", "2004-10-25", "")
                .admin("1123053600", "서울특별시", "동대문구", "용신동", "2009-01-01", "2025-07-01")
                .admin("1123053700", "서울특별시", "동대문구", "용두동", "1990-01-01", "")
                .legal("4211010100", "1988-04-23", "2023-06-11")
                .legal("5111010100", "2023-06-11", "")
                .link("4211051000", "4211010100", "1988-04-23", "2023-06-11")
                .link("5111051000", "5111010100", "2023-06-11", "2026-07-01")
                .link("4122200000", "4122200000", "2004-10-25", "")
                .succession("4200000000", "5100000000", "2023-06-11", "NAME_MATCH")
                .succession("4211051000", "5111051000", "2023-06-11", "NAME_MATCH")
                .succession("5111051000", "5111052000", "2026-07-01", "MANUAL")
                .succession("5111051000", "5111053000", "2026-07-01", "MANUAL")
                .succession("1111088000", "1111088100", "2010-01-01", "MANUAL")
                .succession("1111088100", "1111088000", "2010-01-01", "MANUAL")
                .succession("1123053600", "1123053700", "2025-07-01", "MANUAL");
    }

    @Test
    void findsCodesRegardlessOfValidity() {
        assertThat(codes.adminDong("4200000000")).map(AdminDong::sido).contains("강원도");
        assertThat(codes.adminDong("9999999999")).isEmpty();
        assertThat(codes.legalDong("5111010100")).isPresent();
    }

    @Test
    void listsCodesValidOnDateUsingHalfOpenInterval() {
        assertThat(codesOf(codes.adminDongs(LocalDate.of(2023, 6, 10)))).contains("4200000000").doesNotContain("5100000000");
        assertThat(codesOf(codes.adminDongs(LocalDate.of(2023, 6, 11)))).contains("5100000000").doesNotContain("4200000000");
        assertThat(codesOf(codes.adminDongs(LocalDate.of(2024, 1, 1)))).doesNotContain("1111099900");
    }

    @Test
    void convertReturnsItselfWhileStillValid() {
        assertThat(codesOf(codes.convert("5100000000", LocalDate.of(2024, 1, 1)))).containsExactly("5100000000");
        assertThat(codesOf(codes.convert("4211051000", LocalDate.of(2023, 6, 10)))).containsExactly("4211051000");
    }

    @Test
    void convertFollowsChainsAndSplits() {
        assertThat(codesOf(codes.convert("4211051000", LocalDate.of(2024, 1, 1)))).containsExactly("5111051000");
        assertThat(codesOf(codes.convert("4211051000", LocalDate.of(2026, 7, 1))))
                .containsExactly("5111052000", "5111053000");
    }

    @Test
    void convertIsEmptyForUnknownCodesCyclesAndDatesBeforeCreation() {
        assertThat(codes.convert("9999999999", LocalDate.of(2024, 1, 1))).isEmpty();
        assertThat(codes.convert("1111088000", LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(codes.convert("5111052000", LocalDate.of(2020, 1, 1))).isEmpty();
    }

    @Test
    void mergeIntoExistingCodeIsNotFollowedBeforeItsEffectiveDate() {
        // 용신동(2009~2025)이 이미 있던 용두동(1990~)으로 흡수됨
        assertThat(codesOf(codes.convert("1123053600", LocalDate.of(2026, 1, 1)))).containsExactly("1123053700");
        // 용신동이 생기기 전 날짜로는 바꿀 근거가 없다 (용두동이 그때도 유효했더라도)
        assertThat(codes.convert("1123053600", LocalDate.of(2000, 1, 1))).isEmpty();
    }

    @Test
    void mapsAdminAndLegalDongsOnDate() {
        assertThat(legalCodesOf(codes.legalDongsOf("5111051000", LocalDate.of(2024, 1, 1)))).containsExactly("5111010100");
        assertThat(codes.legalDongsOf("5111051000", LocalDate.of(2026, 7, 1))).isEmpty();
        assertThat(codesOf(codes.adminDongsOf("5111010100", LocalDate.of(2024, 1, 1)))).containsExactly("5111051000");
        // 출장소 자기 참조 매핑: 법정동 파일에 없는 코드라 건너뛴다
        assertThat(codes.legalDongsOf("4122200000", LocalDate.of(2024, 1, 1))).isEmpty();
    }

    @Test
    void overloadsWithoutDateUseClockInKorea() {
        assertThat(codesOf(codes.convert("4211051000"))).containsExactly("5111051000");
        assertThat(codesOf(codes.adminDongs())).contains("5100000000");
        assertThat(legalCodesOf(codes.legalDongsOf("5111051000"))).containsExactly("5111010100");
        assertThat(codesOf(codes.adminDongsOf("5111010100"))).containsExactly("5111051000");
    }

    @Test
    void infoDescribesLoadedSnapshot() {
        SnapshotInfo info = codes.info();

        assertThat(info.origin()).isEqualTo("test");
        assertThat(info.dataDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(info.version()).hasSize(64);
        assertThat(info.loadedAt()).isEqualTo(FIXED.instant());
        assertThat(info.lastCheckedAt()).isNull();
    }

    @Test
    void builderRejectsBadSettings() {
        assertThatThrownBy(() -> AdminCodes.builder().refreshInterval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminCodes.builder().sources(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdminCodes.builder().clock(null)).isInstanceOf(NullPointerException.class);
    }

    static List<String> codesOf(List<AdminDong> rows) {
        return rows.stream().map(AdminDong::code).toList();
    }

    private static List<String> legalCodesOf(List<LegalDong> rows) {
        return rows.stream().map(LegalDong::code).toList();
    }
}
