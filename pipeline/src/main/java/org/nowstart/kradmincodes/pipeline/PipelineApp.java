package org.nowstart.kradmincodes.pipeline;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.nowstart.kradmincodes.model.AdminDong;
import org.nowstart.kradmincodes.model.SuccessionMethod;
import org.nowstart.kradmincodes.pipeline.derive.DerivationResult;
import org.nowstart.kradmincodes.pipeline.derive.OverrideLoader;
import org.nowstart.kradmincodes.pipeline.derive.SuccessionDeriver;
import org.nowstart.kradmincodes.pipeline.derive.SuccessionOverride;
import org.nowstart.kradmincodes.pipeline.parse.JscodeArchive;
import org.nowstart.kradmincodes.pipeline.parse.JscodeArchiveReader;
import org.nowstart.kradmincodes.pipeline.parse.RecordMapper;
import org.nowstart.kradmincodes.pipeline.source.BoardPost;
import org.nowstart.kradmincodes.pipeline.source.HttpFetcher;
import org.nowstart.kradmincodes.pipeline.source.MoisBoardClient;
import org.nowstart.kradmincodes.pipeline.source.SourceRelease;
import org.nowstart.kradmincodes.pipeline.validate.DatasetValidator;
import org.nowstart.kradmincodes.pipeline.validate.KnownCases;
import org.nowstart.kradmincodes.pipeline.validate.ValidationReport;
import org.nowstart.kradmincodes.pipeline.write.TsvReader;
import org.nowstart.kradmincodes.pipeline.write.TsvWriter;

/** 파이프라인 CLI. 사용법은 {@link #USAGE}. 검증 오류가 있으면 아무것도 쓰지 않고 종료 코드 1로 끝난다. */
public final class PipelineApp {

    static final Path DEFAULT_OUT = Path.of("data", "v1");
    static final Path DEFAULT_OVERRIDES = Path.of("overrides", "successions.tsv");

    private static final String USAGE = """
            사용법:
              check-source                                        최신 게시글과 첨부만 확인 (zip은 받지 않음)
              build (--archive <zip> | --from-mois) [--out <dir>] [--overrides <tsv>]
            """;

    private static final System.Logger LOG = System.getLogger(PipelineApp.class.getName());

