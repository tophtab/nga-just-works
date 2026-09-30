package sp.phone.view.editor;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class InlineMediaSourceTest {
    private static final String PREFIX = "https://img.nga.cn/attachments";

    @Test public void mixedRepeatedMediaKeepExactSourceOffsetsAndRoundTrip() {
        String source = "文😀[s:ac:赞同]中[s:ac:赞同]\n[IMG] ./mon_202609/a.png.thumb.jpg [/IMG]尾";
        List<InlineMediaSource.Token> tokens = InlineMediaSource.parse(source, PREFIX);
        assertEquals(3, tokens.size());
        assertEquals("file:///android_asset/ac/ac42.png", tokens.get(0).resource);
        assertEquals(tokens.get(0).resource, tokens.get(1).resource);
        assertNotEquals(tokens.get(0).start, tokens.get(1).start);
        assertEquals(PREFIX + "/mon_202609/a.png.thumb.jpg", tokens.get(2).resource);
        StringBuilder saved = new StringBuilder();
        int cursor = 0;
        for (InlineMediaSource.Token token : tokens) {
            assertEquals(token.source, source.substring(token.start, token.end));
            saved.append(source, cursor, token.start).append(token.source);
            cursor = token.end;
        }
        saved.append(source.substring(cursor));
        assertEquals(source, saved.toString());
        assertEquals(source, new String(saved)); // Draft serialization has no bitmap or formatted HTML.
    }

    @Test public void unknownMalformedAndUnsupportedTokensRemainSource() {
        String source = "[s:ac:未知][s:bad:赞同][img]content://photo/1[/img]"
                + "[img]https://user:secret@example.com/a.png[/img][img=100]a[/img]"
                + "[img]https://example.com/a b.png[/img][img]javascript:alert(1)[/img]";
        assertTrue(InlineMediaSource.parse(source, PREFIX).isEmpty());
        assertArrayEquals(new int[]{2, 4}, InlineMediaSource.expand(2, 4, Collections.emptyList()));
    }

    @Test public void imageHostNormalizationDoesNotModifyMarker() {
        String source = "[img]http://img6.ngacn.cc/attachments/a.jpg?x=1[/img]";
        InlineMediaSource.Token token = InlineMediaSource.parse(source, "http://img9.nga.cn/attachments").get(0);
        assertEquals("http://img9.nga.cn/attachments/a.jpg?x=1", token.resource);
        assertEquals(source, token.source);
        assertEquals("https://example.com/x.png", InlineMediaSource.parse(
                "[img]https://example.com/x.png[/img]", PREFIX).get(0).resource);
    }

    @Test public void displayedMediaDeletionReplacementAndSelectionAreAtomic() {
        String source = "A[s:ac:赞同]B[img]./x.png[/img]C";
        List<InlineMediaSource.Token> tokens = InlineMediaSource.parse(source, PREFIX);
        InlineMediaSource.Token first = tokens.get(0), second = tokens.get(1);
        List<int[]> ranges = Arrays.asList(new int[]{first.start, first.end}, new int[]{second.start, second.end});
        // Backspace and forward-delete each remove the entire preview, preserving neighbors.
        for (int[] requested : Arrays.asList(new int[]{first.end - 1, first.end}, new int[]{first.start, first.start + 1})) {
            int[] actual = InlineMediaSource.expand(requested[0], requested[1], ranges);
            assertEquals("AB[img]./x.png[/img]C", source.substring(0, actual[0]) + source.substring(actual[1]));
        }
        int[] selection = InlineMediaSource.expand(first.start + 2, second.end - 2, ranges);
        assertEquals("AreplacementC", source.substring(0, selection[0]) + "replacement" + source.substring(selection[1]));
        assertArrayEquals(new int[]{second.end, first.start}, InlineMediaSource.expand(second.end - 2, first.start + 1, ranges));
        assertArrayEquals(new int[]{first.start, first.end}, InlineMediaSource.expand(first.start + 1, first.start + 1, ranges));
        assertArrayEquals(new int[]{0, 1}, InlineMediaSource.expand(0, 1, ranges));
        assertArrayEquals(new int[]{first.end, first.end}, InlineMediaSource.expand(first.end, first.end, ranges));
    }

    @Test public void callbacksCannotDecorateNewInputDeletedMediaOrRetiredView() {
        String source = "A[s:ac:赞同]B[s:ac:赞同]C";
        List<InlineMediaSource.Token> tokens = InlineMediaSource.parse(source, PREFIX);
        InlineMediaSource.Revision owner = new InlineMediaSource.Revision();
        long original = owner.current();
        assertTrue(owner.accepts(original, tokens.get(0), source));
        assertTrue(owner.accepts(original, tokens.get(1), source));
        String edited = "prefix" + source;
        owner.changed();
        assertFalse(owner.accepts(original, tokens.get(0), edited));
        assertFalse(owner.accepts(original, tokens.get(1), edited));
        InlineMediaSource.Token shifted = InlineMediaSource.parse(edited, PREFIX).get(1);
        long current = owner.current();
        assertTrue(owner.accepts(current, shifted, edited));
        assertFalse(owner.accepts(current, shifted, ""));
        assertFalse(owner.accepts(current, shifted, edited.replace("赞同", "闪光")));
        owner.close();
        assertFalse(owner.accepts(current, shifted, edited));
        InlineMediaSource.Revision restoredView = new InlineMediaSource.Revision();
        assertTrue(restoredView.accepts(restoredView.current(), shifted, edited));
    }
}
