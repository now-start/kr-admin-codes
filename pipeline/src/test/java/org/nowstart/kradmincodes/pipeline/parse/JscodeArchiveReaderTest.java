package org.nowstart.kradmincodes.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nowstart.kradmincodes.pipeline.parse.FixedWidthFixture.CP949;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.pipeline.PipelineException;

class JscodeArchiveReaderTest {

    private static final String SUFFIX = "20261111(말소코드포함)";

    @Test
    void readsTextEntriesAndIgnoresXlsx() {
        byte[] zip = zip(validEntries());

        JscodeArchive archive = new JscodeArchiveReader().read(zip);

        assertThat(archive.dataDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(archive.admin().rows()).hasSize(1);
        assertThat(archive.legal().header()).contains("동리명");
        assertThat(archive.link().header()).contains("법정동코드");
    }

    @Test
    void failsWhenAnEntryIsMissing() {
        Map<String, byte[]> entries = validEntries();
        entries.remove("KIKmix." + SUFFIX);

        assertThatThrownBy(() -> new JscodeArchiveReader().read(zip(entries)))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("KIKmix");
    }

    @Test
    void failsWhenEntriesHaveDifferentDates() {
        Map<String, byte[]> entries = validEntries();
        entries.put("KIKmix.20261026(말소코드포함)", entries.remove("KIKmix." + SUFFIX));

        assertThatThrownBy(() -> new JscodeArchiveReader().read(zip(entries)))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("기준일");
    }

    @Test
    void failsWhenEntryExceedsSizeLimit() {
        var reader = new JscodeArchiveReader(16);

        assertThatThrownBy(() -> reader.read(zip(validEntries())))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("크기");
    }

    @Test
    void failsOnBytesThatAreNotAZip() {
        assertThatThrownBy(() -> new JscodeArchiveReader().read("not a zip".getBytes(CP949)))
                .isInstanceOf(PipelineException.class);
    }

    private static Map<String, byte[]> validEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("KIKcd_H." + SUFFIX, FixedWidthFixture.admin(List.of(
                List.of("1100000000", "서울특별시", "", "", "19880423", ""))));
        entries.put("KIKcd_H." + SUFFIX + ".xlsx", new byte[] {1, 2, 3});
        entries.put("KIKcd_B." + SUFFIX, FixedWidthFixture.legal(List.of()));
        entries.put("KIKmix." + SUFFIX, FixedWidthFixture.link(List.of()));
        return entries;
    }

    static byte[] zip(Map<String, byte[]> entries) {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out, CP949)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