    private PipelineApp() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(List.of(args));
        } catch (PipelineException e) {
            LOG.log(ERROR, e.getMessage(), e);
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(List<String> args) {
        if (args.isEmpty()) {
            throw new PipelineException(USAGE);
        }
        return switch (args.get(0)) {
            case "check-source" -> checkSource();
            case "build" -> build(BuildOptions.parse(args.subList(1, args.size())));
            default -> throw new PipelineException("알 수 없는 명령: " + args.get(0) + "\n" + USAGE);
        };
    }

    private static int checkSource() {
        SourceRelease release = new MoisBoardClient(new HttpFetcher()).findLatestRelease();
        System.out.println(describe(release));
        return 0;
    }

    private static int build(BuildOptions options) {
        Obtained obtained = obtain(options);
        JscodeArchive archive = new JscodeArchiveReader().read(obtained.zip());
        SourceInfo source = sourceInfo(obtained, archive);
        Dataset dataset = toDataset(archive, OverrideLoader.load(options.overrides()));

        ValidationReport report = validate(dataset, options.out(), archive.dataDate());
        report.warnings().forEach(warning -> LOG.log(WARNING, warning));
        if (report.hasErrors()) {
            report.errors().forEach(error -> LOG.log(ERROR, error));
            LOG.log(ERROR, "검증 오류 " + report.errors().size() + "건: " + options.out() + "에 쓰지 않았습니다");
            return 1;
        }
        TsvWriter.write(options.out(), dataset, source);
        LOG.log(INFO, summary(dataset, source, options.out()));
        return 0;
    }

    private static Obtained obtain(BuildOptions options) {
        if (options.archive() != null) {
            return new Obtained(readArchive(options.archive()), null);
        }
        var client = new MoisBoardClient(new HttpFetcher());
        SourceRelease release = client.findLatestRelease();
        LOG.log(INFO, "원천: " + describe(release));
        return new Obtained(client.download(release), release);
    }

    private static byte[] readArchive(Path path) {
        try {
            if (Files.size(path) > MoisBoardClient.MAX_ARCHIVE_BYTES) {
                throw new PipelineException(path + " 파일이 상한(" + MoisBoardClient.MAX_ARCHIVE_BYTES + "바이트)보다 큽니다");
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new PipelineException(path + "을(를) 읽을 수 없습니다", e);
        }
    }

    private static SourceInfo sourceInfo(Obtained obtained, JscodeArchive archive) {
        String sha256 = HexFormat.of().formatHex(sha256(obtained.zip()));
        SourceRelease release = obtained.release();
        if (release == null) {
            return SourceInfo.localArchive(archive.dataDate(), sha256);
        }
        if (!release.attachment().dataDate().equals(archive.dataDate())) {
            throw new PipelineException("첨부 이름의 기준일(" + release.attachment().dataDate()
                    + ")과 zip 안 파일의 기준일(" + archive.dataDate() + ")이 다릅니다");
        }
        BoardPost post = release.post();
        return new SourceInfo(archive.dataDate(), sha256, post.uri().toString(), post.title(), post.postedDate());
    }

    private static Dataset toDataset(JscodeArchive archive, List<SuccessionOverride> overrides) {
        List<AdminDong> admin = RecordMapper.adminDongs(archive.admin());
        DerivationResult derived = new SuccessionDeriver(DatasetValidator.GUARANTEED_FROM).derive(admin, overrides);
        return new Dataset(
                admin,
                RecordMapper.legalDongs(archive.legal()),
                RecordMapper.links(archive.link()),
                derived.successions(),
                derived.unresolved());
    }

    private static ValidationReport validate(Dataset dataset, Path out, LocalDate asOf) {
        var validator = new DatasetValidator(DatasetValidator.GUARANTEED_FROM, KnownCases.DEFAULT);
        return TsvReader.read(out)
                .map(previous -> validator.validate(dataset, previous, asOf))
                .orElseGet(() -> validator.validate(dataset));
    }

    private static String describe(SourceRelease release) {
        BoardPost post = release.post();
        return post.title() + " (게시 " + post.postedDate() + ", nttId " + post.nttId() + ") → "
                + release.attachment().fileName() + " " + release.attachment().uri();
    }

    private static String summary(Dataset dataset, SourceInfo source, Path out) {
        long manual = dataset.successions().stream().filter(s -> s.method() == SuccessionMethod.MANUAL).count();
        return out + "에 썼습니다: 기준일 " + source.dataDate()
                + ", 행정동 " + dataset.adminDongs().size()
                + ", 법정동 " + dataset.legalDongs().size()
                + ", 매핑 " + dataset.links().size()
                + ", 변환 " + dataset.successions().size() + "(수동 " + manual + ")"
                + ", 미해결 " + dataset.unresolved().size();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    /** 받은 zip과, 게시판에서 받았다면 그 릴리스(로컬 zip이면 null). */
    private record Obtained(byte[] zip, SourceRelease release) {
    }

    private record BuildOptions(Path archive, boolean fromMois, Path out, Path overrides) {

        static BuildOptions parse(List<String> args) {
            Path archive = null;
            boolean fromMois = false;
            Path out = DEFAULT_OUT;
            Path overrides = DEFAULT_OVERRIDES;
            for (int i = 0; i < args.size(); i++) {
                String option = args.get(i);
                switch (option) {
                    case "--archive" -> archive = Path.of(value(args, ++i, option));
                    case "--from-mois" -> fromMois = true;
                    case "--out" -> out = Path.of(value(args, ++i, option));
                    case "--overrides" -> overrides = Path.of(value(args, ++i, option));
                    default -> throw new PipelineException("알 수 없는 옵션: " + option + "\n" + USAGE);
                }
            }
            if ((archive != null) == fromMois) {
                throw new PipelineException("--archive와 --from-mois 중 하나만 지정하세요\n" + USAGE);
            }
            return new BuildOptions(archive, fromMois, out, overrides);
        }

        private static String value(List<String> args, int index, String option) {
            if (index >= args.size()) {
                throw new PipelineException(option + " 값이 없습니다\n" + USAGE);
            }
            return args.get(index);
        }
    }
}
