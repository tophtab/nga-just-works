package sp.phone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FunctionUtilsAvatarTest {

    @Test
    public void normalizesRetiredAvatarHostFromDirectUrl() {
        assertEquals("https://img.nga.cn/avatars/mon_test/avatar.jpg",
                FunctionUtils.parseAvatarUrl(
                        "http://img.nga.178.com/avatars/mon_test/avatar.jpg"));
    }

    @Test
    public void normalizesRetiredAvatarHostFromNestedAvatarJson() {
        String avatarJson = "{\"0\":{\"0\":\"https://img2.ngacn.cc/avatars/mon_test/avatar.jpg\"}}";

        assertEquals("https://img2.nga.cn/avatars/mon_test/avatar.jpg",
                FunctionUtils.parseAvatarUrl(avatarJson));
    }

    @Test
    public void preservesCurrentOtherAndUnextractableAvatarValues() {
        assertEquals("https://img.nga.cn/avatars/current.jpg",
                FunctionUtils.parseAvatarUrl("https://img.nga.cn/avatars/current.jpg"));
        assertEquals("https://avatar.example/avatar.jpg",
                FunctionUtils.parseAvatarUrl("https://avatar.example/avatar.jpg"));
        assertEquals("not-a-url", FunctionUtils.parseAvatarUrl("not-a-url"));
        assertEquals("HTTP://img.nga.178.com/avatars/unextractable.jpg",
                FunctionUtils.parseAvatarUrl("HTTP://img.nga.178.com/avatars/unextractable.jpg"));
        assertEquals("", FunctionUtils.parseAvatarUrl(""));
        assertNull(FunctionUtils.parseAvatarUrl(null));
    }
}
