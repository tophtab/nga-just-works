package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import sp.phone.http.bean.ThreadRowInfo;

public class SummaryInputTest {

    @Test
    public void clickedFloorIsAnImmutableSnapshotWithoutCommentsOrAccountFields() {
        ThreadRowInfo selected = row("Selected author", "[b]Selected floor[/b]<br/>Only this body");
        selected.setTid(123);
        selected.setPid(456);
        selected.setLou(7);
        selected.setSignature("PRIVATE_SIGNATURE_SENTINEL");
        selected.setFormattedHtmlData("OTHER_RENDERED_FLOORS_SENTINEL");
        selected.setComments(Collections.singletonList(row("Other author", "OTHER_COMMENT_SENTINEL")));

        FloorSummaryInput snapshot = FloorSummaryInput.fromRow("Thread &amp; title", selected);
        selected.setContent("LATER_MUTATION_SENTINEL");
        selected.setAuthor("LATER_AUTHOR_SENTINEL");
        selected.setLou(99);

        String prompt = snapshot.toPrompt();
        assertTrue(prompt.contains("Thread & title"));
        assertTrue(prompt.contains("楼层：7"));
        assertTrue(prompt.contains("Selected author"));
        assertTrue(prompt.contains("Selected floor\nOnly this body"));
        assertEquals("floor:123:456:7", snapshot.getTarget());
        assertFalse(prompt.contains("SENTINEL"));
        assertFalse(prompt.contains("[b]"));
    }

    @Test
    public void plainTextKeepsVisibleTextAndQuotesWithoutRenderingOrRetainingMediaUrls() {
        String result = SummaryText.plain("<script>HIDDEN_SECRET</script><style>HIDDEN_STYLE</style>"
                + "<p>文本 &amp; &#x1F600;</p>[url=https://unused.invalid]标签[/url]"
                + "[img]https://unused.invalid/media.jpg[/img][quote][b]引用文字[/b][/quote]"
                + "<br/>自己的正文", 1000);
        assertTrue(result.contains("文本 & 😀"));
        assertTrue(result.contains("标签"));
        assertTrue(result.contains("引用：\n引用文字\n引用结束"));
        assertTrue(result.contains("自己的正文"));
        assertFalse(result.contains("https://"));
        assertFalse(result.contains("HIDDEN"));
        assertFalse(result.contains("[b]"));
    }

    @Test
    public void emptyAndLongInputsAreBoundedWithoutBrokenSurrogatePairs() {
        assertEquals("", SummaryText.plain(null, 50));
        assertEquals("", SummaryText.plain(" \u00a0\u3000\n", 50));
        assertEquals("a", SummaryText.limit("a😀b", 2));
        ThreadRowInfo selected = row("Author", repeat('a', 20000));
        assertEquals(12000, FloorSummaryInput.fromRow(null, selected).getBody().length());
    }

    @Test
    public void profileInputCopiesAndBoundsEachPageAndReply() {
        List<ProfileSummaryInput.Entry> topics = new ArrayList<>();
        List<ProfileSummaryInput.Entry> replies = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            topics.add(new ProfileSummaryInput.Entry("Topic " + i, "Board", "2026-01-01", "IGNORED_TOPIC_BODY"));
            replies.add(new ProfileSummaryInput.Entry("Reply topic " + i, "Board", "2026-01-02",
                    repeat('x', 1000) + "TRUNCATED_REPLY_SENTINEL"));
        }
        ProfileSummaryInput snapshot = new ProfileSummaryInput("4200", "Viewed user", topics, replies);
        topics.clear();
        replies.clear();

        String prompt = snapshot.toPrompt();
        assertEquals("4200", snapshot.getUid());
        assertTrue(prompt.contains("当前资料页 UID：4200"));
        assertTrue(prompt.contains("Viewed user"));
        assertTrue(prompt.contains("Topic 19"));
        assertFalse(prompt.contains("Topic 20"));
        assertFalse(prompt.contains("Reply topic 20"));
        assertFalse(prompt.contains("SENTINEL"));
        assertFalse(prompt.contains("IGNORED_TOPIC_BODY"));
        assertTrue(prompt.length() < 32000);
    }

    @Test
    public void profileWithoutVisibleActivityIsEmpty() {
        assertTrue(new ProfileSummaryInput("42", "User", null, null).isEmpty());
    }

    private static ThreadRowInfo row(String author, String content) {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setAuthor(author);
        row.setContent(content);
        row.setSubject("Synthetic topic");
        return row;
    }

    static String repeat(char value, int count) {
        char[] output = new char[count];
        java.util.Arrays.fill(output, value);
        return new String(output);
    }
}
