package sp.phone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import java.lang.reflect.Method;
import gov.anzong.androidnga.common.util.EmoticonUtils;

public class LegacyEmoticonTest {
    @Test public void canonicalAssetsRetainLegacyCategoryDimensions() throws Exception {
        assertEquals("<img src='file:///android_asset/ac/ac42.png'>", decode("[s:ac:赞同]"));
        assertEquals("<img src='file:///android_asset/ng/ng_38.png' width=60 height=60>",
                decode("[s:ng:问号大]"));
        assertEquals("<img src='file:///android_asset/pg/pg01.png' width=60 height=60>",
                decode("[s:pg:战斗力]"));
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
