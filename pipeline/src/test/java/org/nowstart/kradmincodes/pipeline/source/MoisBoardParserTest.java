package org.nowstart.kradmincodes.pipeline.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.nowstart.kradmincodes.pipeline.PipelineException;

/** 실제 게시판 구조를 본뜬 합성 HTML로 검증한다 (실제 페이지는 저장하지 않는다). */
class MoisBoardParserTest {

    private static final String ARTICLE = "/frt/bbs/type001/commonSelectBoardArticle.do;jsessionid=abc.node50"
            + "?bbsId=BBSMSTR_000000000052&amp;nttId=";

    private static final String LIST = """
            <table class="table_style1 mobile">
              <tbody>
                <tr><td>공지</td><td class="l"><div class="wrap"><a href="%1$s100">오래된 공지</a></div></td>
                    <td></td><td>주민과</td><td> 2020.01.02.</td><td>10</td></tr>
                <tr><td>2</td><td class="l"><div class="wrap"><a href="%1$s300">홍보 포스터 게시</a></div></td>
                    <td></td><td>주민과</td><td> 2026.11.20.</td><td>5</td></tr>
                <tr><td>1</td><td class="l"><div class="wrap"><a href="%1$s200">변경내역(2026.10.23. 시행)</a></div></td>
                    <td></td><td>주민과</td><td> 2026.11.18.</td><td>7</td></tr>
                <tr><td colspan="6">구분선</td></tr>
              </tbody>
            </table>
            """.formatted(ARTICLE);

    private static final String POST_WITH_JSCODE = """
            <ul>
              <li><a href="/cmm/fms/FileDown.do?atchFileId=FILE_X&amp;fileSn=0">[붙임] 변경내역.hwpx &nbsp;[ 68.4 KB ]</a></li>
              <li><a href="/cmm/fms/FileDown.do?atchFileId=FILE_X&amp;fileSn=1">jscode20261111.zip &nbsp;[ 2.0 MB ]</a></li>
              <li><a href="/cmm/fms/FileDown.do;jsessionid=abc?atchFileId=FILE_X&amp;fileSn=2">
                  jscode20261111(말소코드포함).zip &nbsp;[ 5.2 MB ]</a></li>
            </ul>
            """;

    @Test
    void parsesPostsWithoutSessionIdInUri() {
        List<BoardPost> posts = MoisBoardParser.parseList(LIST);

        assertThat(posts).extracting(BoardPost::nttId).containsExactly("100", "300", "200");
        BoardPost change = posts.get(2);
        assertThat(change.title()).isEqualTo("변경내역(2026.10.23. 시행)");
        assertThat(change.postedDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(change.uri()).hasToString(
                "https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do"
                        + "?bbsId=BBSMSTR_000000000052&nttId=200");
    }

    @Test
    void failsLoudlyWhenPostedDateIsMissing() {
        String broken = "<table class=\"table_style1\"><tr><td><a href=\"" + ARTICLE + "1\">제목</a></td></tr></table>";

        assertThatThrownBy(() -> MoisBoardParser.parseList(broken))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("게시일");
    }

    @Test
    void picksOnlyTheArchiveThatIncludesAbolishedCodes() {
        List<Attachment> attachments = MoisBoardParser.parseAttachments(POST_WITH_JSCODE);

        assertThat(attachments).singleElement().satisfies(a -> {
            assertThat(a.fileName()).isEqualTo("jscode20261111(말소코드포함).zip");
            assertThat(a.dataDate()).isEqualTo(LocalDate.of(2026, 11, 11));
            assertThat(a.uri()).hasToString("https://www.mois.go.kr/cmm/fms/FileDown.do?atchFileId=FILE_X&fileSn=2");
        });
    }

    @Test
    void refusesLinksOutsideTheBoardHost() {
        String foreign = "<a href=\"http://evil.example/cmm/fms/FileDown.do?x=1\">jscode20261111(말소코드포함).zip</a>";

        assertThatThrownBy(() -> MoisBoardParser.parseAttachments(foreign))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("evil.example");
    }

    @Test
    void selectsNewestPostThatHasTheArchiveAndStopsThere() {
        List<BoardPost> posts = MoisBoardParser.parseList(LIST);
        Map<String, String> pages = Map.of("300", "<p>첨부 없음</p>", "200", POST_WITH_JSCODE);
        List<String> visited = new ArrayList<>();

        SourceRelease release = MoisBoardParser.selectRelease(posts, 10, post -> {
            visited.add(post.nttId());
            return MoisBoardParser.parseAttachments(pages.getOrDefault(post.nttId(), ""));
        });

        assertThat(release.post().nttId()).isEqualTo("200");
        assertThat(release.attachment().dataDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(visited).containsExactly("300", "200");
    }

    @Test
    void failsWhenNoRecentPostHasTheArchive() {
        List<BoardPost> posts = List.of(new BoardPost(
                "1", "홍보", LocalDate.of(2026, 1, 1), URI.create("https://www.mois.go.kr/x")));

        assertThatThrownBy(() -> MoisBoardParser.selectRelease(posts, 10, post -> List.of()))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("말소코드포함");
    }
}
