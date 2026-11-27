package org.nowstart.kradmincodes.pipeline.write;

import static java.util.Comparator.comparing;
import static org.nowstart.kradmincodes.pipeline.write.Tsv.date;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.AdminLegalLink;
import org.nowstart.kradmincodes.model.LegalDong;
import org.nowstart.kradmincodes.model.Succession;
import org.nowstart.kradmincodes.pipeline.Dataset;
import org.nowstart.kradmincodes.pipeline.PipelineException;
import org.nowstart.kradmincodes.pipeline.SourceInfo;
import org.nowstart.kradmincodes.pipeline.derive.Unresolved;

/**
 * {@link Dataset}을 {@code data/v1/}에 쓴다. 정렬 키가 고정이라 같은 입력이면 같은 바이트가 나오고, 월별 git diff가 바뀐 행만
 * 보여준다. 전부 렌더링해서 값을 검사한 뒤에 쓰고, 파일마다 임시 파일 → 원자적 이동으로 바꾼다.
 *
 * <p>여러 파일을 한꺼번에 원자적으로 바꾸지는 않는다. 이동 도중 실패하면 예외(종료 코드 1)로 드러나고, {@code data/v1}은 git에
 * 있으므로 {@code git checkout data/v1}로 되돌린 뒤 다시 실행한다.
 */
public final class TsvWriter {

    private static final Comparator<AdminLegalLink> LINK_ORDER = comparing(AdminLegalLink::adminCode)
            .thenComparing(AdminLegalLink::legalCode)
            .thenComparing(link -> link.validity().from());
    private static final Comparator<Succession> SUCCESSION_ORDER = comparing(Succession::fromCode)
            .thenComparing(Succession::effectiveDate)
            .thenComparing(Succession::toCode);
    private static final Comparator<Unresolved> UNRESOLVED_ORDER =
            comparing(Unresolved::abolishedOn).thenComparing(Unresolved::code);

    private TsvWriter() {
    }

    public static void write(Path dir, Dataset dataset, SourceInfo source) {
        Map<String, String> files = render(dataset, source);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new PipelineException(dir + " 디렉터리를 만들 수 없습니다", e);
        }
        files.forEach((name, content) -> writeAtomically(dir.resolve(name), content));
    }

    private static Map<String, String> render(Dataset dataset, SourceInfo source) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(DatasetLayout.ADMIN_FILE, table(DatasetLayout.ADMIN_HEADER,
                dataset.adminDongs(), comparing(AdminDong::code), r -> List.of(
                        r.code(), r.sido(), r.sigungu(), r.dong(),
                        date(r.validity().from()), date(r.validity().toExclusive()))));
        files.put(DatasetLayout.LEGAL_FILE, table(DatasetLayout.LEGAL_HEADER,
                dataset.legalDongs(), comparing(LegalDong::code), r -> List.of(
                        r.code(), r.sido(), r.sigungu(), r.eupmyeondong(), r.ri(),
                        date(r.validity().from()), date(r.validity().toExclusive()))));
        files.put(DatasetLayout.LINK_FILE, table(DatasetLayout.LINK_HEADER, dataset.links(), LINK_ORDER, r -> List.of(
                r.adminCode(), r.legalCode(), date(r.validity().from()), date(r.validity().toExclusive()))));
        files.put(DatasetLayout.SUCCESSION_FILE, table(DatasetLayout.SUCCESSION_HEADER,
                dataset.successions(), SUCCESSION_ORDER, r -> List.of(
                        r.fromCode(), r.toCode(), date(r.effectiveDate()), r.method().name())));
        files.put(DatasetLayout.UNRESOLVED_FILE, table(DatasetLayout.UNRESOLVED_HEADER,
                dataset.unresolved(), UNRESOLVED_ORDER, r -> List.of(
                        r.code(), r.sido(), r.sigungu(), r.dong(), date(r.abolishedOn()))));
        files.put(DatasetLayout.SOURCE_FILE, sourceTable(source));
        return files;
    }

    private static <T> String table(
            List<String> header, List<T> rows, Comparator<T> order, Function<T, List<String>> cells) {
        var out = new StringBuilder();
        out.append(Tsv.line(header)).append('\n');
        rows.stream().sorted(order).forEach(row -> out.append(Tsv.line(cells.apply(row))).append('\n'));
        return out.toString();
    }

    private static String sourceTable(SourceInfo source) {
        List<List<String>> entries = List.of(
                List.of("data_date", date(source.dataDate())),
                List.of("archive_sha256", source.archiveSha256()),
                List.of("post_url", source.postUrl()),
                List.of("post_title", source.postTitle()),
                List.of("posted_date", date(source.postedDate())));
        return table(DatasetLayout.SOURCE_HEADER, entries, (a, b) -> 0, Function.identity());
    }

    private static void writeAtomically(Path target, String content) {
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            var failure = new PipelineException(target + "에 쓸 수 없습니다", e);
            deleteTemp(temp, failure);
            throw failure;
        }
    }

    private static void deleteTemp(Path temp, PipelineException failure) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException suppressed) {
            failure.addSuppressed(suppressed);
        }
    }
}
