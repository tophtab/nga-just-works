package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void labelAndUrlTablesHaveMatchingCategoryCount() {
        assertEquals(EmoticonUtils.EMOTICON_LABEL.length, EmoticonUtils.EMOTICON_URL.length);
        assertTrue(EmoticonUtils.EMOTICON_LABEL.length > 0);
    }

    @Test
    public void everyCategoryHasIdAndDisplayName() {
        for (String[] label : EmoticonUtils.EMOTICON_LABEL) {
            assertEquals(2, label.length);
            for (String field : label) {
                assertNotNull(field);
                assertFalse(field.trim().isEmpty());
            }
        }
    }

    @Test
    public void categoryIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (String[] label : EmoticonUtils.EMOTICON_LABEL) {
            assertTrue("duplicate category id: " + label[0], ids.add(label[0]));
        }
    }

    @Test
    public void everyEmoticonHasNameAndFileName() {
        for (int category = 0; category < EmoticonUtils.EMOTICON_URL.length; category++) {
            String[][] emoticons = EmoticonUtils.EMOTICON_URL[category];
            assertTrue("empty category: " + categoryId(category), emoticons.length > 0);
            for (String[] emoticon : emoticons) {
                assertEquals("bad tuple in category " + categoryId(category), 2, emoticon.length);
                for (String field : emoticon) {
                    assertNotNull(field);
                    assertFalse(field.trim().isEmpty());
                }
            }
        }
    }

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
    public void emoticonNamesAreUniqueWithinEachCategory() {
        for (int category = 0; category < EmoticonUtils.EMOTICON_URL.length; category++) {
            Set<String> names = new HashSet<>();
            for (String[] emoticon : EmoticonUtils.EMOTICON_URL[category]) {
                assertTrue("duplicate emoticon name '" + emoticon[0] + "' in category "
                        + categoryId(category), names.add(emoticon[0]));
            }
        }
    }

    @Test
    public void getFileNamesMatchesSecondColumn() {
        for (int category = 0; category < EmoticonUtils.EMOTICON_URL.length; category++) {
            String[][] emoticons = EmoticonUtils.EMOTICON_URL[category];
            String[] expected = new String[emoticons.length];
            for (int i = 0; i < emoticons.length; i++) {
                expected[i] = emoticons[i][1];
            }
            assertArrayEquals("category " + categoryId(category),
                    expected, EmoticonUtils.getFileNames(category));
        }
    }

    @Test
    public void getFileNamesReturnsEmptyForOutOfRangeCategory() {
        assertEquals(0, EmoticonUtils.getFileNames(-1).length);
        assertEquals(0, EmoticonUtils.getFileNames(EmoticonUtils.EMOTICON_URL.length).length);
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
