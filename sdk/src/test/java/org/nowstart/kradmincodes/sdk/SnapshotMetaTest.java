package org.nowstart.kradmincodes.sdk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotMetaTest {

    private final Map<String, byte[]> files = AdminCodesQueryTest.sample().files();
    private final String meta = new String(files.get(SnapshotFiles.META), UTF_8);

    @Test
    void parsesValidMeta() {
        SnapshotMeta parsed = SnapshotMeta.parse(meta);

        assertThat(parsed.dataDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(parsed.sha256Of(SnapshotFiles.ADMIN)).isEqualTo(SnapshotFiles.sha256Hex(files.get(SnapshotFiles.ADMIN)));
    }

    @Test
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> SnapshotMeta.parse(meta.replace("format\t1", "format\t2")))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("형식");
    }

    @Test
    void rejectsMissingKeyAndDuplicateKey() {
        assertThatThrownBy(() -> SnapshotMeta.parse(meta.replaceAll("sha256\\.succession\\.tsv\t[0-9a-f]+\n", "")))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("sha256.succession.tsv");
        assertThatThrownBy(() -> SnapshotMeta.parse(meta + "format\t1\n"))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("두 번");
    }

    @Test
    void rejectsVersionThatDoesNotMatchHashes() {
        String wrongVersion = meta.replaceAll("version\t[0-9a-f]+", "version\t" + "0".repeat(64));

        assertThatThrownBy(() -> SnapshotMeta.parse(wrongVersion))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsBadDateAndBadHeader() {
        assertThatThrownBy(() -> SnapshotMeta.parse(meta.replace("data_date\t2026-11-11", "data_date\t2026-13-01")))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("data_date");
        assertThatThrownBy(() -> SnapshotMeta.parse("k\tv\n"))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("헤더");
    }

    @Test
    void parserRejectsRowsWithWrongColumnCountOrBadValues() {
        Map<String, String> text = new java.util.HashMap<>();
        files.forEach((name, bytes) -> text.put(name, new String(bytes, UTF_8)));
        SnapshotMeta parsed = SnapshotMeta.parse(meta);

        Map<String, String> wrongColumns = new java.util.HashMap<>(text);
        wrongColumns.put(SnapshotFiles.ADMIN, text.get(SnapshotFiles.ADMIN) + "1100000000\t서울\n");
        assertThatThrownBy(() -> SnapshotParser.parse(parsed, wrongColumns))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("열 수");

        Map<String, String> badDate = new java.util.HashMap<>(text);
        badDate.put(SnapshotFiles.LEGAL, text.get(SnapshotFiles.LEGAL) + "1111010100\t\t\t\t\t1988-99-01\t\n");
        assertThatThrownBy(() -> SnapshotParser.parse(parsed, badDate))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining(SnapshotFiles.LEGAL);

        Map<String, String> duplicate = new java.util.HashMap<>(text);
        duplicate.put(SnapshotFiles.ADMIN, text.get(SnapshotFiles.ADMIN) + "4200000000\t강원도\t\t\t1988-04-23\t\n");
        assertThatThrownBy(() -> SnapshotParser.parse(parsed, duplicate))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void rejectsBytesThatAreNotUtf8() {
        assertThatThrownBy(() -> SnapshotFiles.utf8("x.tsv", new byte[] {(byte) 0xFF, (byte) 0xFE}))
                .isInstanceOf(SnapshotException.class)
                .hasMessageContaining("UTF-8");
    }
}
