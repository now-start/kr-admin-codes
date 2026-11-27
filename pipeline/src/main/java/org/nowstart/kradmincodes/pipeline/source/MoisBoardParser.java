package org.nowstart.kradmincodes.pipeline.source;

import static java.util.Comparator.comparing;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/**
 * 행안부 주민등록 게시판 HTML을 읽는 순수 함수 모음. 네트워크는 {@link MoisBoardClient}가 맡는다.
 *
 * <p>게시글은 제목이 아니라 첨부파일 이름({@code jscodeYYYYMMDD(말소코드포함).zip})으로 찾는다. 제목 형식은 시기마다 바뀌어
 * 왔고, 제목으로 찾던 기존 수집기(kr-juso)는 그 때문에 멈췄다.
 */
public final class MoisBoardParser {

    public static final URI BASE = URI.create("https://www.mois.go.kr");
    public static final URI LIST_URI =
            BASE.resolve("/frt/bbs/type001/commonSelectBoardList.do?bbsId=BBSMSTR_000000000052");

    private static final Pattern JSESSIONID = Pattern.compile(";jsessionid=[^?#]*");
    private static final Pattern NTT_ID = Pattern.compile("[?&]nttId=(\\d+)");
    private static final Pattern POSTED_DATE = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2}\\.");
    private static final Pattern JSCODE_ARCHIVE = Pattern.compile("jscode(\\d{8})\\(말소코드포함\\)\\.zip");
    private static final DateTimeFormatter POSTED_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd.");

    /** 게시일 최신순, 같은 날이면 글 번호가 큰 순. 상단 고정 공지가 섞여 있어도 최신 글부터 본다. */
    private static final Comparator<BoardPost> NEWEST_FIRST = comparing(BoardPost::postedDate)
            .thenComparing(post -> Long.parseLong(post.nttId()))
            .reversed();

    private MoisBoardParser() {
    }

    public static List<BoardPost> parseList(String html) {
        Document doc = Jsoup.parse(html, BASE.toString());
        List<BoardPost> posts = new ArrayList<>();
        for (Element row : doc.select("table.table_style1 tr")) {
            Element link = row.selectFirst("a[href*=commonSelectBoardArticle]");
            if (link == null) {
                continue;
            }
            String href = stripSession(link.attr("href"));
            Matcher nttId = NTT_ID.matcher(href);
            if (!nttId.find()) {
                continue;
            }
            posts.add(new BoardPost(nttId.group(1), link.text().strip(), postedDate(row, href), onBoard(href)));
        }
        return List.copyOf(posts);
    }

    public static List<Attachment> parseAttachments(String html) {
        Document doc = Jsoup.parse(html, BASE.toString());
        List<Attachment> attachments = new ArrayList<>();
        for (Element link : doc.select("a[href*=FileDown.do]")) {
            Matcher name = JSCODE_ARCHIVE.matcher(link.text());
            if (name.find()) {
                LocalDate dataDate = LocalDate.parse(name.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                attachments.add(new Attachment(name.group(), dataDate, onBoard(stripSession(link.attr("href")))));
            }
        }
        return List.copyOf(attachments);
    }

    /**
     * 최신 게시글부터 최대 {@code maxPosts}개를 열어 보고, 말소코드포함 zip이 처음 나오는 게시글을 고른다. 한 게시글에 여러 개면
     * 기준일이 가장 늦은 것.
     */
    public static SourceRelease selectRelease(
            List<BoardPost> posts, int maxPosts, Function<BoardPost, List<Attachment>> attachmentsOf) {
        return posts.stream()
                .sorted(NEWEST_FIRST)
                .limit(maxPosts)
                .map(post -> latest(attachmentsOf.apply(post)).map(attachment -> new SourceRelease(post, attachment)))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new PipelineException(
                        "최근 게시글 " + maxPosts + "개에서 jscode(말소코드포함).zip 첨부를 찾지 못했습니다. 게시판 구조가 바뀌었을 수 있습니다"));
    }

    private static Optional<Attachment> latest(List<Attachment> attachments) {
        return attachments.stream().max(comparing(Attachment::dataDate));
    }

    private static LocalDate postedDate(Element row, String href) {
        // 제목에도 "(2026.10.23. 시행)" 같은 날짜가 있으므로, 칸 전체가 날짜인 것만 게시일로 본다
        for (Element cell : row.select("td")) {
            String text = cell.text().strip();
            if (POSTED_DATE.matcher(text).matches()) {
                return LocalDate.parse(text, POSTED_FORMAT);
            }
        }
        throw new PipelineException("게시글의 게시일을 찾을 수 없습니다. 목록 구조가 바뀌었을 수 있습니다: " + href);
    }

    /** HTML이 준 주소가 게시판 밖(다른 호스트, http)이면 따라가지 않는다. */
    private static URI onBoard(String href) {
        URI uri = BASE.resolve(href);
        if (!BASE.getScheme().equals(uri.getScheme()) || !BASE.getHost().equals(uri.getHost())) {
            throw new PipelineException("게시판 밖 주소는 따라가지 않습니다: " + uri);
        }
        return uri;
    }

    private static String stripSession(String href) {
        return JSESSIONID.matcher(href).replaceAll("");
    }
}
