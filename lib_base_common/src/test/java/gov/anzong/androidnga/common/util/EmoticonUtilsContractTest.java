package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * 守住表情自定义顺序所依赖的数据前提。
 *
 * <p>顺序按图片文件名持久化，其正确性建立在「分类内文件名唯一」之上。若将来官方数据变更
 * 打破这一前提，本测试会先失败——此时应回到规划阶段重新选择标识，而不是就地改 key。
 */
public class EmoticonUtilsContractTest {

    /** 这是自定义顺序 key 选型的核心前提。 */
    @Test
    public void fileNamesAreUniqueWithinEachCategory() {
        for (int category = 0; category < EmoticonUtils.EMOTICON_URL.length; category++) {
            Set<String> fileNames = new HashSet<>();
            for (String[] emoticon : EmoticonUtils.EMOTICON_URL[category]) {
                assertTrue("duplicate file name '" + emoticon[1] + "' in category "
                        + categoryId(category), fileNames.add(emoticon[1]));
            }
        }
    }

    @Test
    public void resolverKeepsAssetIdentityAfterCustomOrderAndReset() {
        int count = 0;
        for (int c = 0; c < EmoticonUtils.EMOTICON_LABEL.length; c++) {
            String[] files = EmoticonUtils.getFileNames(c);
            java.util.List<String> saved = new java.util.ArrayList<>(java.util.Arrays.asList(files));
            java.util.Collections.reverse(saved);
            for (java.util.List<String> order : java.util.Arrays.asList(saved, java.util.Collections.<String>emptyList())) {
                for (int index : EmoticonOrderResolver.resolve(files, order)) {
                    String[] item = EmoticonUtils.EMOTICON_URL[c][index];
                    assertEquals(categoryId(c) + "/" + item[1],
                            EmoticonUtils.resolveAssetPath(categoryId(c), item[0]));
                }
            }
            count += files.length;
        }
        assertEquals(238, count);
        assertEquals("ac/ac42.png", EmoticonUtils.resolveAssetPath("ac", "赞同"));
        assertEquals("ac/ac43.png", EmoticonUtils.resolveAssetPath("ac", "闪光"));
        org.junit.Assert.assertNull(EmoticonUtils.resolveAssetPath("missing", "哭"));
        org.junit.Assert.assertNull(EmoticonUtils.resolveAssetPath("ac", "ac42.png"));
        org.junit.Assert.assertNull(EmoticonUtils.resolveAssetPath(null, null));
    }

    private static String categoryId(int category) {
        return EmoticonUtils.EMOTICON_LABEL[category][0];
    }
}
