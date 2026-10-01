package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link EmoticonOrderResolver} 的纯 JVM 单元测试。
 *
 * <p>本类刻意不引用 {@code EmoticonOrderStore} / {@code PreferenceUtils}，
 * 后者的静态初始化需要 Application Context，会让 host JVM 测试失败。
 */
public class EmoticonOrderResolverTest {

    private static final String[] DEFAULTS = {"ac0.png", "ac1.png", "ac2.png", "ac3.png"};

    @Test
    public void resolve_returnsIdentity_whenSavedIsNull() {
        assertArrayEquals(new int[]{0, 1, 2, 3}, EmoticonOrderResolver.resolve(DEFAULTS, null));
    }

    @Test
    public void resolve_returnsEmpty_whenDefaultsIsNull() {
        assertArrayEquals(new int[0], EmoticonOrderResolver.resolve(null, Arrays.asList("ac0.png")));
    }

    @Test
    public void resolve_ignoresUnknownFileName() {
        List<String> saved = Arrays.asList("ac2.png", "removed.png", "ac0.png");
        // removed.png 被忽略；ac1/ac3 属于「未出现在 saved 中」，按内置顺序追加到末尾。
        assertArrayEquals(new int[]{2, 0, 1, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void resolve_dropsDuplicateEntries() {
        List<String> saved = Arrays.asList("ac2.png", "ac2.png", "ac0.png", "ac2.png");
        assertArrayEquals(new int[]{2, 0, 1, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void resolve_toleratesNullElements() {
        List<String> saved = new ArrayList<>();
        saved.add("ac1.png");
        saved.add(null);
        saved.add("ac0.png");
        assertArrayEquals(new int[]{1, 0, 2, 3}, EmoticonOrderResolver.resolve(DEFAULTS, saved));
    }

    @Test
    public void toFileNames_roundTripsWithResolve() {
        List<String> saved = Arrays.asList("ac2.png", "ac0.png", "ac3.png", "ac1.png");
        int[] order = EmoticonOrderResolver.resolve(DEFAULTS, saved);
        assertEquals(saved, EmoticonOrderResolver.toFileNames(DEFAULTS, order));
    }

    @Test
    public void toFileNames_skipsOutOfRangeIndexes() {
        int[] order = {2, 99, -1, 0};
        assertEquals(Arrays.asList("ac2.png", "ac0.png"),
                EmoticonOrderResolver.toFileNames(DEFAULTS, order));
    }

    @Test
    public void move_toFirstAndLast() {
        assertArrayEquals(new int[]{3, 0, 1, 2},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 3, 0));
        assertArrayEquals(new int[]{1, 2, 3, 0},
                EmoticonOrderResolver.move(new int[]{0, 1, 2, 3}, 0, 3));
    }

    @Test
    public void move_doesNotMutateInput() {
        int[] input = {0, 1, 2, 3};
        EmoticonOrderResolver.move(input, 0, 3);
        assertArrayEquals(new int[]{0, 1, 2, 3}, input);
    }

    @Test
    public void move_ignoresOutOfRangeArguments() {
        int[] input = {0, 1, 2, 3};
        assertArrayEquals(input, EmoticonOrderResolver.move(input, -1, 2));
        assertArrayEquals(input, EmoticonOrderResolver.move(input, 1, 99));
        assertArrayEquals(new int[0], EmoticonOrderResolver.move(null, 0, 1));
    }

}
