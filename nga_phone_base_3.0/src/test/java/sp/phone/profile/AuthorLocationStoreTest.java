package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class AuthorLocationStoreTest {

    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    private final ProfileSession session = ProfileSession.create(
            "https://bbs.nga.cn", "7", "fixture-session-never-stored", "Fixture UA");
    private final long now = 1_700_000_000_000L;

    @Test
    public void latestAndValidEmptyObservationsRoundTripAndExpireAtExactly24Hours() throws Exception {
        AuthorLocationCache cache = new AuthorLocationCache();
        AuthorLocationCache.Key known = new AuthorLocationCache.Key(session, 42);
        AuthorLocationCache.Key empty = new AuthorLocationCache.Key(session, 43);
        cache.observe(known, "广东", now);
        cache.observe(empty, null, now);
        File file = new File(directory.getRoot(), "private-cache/author-locations-v1.json");
        new AuthorLocationStore(file).write(cache.snapshot());

        AuthorLocationCache restored = new AuthorLocationCache();
        restored.restore(new AuthorLocationStore(file).read(), now + 1);
        assertEquals("广东", restored.get(known, now + AuthorLocationCache.FRESH_MILLIS - 1).location);
        assertNotNull(restored.get(empty, now + 1));
        assertNull(restored.get(empty, now + 1).location);
        assertNull(restored.get(known, now + AuthorLocationCache.FRESH_MILLIS));
        assertNull(restored.get(empty, now + AuthorLocationCache.FRESH_MILLIS));

        String stored = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse(stored.contains("fixture-session-never-stored"));
        assertFalse(stored.contains("Cookie"));
        assertFalse(stored.contains("username"));
        assertFalse(stored.contains("Fixture UA"));
    }

    @Test
    public void failureAndLongerRateLimitSurviveRecreationWithoutBecomingLocations() {
        File file = new File(directory.getRoot(), "cache.json");
        AuthorLocationCache cache = new AuthorLocationCache();
        AuthorLocationCache.Key failed = new AuthorLocationCache.Key(session, 42);
        cache.fail(failed, now);
        long retry = now + 2 * AuthorLocationCache.RATE_LIMIT_MILLIS;
        cache.pause(session, now, retry);
        new AuthorLocationStore(file).write(cache.snapshot());
        AuthorLocationCache restored = new AuthorLocationCache();
        restored.restore(new AuthorLocationStore(file).read(), now);
        assertEquals(AuthorLocationCache.Kind.FAILURE, restored.get(failed, now).kind);
        assertNull(restored.get(failed, now).location);
        assertNull(restored.get(failed, now + AuthorLocationCache.FAILURE_MILLIS));
        assertTrue(restored.isPaused(session, retry - 1));
        assertFalse(restored.isPaused(session, retry));
    }

    @Test
    public void cacheKeepsOnlyLatestObservationAndBoundsOldestEntries() {
        AuthorLocationCache cache = new AuthorLocationCache();
        for (int uid = 1; uid <= 1005; uid++) {
            cache.observe(new AuthorLocationCache.Key(session, uid), "广东", now + uid);
        }
        assertEquals(1000, cache.snapshot().size());
        assertNull(cache.get(new AuthorLocationCache.Key(session, 5), now + 1006));
        cache.observe(new AuthorLocationCache.Key(session, 6), "上海", now + 1007);
        cache.observe(new AuthorLocationCache.Key(session, 1006), "江苏", now + 1008);
        assertNull(cache.get(new AuthorLocationCache.Key(session, 7), now + 1008));
        assertEquals("上海", cache.get(new AuthorLocationCache.Key(session, 6), now + 1008).location);
        assertEquals(1000, cache.snapshot().size());
    }

    @Test
    public void accountOriginAndAuthorAreAllPartOfTheCacheKey() {
        AuthorLocationCache cache = new AuthorLocationCache();
        AuthorLocationCache.Key original = new AuthorLocationCache.Key(session, 42);
        cache.observe(original, "广东", now);
        assertNull(cache.get(new AuthorLocationCache.Key("https://ngabbs.com", "7", 42), now));
        assertNull(cache.get(new AuthorLocationCache.Key("https://bbs.nga.cn", "8", 42), now));
        assertNull(cache.get(new AuthorLocationCache.Key("https://bbs.nga.cn", "7", 43), now));
        assertEquals("广东", cache.get(original, now).location);
    }

    @Test
    public void observationEvictionCannotShortenPersistedServerPause() {
        AuthorLocationCache cache = new AuthorLocationCache();
        cache.pause(session, now, now + AuthorLocationCache.RATE_LIMIT_MILLIS);
        for (int uid = 1; uid <= 1005; uid++) {
            cache.observe(new AuthorLocationCache.Key("https://bbs.nga.cn", "8", uid), "广东", now + uid);
        }
        assertEquals(1000, cache.snapshot().size());
        File file = new File(directory.getRoot(), "cache.json");
        new AuthorLocationStore(file).write(cache.snapshot());
        AuthorLocationCache restored = new AuthorLocationCache();
        restored.restore(new AuthorLocationStore(file).read(), now + 1006);
        assertTrue(restored.isPaused(session, now + AuthorLocationCache.RATE_LIMIT_MILLIS - 1));
    }

    @Test
    public void corruptUnsupportedAndOversizedFilesBecomeMisses() throws Exception {
        File file = directory.newFile();
        AuthorLocationStore store = new AuthorLocationStore(file);
        for (String source : new String[]{"not json", "{", "null", "[]", "{}",
                "{\"version\":2,\"entries\":[]}", "x".repeat(1024 * 1024 + 1)}) {
            Files.write(file.toPath(), source.getBytes(StandardCharsets.UTF_8));
            assertTrue(store.read().isEmpty());
        }
        AuthorLocationCache cache = new AuthorLocationCache();
        cache.observe(new AuthorLocationCache.Key(session, 42), "广东", now);
        for (String field : new String[]{"origin", "location", "expires", "kind"}) {
            store.write(cache.snapshot());
            JSONObject root = JSON.parseObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONObject entry = root.getJSONArray("entries").getJSONObject(0);
            if (field.equals("origin")) {
                entry.put(field, "https://example.invalid");
            } else if (field.equals("location")) {
                entry.put(field, "192.0.2.1");
            } else if (field.equals("expires")) {
                entry.put(field, now + 2 * AuthorLocationCache.FRESH_MILLIS);
            } else {
                entry.put(field, "UNKNOWN");
            }
            Files.write(file.toPath(), root.toJSONString().getBytes(StandardCharsets.UTF_8));
            assertTrue(field, store.read().isEmpty());
        }
    }

    @Test
    public void backwardsClockOrExpiredPersistedRowsCannotExposeStaleLocation() {
        AuthorLocationCache cache = new AuthorLocationCache();
        AuthorLocationCache.Key key = new AuthorLocationCache.Key(session, 42);
        cache.observe(key, "广东", now);
        List<AuthorLocationCache.Entry> stored = cache.snapshot();
        AuthorLocationCache restored = new AuthorLocationCache();
        restored.restore(stored, now - 1);
        assertNull(restored.get(key, now - 1));
        restored.restore(stored, now + AuthorLocationCache.FRESH_MILLIS);
        assertNull(restored.get(key, now + AuthorLocationCache.FRESH_MILLIS));
    }
}
