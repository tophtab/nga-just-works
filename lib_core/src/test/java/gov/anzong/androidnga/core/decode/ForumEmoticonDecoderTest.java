package gov.anzong.androidnga.core.decode;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import gov.anzong.androidnga.common.util.EmoticonUtils;

public class ForumEmoticonDecoderTest {
    private final ForumEmoticonDecoder decoder = new ForumEmoticonDecoder();

    @Test public void all238PickerCodesRenderTheirOwnAssets() {
        int count = 0;
        for (int c = 0; c < EmoticonUtils.EMOTICON_LABEL.length; c++) {
            String category = EmoticonUtils.EMOTICON_LABEL[c][0];
            for (String[] item : EmoticonUtils.EMOTICON_URL[c]) {
                assertEquals(html(category, item[1]), decoder.decode("[s:" + category + ":" + item[0] + "]"));
                count++;
            }
        }
        assertEquals(238, count);
    }

    @Test public void agreementAndSparkleKeepCorrectAssetsInMixedRepeatedAndQuotedContent() {
        assertEquals("text" + html("ac", "ac42.png") + "<blockquote>" + html("ac", "ac43.png")
                        + html("ac", "ac42.png") + "</blockquote><div class='signature'>"
                        + html("ng", "ng_38.png") + "</div>",
                decoder.decode("text[s:ac:赞同]<blockquote>[s:ac:闪光][s:ac:赞同]</blockquote>"
                        + "<div class='signature'>[s:ng:问号大]</div>"));
    }

    @Test public void unknownAndMalformedCodesRemainLiteralAroundKnownTokens() {
        String unknown = "[s:ac:nope][s:unknown:哭][s:ac:ac42.png][s:AC:赞同][s:ac:unfinished";
        assertEquals(unknown + html("ac", "ac42.png") + "$\\", decoder.decode(unknown + "[s:ac:赞同]$\\"));
    }

    private static String html(String category, String file) {
        String attributes = category.equals("ac") || category.equals("a2") ? " class='emoticon invertFilter'" : "";
        return "<img" + attributes + " src='file:///android_asset/" + category + "/" + file + "'>";
    }
}
