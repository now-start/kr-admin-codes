package org.nowstart.kradmincodes.pipeline.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nowstart.kradmincodes.pipeline.TestRows.admin;
import static org.nowstart.kradmincodes.pipeline.TestRows.date;
import static org.nowstart.kradmincodes.pipeline.TestRows.legal;
import static org.nowstart.kradmincodes.pipeline.TestRows.link;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.pipeline.Dataset;
import org.nowstart.kradmincodes.pipeline.PipelineException;
import org.nowstart.kradmincodes.pipeline.SourceInfo;
import org.nowstart.kradmincodes.pipeline.derive.Unresolved;

class TsvRoundTripTest {

    private static final SourceInfo SOURCE = new SourceInfo(
            date("2026-11-11"), "ab12", "https://example.test/post", "변경내역 게시글", date("2026-11-18"));

    @TempDir
    Path dir;

    @Test
    void writesSortedFilesAndReadsThemBack() throws IOException {
        TsvWriter.write(dir, unsorted(), SOURCE);

        assertThat(Files.readString(dir.resolve(DatasetLayout.ADMIN_FILE), StandardCharsets.UTF_8)).isEqualTo("""
                code\tsido\tsigungu\tdong\tvalid_from\tvalid_to
                1100000000\t서울특별시\t\t\t1988-04-23\t
                1111051000\t서울특별시\t종로구\t청운동\t1988-04-23\t2008-11-01
                """);
        assertThat(TsvReader.read(dir)).contains(sorted());
    }

    @Test
    void sameInputWritesIdenticalBytes() throws IOException {
        TsvWriter.write(dir, unsorted(), SOURCE);
        byte[] first = Files.readAllBytes(dir.resolve(DatasetLayout.LINK_FILE));
        TsvWriter.write(dir, sorted(), SOURCE);

        assertThat(Files.readAllBytes(dir.resolve(DatasetLayout.LINK_FILE))).isEqualTo(first);
    }

    @Test
    void writesSourceInfo() throws IOException {
        TsvWriter.write(dir, sorted(), SOURCE);

        assertThat(Files.readString(dir.resolve(DatasetLayout.SOURCE_FILE), StandardCharsets.UTF_8))
                .contains("data_date\t2026-11-11")
                .contains("post_title\t변경내역 게시글")
                .contains("posted_date\t2026-11-18");
    }

    @Test
    void writesMetaWithPerFileSha256AndContentVersion() throws Exception {
        TsvWriter.write(dir, sorted(), SOURCE);

        Map<String, String> meta = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(dir.resolve(DatasetLayout.META_FILE), StandardCharsets.UTF_8);
        assertThat(lines.get(0)).isEqualTo("key\tvalue");
        lines.subList(1, lines.size()).forEach(line -> meta.put(line.split("\t")[0], line.split("\t")[1]));

        assertThat(meta).containsEntry("format", "1").containsEntry("data_date", "2026-11-11");
        var versionInput = new StringBuilder();
        for (String file : DatasetLayout.SNAPSHOT_FILES.stream().sorted().toList()) {
            String hash = sha256(Files.readAllBytes(dir.resolve(file)));
            assertThat(meta).containsEntry("sha256." + file, hash);
            versionInput.append(file).append(':').append(hash).append('\n');
        }
        assertThat(meta).containsEntry("version", sha256(versionInput.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsTabInsideValue() {
        Dataset bad = new Dataset(
                List.of(admin("1100000000", "서울\t특별시", "", "", "1988-04-23", null)),
                List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> TsvWriter.write(dir, bad, SOURCE))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("탭");
    }

    @Test
    void readingEmptyDirectoryMeansNoPreviousData() {
        assertThat(TsvReader.read(dir)).isEmpty();
    }

    @Test
    void readRejectsChangedHeaderAndBrokenRows() throws IOException {
        TsvWriter.write(dir, sorted(), SOURCE);
        Path admin = dir.resolve(DatasetLayout.ADMIN_FILE);

        Files.writeString(admin, "code\tname\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TsvReader.read(dir)).isInstanceOf(PipelineException.class).hasMessageContaining("헤더");

        Files.writeString(admin, "code\tsido\tsigungu\tdong\tvalid_from\tvalid_to\n1100000000\t서울\n",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TsvReader.read(dir)).isInstanceOf(PipelineException.class).hasMessageContaining("2번째 줄");
    }

    private static String sha256(byte[] data) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Dataset unsorted() {
        return new Dataset(
                List.of(
                        admin("1111051000", "서울특별시", "종로구", "청운동", "1988-04-23", "2008-11-01"),
                        admin("1100000000", "서울특별시", "", "", "1988-04-23", null)),
                List.of(legal("1111010200", "1988-04-23", null), legal("1111010100", "1988-04-23", null)),
                List.of(
                        link("1111051500", "1111010200", "2008-11-01", null),
                        link("1111051500", "1111010100", "2008-11-01", null)),
                List.of(new Succession("4200000000", "5100000000", date("2023-06-11"), SuccessionMethod.NAME_MATCH)),
                List.of(new Unresolved("2814000000", "인천광역시", "동구", "", date("2026-07-01"))));
    }

    private static Dataset sorted() {
        Dataset d = unsorted();
        return new Dataset(
                List.of(d.adminDongs().get(1), d.adminDongs().get(0)),
                List.of(d.legalDongs().get(1), d.legalDongs().get(0)),
                List.of(d.links().get(1), d.links().get(0)),
                d.successions(),
                d.unresolved());
    }
}
