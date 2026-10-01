package gov.anzong.androidnga.core.decode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.data.HtmlData;

public class ForumBasicDecoderMediaTest {
    private static final String PREFIX = "https://page.example/attachments";
    private final ForumBasicDecoder decoder = new ForumBasicDecoder();

    private String decode(String source, String prefix) {
        HtmlData data = HtmlData.create(source, "https://forum.example/");
        data.setAttachmentsPrefix(prefix);
        String result = decoder.decode(source, data);
        assertEquals(source, data.getRawData());
        return result;
    }

    private String decode(String source) {
        return decode(source, PREFIX);
    }

    private static String video(String url) {
        return "<video src='" + url + "' controls='controls'></video>";
    }

    @Test public void relativePathsPreserveCaseUnicodeAndOptionalUriParts() {
        for (String path : new String[]{"mon_202609/a.mp4", "A.MP4", "视频.mp4", "media",
                "a.mp4?", "a.mp4#", "a%20b.mp4?x=%24", "dir/a:b;v=1.mp4"}) {
            assertEquals(path, video(PREFIX + "/" + path), decode("[FLASH]./" + path + "[/FlAsH]"));
        }
    }

    @Test public void adjacentAndNestedSpansKeepSurroundingText() {
        assertEquals("前" + video(PREFIX + "/a.mp4") + video(PREFIX + "/b.mp4") + "后",
                decode("前[flash]./a.mp4[/flash][flash]./b.mp4[/flash]后"));
        assertEquals("[flash]./bad" + video(PREFIX + "/ok.mp4") + "[/flash]",
                decode("[flash]./bad[flash]./ok.mp4[/flash][/flash]"));
        assertEquals("[flash]./broken " + video(PREFIX + "/ok.mp4") + "尾",
                decode("[flash]./broken [flash]./ok.mp4[/flash]尾"));
    }

    @Test public void attributeEncodingAndReplacementAreLiteralSafe() {
        assertEquals(video(PREFIX + "/a.mp4?token=$1&amp;x=2#t=1"),
                decode("[flash]./a.mp4?token=$1&x=2#t=1[/flash]"));
        assertEquals(video(PREFIX + "/a.mp4?x=1&amp;y=2"),
                decode("[flash]./a.mp4?x=1&amp;y=2[/flash]"));
        assertEquals(video(PREFIX + "/a&#39;b.mp4"), decode("[flash]./a'b.mp4[/flash]"));
    }

    @Test public void invalidAndUnknownTagsStayReadableAndDoNotBreakNextVideo() {
        String[] invalid = {"", "./", "./?x=1", "./#t=1", "x/a.mp4", "/a.mp4", "../a.mp4",
                "//media.example/a.mp4", ".//a.mp4", "./dir/../a.mp4", "././a.mp4",
                "./%2e/a.mp4", "./%2e%2e/a.mp4", "./%2E%2e/a.mp4", "./.%2E/a.mp4",
                "./dir/", "./dir//a.mp4", " ./a.mp4", "./a.mp4 ", "./a\nb.mp4", "./a\rb.mp4",
                "./a\tb.mp4", "./a\u0000b.mp4", "./a\u0085b.mp4", "./a\u00a0b.mp4",
                "./a\u3000b.mp4", "./a%GG.mp4", "./a%", "./a\\b.mp4", "./a\"b.mp4",
                "./a<b>.mp4", "./a[bad]b.mp4"};
        for (String path : invalid) {
            String source = "[flash]" + path + "[/flash]";
            assertEquals(path, source + video(PREFIX + "/ok.mp4"),
                    decode(source + "[flash]./ok.mp4[/flash]"));
        }
        for (String source : new String[]{"[flash=unknown]./a.mp4[/flash]", "[flash]./a.mp4",
                "[flash]./a.mp4[/flas]", "[/flash]后"}) {
            assertEquals(source, decode(source));
        }
    }

    @Test public void existingTypedAndAbsoluteOutputsRemainUnchanged() {
        assertEquals(video(PREFIX + "/a.mp4"), decode("[flash=video]./a.mp4[/flash]"));
        String audio = "<audio src='" + PREFIX + "/b.mp3&filename=nga_audio.mp3' controls='controls'></audio>";
        assertEquals(audio, decode("[flash=audio]./b.mp3[/flash]"));
        assertEquals(video(PREFIX + "/a.mp4") + "中" + audio,
                decode("[flash]./a.mp4[/flash]中[flash=audio]./b.mp3[/flash]"));
        for (String scheme : new String[]{"http", "https"}) {
            String url = scheme + "://media.example/a.mp4";
            String link = "<a href=\"" + url + "\"><img src='file:///android_asset/flash.png' style= 'max-width:100%;' ></a>";
            assertEquals(link, decode("[flash]" + url + "[/flash]"));
            assertEquals("[flash]./bad%[/flash]" + link + video(PREFIX + "/ok.mp4"),
                    decode("[flash]./bad%[/flash][flash]" + url + "[/flash][flash=video]./ok.mp4[/flash]"));
        }
    }

    @Test public void pagePrefixesStayLocalAndNonNullContextFallsBack() {
        String source = "[flash]./a.mp4[/flash]";
        for (String prefix : new String[]{PREFIX, "http://second.example:8080/attachments", PREFIX}) {
            assertEquals(video(prefix + "/a.mp4"), decode(source, prefix));
        }
        assertEquals(video(NgaImageHost.attachmentsPrefix() + "/a.mp4"), decode(source, null));
    }
}
