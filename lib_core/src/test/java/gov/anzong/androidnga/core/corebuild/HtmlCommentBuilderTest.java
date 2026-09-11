package gov.anzong.androidnga.core.corebuild;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HtmlCommentBuilderTest {
    @Test public void onlyACompleteLeadingReplyHeaderIsRemoved() {
        String header = "[b]Reply to [pid=50120,100001,7]Reply[/pid] Post by [uid=42]author[/uid] (synthetic)[/b]";
        String body = "\ncomment $ \\ source [b]keep this[/b]";
        assertEquals(body, HtmlCommentBuilder.stripReplyHeader(header + body));
        assertEquals("lead " + header + body, HtmlCommentBuilder.stripReplyHeader("lead " + header + body));
        assertEquals("body", HtmlCommentBuilder.stripReplyHeader(header.toUpperCase() + "body"));
    }

    @Test public void plainShortMissingAndIncompleteContentRemainSafeAndUnmodified() {
        for (String source : new String[]{"", "a", "正文", "comment source", "[/b]tail", "[b]other[/b]tail",
                "[b]Reply to [pid=50120,100001,7]Reply[/pid] unclosed", "此条内容暂无法完整显示。<br/>"}) {
            assertEquals(source, HtmlCommentBuilder.stripReplyHeader(source));
        }
        assertEquals("", HtmlCommentBuilder.stripReplyHeader(null));
    }
}
