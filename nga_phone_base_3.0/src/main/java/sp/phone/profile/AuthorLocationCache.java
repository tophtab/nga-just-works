package sp.phone.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Disposable observations and failure metadata; all access is confined to the repository owner. */
public final class AuthorLocationCache {

    public static final long FRESH_MILLIS = 24 * 60 * 60 * 1000L;
    public static final long FAILURE_MILLIS = 10 * 60 * 1000L;
    public static final long RATE_LIMIT_MILLIS = 30 * 60 * 1000L;
    public static final int MAX_ENTRIES = 1000;

    enum Kind { OBSERVATION, FAILURE, RATE_LIMIT }

    static final class Key {
        final String origin;
        final String account;
        final int author;

        Key(String origin, String account, int author) {
            this.origin = origin;
            this.account = account;
            this.author = author;
        }

        Key(ProfileSession session, int author) {
            this(session.origin, session.accountUid, author);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return author == that.author && origin.equals(that.origin) && account.equals(that.account);
        }

        @Override
        public int hashCode() {
            return Objects.hash(origin, account, author);
        }
    }

    static final class Entry {
        final Key key;
        final Kind kind;
        final String location;
        final long observedAt;
        final long expiresAt;

        Entry(Key key, Kind kind, String location, long observedAt, long expiresAt) {
            this.key = key;
            this.kind = kind;
            this.location = location;
            this.observedAt = observedAt;
            this.expiresAt = expiresAt;
        }

        boolean isFresh(long now) {
            return observedAt <= now && now < expiresAt;
        }
    }

    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>();

    void restore(List<Entry> stored, long now) {
        entries.clear();
        List<Entry> sorted = new ArrayList<>(stored);
        sorted.sort(Comparator.comparingLong(entry -> entry.observedAt));
        for (Entry entry : sorted) {
            if (entry.isFresh(now)) {
                put(entry);
            }
        }
    }

    Entry get(Key key, long now) {
        Entry entry = entries.get(key);
        if (entry != null && !entry.isFresh(now)) {
            entries.remove(key);
            return null;
        }
        return entry;
    }

    boolean isPaused(ProfileSession session, long now) {
        return get(new Key(session, 0), now) != null;
    }

    void observe(Key key, String location, long now) {
        put(new Entry(key, Kind.OBSERVATION, location, now, addTime(now, FRESH_MILLIS)));
    }

    void fail(Key key, long now) {
        put(new Entry(key, Kind.FAILURE, null, now, addTime(now, FAILURE_MILLIS)));
    }

    void pause(ProfileSession session, long now, long retryAt) {
        put(new Entry(new Key(session, 0), Kind.RATE_LIMIT, null, now,
                Math.max(addTime(now, RATE_LIMIT_MILLIS), retryAt)));
    }

    List<Entry> snapshot() {
        return new ArrayList<>(entries.values());
    }

    private void put(Entry entry) {
        entries.remove(entry.key);
        entries.put(entry.key, entry);
        while (entries.size() > MAX_ENTRIES) {
            Key oldest = entries.keySet().iterator().next();
            for (Entry candidate : entries.values()) {
                if (candidate.kind != Kind.RATE_LIMIT) {
                    oldest = candidate.key;
                    break;
                }
            }
            // Observation churn in another account must not shorten an active server pause.
            entries.remove(oldest);
        }
    }

    static long addTime(long start, long duration) {
        return duration > Long.MAX_VALUE - start ? Long.MAX_VALUE : start + duration;
    }
}
