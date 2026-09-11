package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProfileSessionTest {

    @Test
    public void exactHttpsForumOriginsNormalizeAndGuestHasNoCookie() {
        for (String host : new String[]{"bbs.nga.cn", "bbs.ngacn.cc", "nga.178.com", "ngabbs.com"}) {
            ProfileSession session = ProfileSession.create("HTTPS://" + host + ":443/", null, null, "Fixture UA");
            assertNotNull(session);
            assertEquals("https://" + host, session.origin);
            assertEquals("0", session.accountUid);
            assertEquals("", session.cookie);
        }
    }

    @Test
    public void otherOriginsAndHeaderInjectionNeverProduceARequestIdentity() {
        for (String origin : new String[]{"http://bbs.nga.cn", "https://bbs.nga.cn.evil.invalid",
                "https://user@bbs.nga.cn", "https://bbs.nga.cn:444", "https://192.0.2.1",
                "https://bbs.nga.cn/path", "https://bbs.nga.cn?uid=42", "https://bbs.nga.cn#fragment",
                "https://bbs.nga.cn.", "https://bbs.nga.cn\\@example.invalid", null}) {
            assertNull(origin, ProfileSession.create(origin, "7", "fixture-session", "Fixture UA"));
        }
        for (String cid : new String[]{null, "", "bad; other=secret", "bad\r\nHeader: value",
                "bad,other", "bad\\escape", "bad\"quote", "a".repeat(513)}) {
            assertNull(ProfileSession.create("https://bbs.nga.cn", "7", cid, "Fixture UA"));
        }
        for (String uid : new String[]{"0", "-1", "001", "abc", "2147483648", null}) {
            assertNull(ProfileSession.create("https://bbs.nga.cn", uid, "fixture-session", "Fixture UA"));
        }
        assertNull(ProfileSession.create("https://bbs.nga.cn", "7", "fixture-session", "bad\nUA"));
    }

    @Test
    public void sessionCopiesCredentialsAndDetectsSameUidReplacement() {
        ProfileSession first = session("first-fixture");
        ProfileSession same = session("first-fixture");
        ProfileSession replacement = session("second-fixture");
        assertEquals(first, same);
        assertNotEquals(first, replacement);
        assertEquals("ngaPassportUid=7; ngaPassportCid=first-fixture", first.cookie);
        assertFalse(first.toString().contains("first-fixture"));
        assertFalse(first.toString().contains("ngaPassport"));
    }

    private static ProfileSession session(String cid) {
        return ProfileSession.create("https://bbs.nga.cn", "7", cid, "Fixture UA");
    }
}
