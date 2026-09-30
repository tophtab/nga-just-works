package sp.phone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import java.lang.reflect.Method;
import gov.anzong.androidnga.common.util.EmoticonUtils;

public class LegacyEmoticonTest {
    @Test public void all238CodesUseCanonicalAssetsAndRetainLegacyDimensions() throws Exception {
        int count = 0;
        for (int c = 0; c < EmoticonUtils.EMOTICON_LABEL.length; c++) {
            String category = EmoticonUtils.EMOTICON_LABEL[c][0];
            for (String[] item : EmoticonUtils.EMOTICON_URL[c]) {
                String size = category.equals("ng") || category.equals("pg") ? " width=60 height=60" : "";
                assertEquals("<img src='file:///android_asset/" + category + "/" + item[1] + "'" + size + ">",
                        decode("[s:" + category + ":" + item[0] + "]"));
                count++;
            }
        }
        assertEquals(238, count);
    }

    @Test public void legacyCaseAliasesAndUnknownTextArePreserved() throws Exception {
        assertEquals("<img src='file:///android_asset/ac/ac0.png'>[s:no:blink]"
                        + "<img src='file:///android_asset/ac/ac42.png'><img src='file:///android_asset/ac/ac43.png'>",
                decode("[S:AC:BLINK][s:no:blink][s:ac:赞同][s:ac:闪光]"));
        assertNull(EmoticonUtils.getPathByURI("https://example.test/ac42.png"));
        assertNull(EmoticonUtils.getPathByURI("ac42.png"));
    }

    private static String decode(String content) throws Exception {
        Method method = StringUtils.class.getDeclaredMethod("buildEmoticonImage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, content);
    }
}
