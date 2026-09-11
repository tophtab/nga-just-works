package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        assertTrue(prompt.contains("回复正文在1000字以内"));
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
        topics.add(null);
        replies.add(null);
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
        assertTrue(prompt.contains("样本数量：主题 20 条，回复 20 条\n"));
        assertEvidenceNumbers(prompt, "主题", 20);
        assertEvidenceNumbers(prompt, "回复", 20);
        assertTrue(prompt.contains("当前资料页 UID：4200"));
        assertTrue(prompt.contains("回复正文在1000字以内"));
        assertFalse(prompt.contains("全文不超过 500 字"));
        assertTrue(prompt.contains("Viewed user"));
        assertTrue(prompt.contains("Topic 19"));
        assertFalse(prompt.contains("Topic 20"));
        assertFalse(prompt.contains("Reply topic 20"));
        assertFalse(prompt.contains("SENTINEL"));
        assertFalse(prompt.contains("IGNORED_TOPIC_BODY"));
        assertTrue(prompt.length() < 32000);
    }

    @Test
    public void profileCountsAndIdentifiersExcludeNullEntriesAndKeepEachKindsOrder() {
        List<ProfileSummaryInput.Entry> topics = Arrays.asList(null,
                new ProfileSummaryInput.Entry("First topic", "Topic board", "2026-01-01", ""), null,
                new ProfileSummaryInput.Entry("Second topic", "Topic board", "2026-01-02", ""), null);
        List<ProfileSummaryInput.Entry> replies = Arrays.asList(null,
                new ProfileSummaryInput.Entry("First reply topic", "Reply board", "2026-01-03", "First reply"),
                new ProfileSummaryInput.Entry("Second reply topic", "Reply board", "2026-01-04", "Second reply"),
                null, new ProfileSummaryInput.Entry("Third reply topic", "Reply board", "2026-01-05", "Third reply"));

        String prompt = new ProfileSummaryInput("42", "User", topics, replies).toPrompt();
        assertTrue(prompt.contains("样本数量：主题 2 条，回复 3 条\n"));
        assertEvidenceNumbers(prompt, "主题", 2);
        assertEvidenceNumbers(prompt, "回复", 3);
        assertTrue(prompt.contains("[主题1] First topic | Topic board | 2026-01-01\n"));
        assertTrue(prompt.contains("[主题2] Second topic | Topic board | 2026-01-02\n"));
        assertTrue(prompt.contains("[回复1] First reply topic | Reply board | 2026-01-03\n回复正文：First reply\n"));
        assertTrue(prompt.contains("[回复3] Third reply topic | Reply board | 2026-01-05\n回复正文：Third reply\n"));
    }

    @Test
    public void profileWithEitherPageEmptyKeepsAccurateCountsAndReplyIsolation() {
        ProfileSummaryInput.Entry entry = new ProfileSummaryInput.Entry("Visible topic", "Board", "2026-01-01",
                "[quote]Quoted claim[/quote]My reply [img]https://unused.invalid/media.jpg[/img]");
        ProfileSummaryInput onlyReplies = new ProfileSummaryInput("42", "User",
                Collections.emptyList(), Collections.singletonList(entry));
        assertFalse(onlyReplies.isEmpty());
        String repliesPrompt = onlyReplies.toPrompt();
        assertTrue(repliesPrompt.contains("样本数量：主题 0 条，回复 1 条\n"));
        assertTrue(repliesPrompt.contains("无可见主题\n"));
        assertFalse(repliesPrompt.contains("无可见回复\n"));
        assertEvidenceNumbers(repliesPrompt, "主题", 0);
        assertEvidenceNumbers(repliesPrompt, "回复", 1);
        assertTrue(repliesPrompt.contains("引用：\nQuoted claim\n引用结束"));
        assertTrue(repliesPrompt.contains("My reply"));
        assertFalse(repliesPrompt.contains("https://unused.invalid"));

        ProfileSummaryInput onlyTopics = new ProfileSummaryInput("42", "User",
                Collections.singletonList(entry), Collections.emptyList());
        assertFalse(onlyTopics.isEmpty());
        String topicsPrompt = onlyTopics.toPrompt();
        assertTrue(topicsPrompt.contains("样本数量：主题 1 条，回复 0 条\n"));
        assertTrue(topicsPrompt.contains("无可见回复\n"));
        assertFalse(topicsPrompt.contains("无可见主题\n"));
        assertEvidenceNumbers(topicsPrompt, "主题", 1);
        assertEvidenceNumbers(topicsPrompt, "回复", 0);
        assertFalse(topicsPrompt.contains("Quoted claim"));
        assertFalse(topicsPrompt.contains("My reply"));
    }

    @Test
    public void profileWithoutVisibleActivityIsEmpty() {
        ProfileSummaryInput[] inputs = {
                new ProfileSummaryInput("42", "User", null, null),
                new ProfileSummaryInput("42", "User", Collections.singletonList(null), Collections.singletonList(null))
        };
        for (ProfileSummaryInput input : inputs) {
            assertTrue(input.isEmpty());
            String prompt = input.toPrompt();
            assertTrue(prompt.contains("样本数量：主题 0 条，回复 0 条\n"));
            assertTrue(prompt.contains("无可见主题\n"));
            assertTrue(prompt.contains("无可见回复\n"));
            assertEvidenceNumbers(prompt, "主题", 0);
            assertEvidenceNumbers(prompt, "回复", 0);
        }
    }

    private static void assertEvidenceNumbers(String prompt, String kind, int expectedCount) {
        Matcher labels = Pattern.compile("(?m)^\\[" + kind + "([0-9]+)\\] ").matcher(prompt);
        int count = 0;
        while (labels.find()) {
            assertEquals(++count, Integer.parseInt(labels.group(1)));
        }
        assertEquals(expectedCount, count);
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
