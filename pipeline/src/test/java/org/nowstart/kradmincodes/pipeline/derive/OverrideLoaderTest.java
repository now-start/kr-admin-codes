package org.nowstart.kradmincodes.pipeline.derive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nowstart.kradmincodes.pipeline.PipelineException;

class OverrideLoaderTest {

    @TempDir
    Path dir;

    @Test
    void loadsRowsIncludingNoSuccessorMarker() throws IOException {
        Path file = write("""
                from_code\tto_code\teffective_date\tnote
                4772000000\t2772000000\t2023-07-01\t군위군 대구 편입
                1123053600\t\t2025-07-01\t통폐합
                """);

        var overrides = OverrideLoader.load(file);

        assertThat(overrides).containsExactly(
                new SuccessionOverride("4772000000", "2772000000", LocalDate.of(2023, 7, 1), "군위군 대구 편입"),
                new SuccessionOverride("1123053600", "", LocalDate.of(2025, 7, 1), "통폐합"));
        assertThat(overrides.get(1).hasSuccessor()).isFalse();
    }

    @Test
    void missingFileIsErrorSoManualSuccessionsAreNeverDroppedSilently() {
        assertThatThrownBy(() -> OverrideLoader.load(dir.resolve("none.tsv")))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("none.tsv");
    }

    @Test
    void reportsLineNumberForBadRow() throws IOException {
        Path file = write("""
                from_code\tto_code\teffective_date\tnote
                4772000000\t2772000000\t2023-07-01\tok
                47720\t2772000000\t2023-07-01\t잘못된 코드
                """);

        assertThatThrownBy(() -> OverrideLoader.load(file))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("3번째 줄");
    }

    @Test
    void rejectsDuplicatePair() throws IOException {
        Path file = write("""
                from_code\tto_code\teffective_date\tnote
                4772000000\t2772000000\t2023-07-01\ta
                4772000000\t2772000000\t2023-07-01\tb
                """);

        assertThatThrownBy(() -> OverrideLoader.load(file))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("중복");
    }

    private Path write(String content) throws IOException {
        Path file = dir.resolve("successions.tsv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
