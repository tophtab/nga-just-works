package sp.phone.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shared per-author work, driven by actual page deliveries. Methods and dispatched completions
 * run on one owner executor (the Android main thread in production); disk and network I/O do not.
 */
public final class AuthorLocationRepository {

    public interface Cancellation {
        void cancel();
    }

    public interface Transport {
        Cancellation fetch(ProfileSession session, int author, Consumer<ProfileLocationResult> callback);
    }

    private static final class Epoch {
        boolean valid = true;
    }

    public static final class Snapshot {
        private final Epoch epoch;
        private final Map<Integer, AuthorLocationCache.Entry> entries;

        private Snapshot(Epoch epoch, Map<Integer, AuthorLocationCache.Entry> entries) {
            this.epoch = epoch;
            this.entries = Collections.unmodifiableMap(entries);
        }

        public static Snapshot empty() {
            return new Snapshot(new Epoch(), Collections.emptyMap());
        }

        public String location(int author, long now) {
            AuthorLocationCache.Entry entry = entries.get(author);
            return epoch.valid && entry != null && entry.isFresh(now) ? entry.location : null;
        }
    }

    public final class Subscription implements AutoCloseable {
        private final Set<Integer> authors;
        private final boolean online;
        private final Epoch epoch;
        private Consumer<Snapshot> listener;

        private Subscription(Collection<Integer> authors, boolean online, Consumer<Snapshot> listener) {
            LinkedHashSet<Integer> eligible = new LinkedHashSet<>();
            for (Integer author : authors) {
                if (author != null && author > 0) {
                    eligible.add(author);
                }
            }
            this.authors = Collections.unmodifiableSet(eligible);
            this.online = online;
            this.listener = listener;
            this.epoch = currentEpoch;
        }

        private boolean isActive() {
            return listener != null && epoch == currentEpoch && epoch.valid;
        }

        private void publish() {
            if (!isActive()) {
                return;
            }
            Map<Integer, AuthorLocationCache.Entry> found = new LinkedHashMap<>();
            if (session != null) {
                long now = clock.getAsLong();
                for (int author : authors) {
                    AuthorLocationCache.Entry entry = cache.get(new AuthorLocationCache.Key(session, author), now);
                    if (entry != null && entry.kind == AuthorLocationCache.Kind.OBSERVATION) {
                        found.put(author, entry);
                    }
                }
            }
            listener.accept(new Snapshot(epoch, found));
        }

        @Override
        public void close() {
            listener = null;
            subscriptions.remove(this);
            pruneQueue();
        }
    }

    private static final class Job {
        final AuthorLocationCache.Key key;
        final ProfileSession session;
        final Epoch epoch;
        Cancellation cancellation;

        Job(AuthorLocationCache.Key key, ProfileSession session, Epoch epoch) {
            this.key = key;
            this.session = session;
            this.epoch = epoch;
        }
    }

    private final AuthorLocationCache cache = new AuthorLocationCache();
    private final Transport transport;
    private final LongSupplier clock;
    private final Supplier<ProfileSession> sessionSource;
    private final Executor completionExecutor;
    private final Consumer<List<AuthorLocationCache.Entry>> persist;
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();
    private final LinkedHashMap<AuthorLocationCache.Key, Job> queue = new LinkedHashMap<>();
    private final Set<ProfileSession> rejectedSessions = new LinkedHashSet<>();
    private ProfileSession session;
    private Epoch currentEpoch = new Epoch();
    private Job inFlight;
    private boolean loaded;

    AuthorLocationRepository(Transport transport, LongSupplier clock, Supplier<ProfileSession> sessionSource,
                             Executor completionExecutor, Consumer<List<AuthorLocationCache.Entry>> persist) {
        this.transport = transport;
        this.clock = clock;
        this.sessionSource = sessionSource;
        this.completionExecutor = completionExecutor;
        this.persist = persist;
    }

    void restore(List<AuthorLocationCache.Entry> entries) {
        if (loaded) {
            return;
        }
        cache.restore(entries, clock.getAsLong());
        loaded = true;
        synchronizeSession();
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            subscription.publish();
            enqueueMissing(subscription);
        }
        dispatch();
    }

    /** Cache-only readers register the same way but can never create a profile request. */
    public Subscription subscribe(Collection<Integer> authors, boolean online, Consumer<Snapshot> listener) {
        synchronizeSession();
        Subscription subscription = new Subscription(authors, online, listener);
        subscriptions.add(subscription);
        subscription.publish();
        if (loaded && online) {
            enqueueMissing(subscription);
            dispatch();
        }
        return subscription;
    }

    /** Called synchronously on an account signal, before its values are safe to capture. */
    public void invalidateSession() {
        currentEpoch.valid = false;
        queue.clear();
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            if (subscription.listener != null) {
                subscription.listener.accept(Snapshot.empty());
            }
            subscription.listener = null;
        }
        subscriptions.clear();
        session = null;
        currentEpoch = new Epoch();
        // Keep the physical slot until cancellation has delivered its terminal callback.
        if (inFlight != null && inFlight.cancellation != null) {
            inFlight.cancellation.cancel();
        }
    }

    public void synchronizeSession() {
        ProfileSession latest = sessionSource.get();
        if (session == null ? latest != null : !session.equals(latest)) {
            invalidateSession();
            session = latest;
        }
    }

    private void enqueueMissing(Subscription subscription) {
        if (!subscription.isActive() || !subscription.online || session == null
                || rejectedSessions.contains(session)) {
            return;
        }
        long now = clock.getAsLong();
        for (int author : subscription.authors) {
            AuthorLocationCache.Key key = new AuthorLocationCache.Key(session, author);
            if (cache.get(key, now) != null || queue.containsKey(key)
                    || (inFlight != null && inFlight.epoch == currentEpoch && inFlight.key.equals(key))) {
                continue;
            }
            queue.put(key, new Job(key, session, currentEpoch));
        }
    }

    private void pruneQueue() {
        Iterator<Map.Entry<AuthorLocationCache.Key, Job>> iterator = queue.entrySet().iterator();
        while (iterator.hasNext()) {
            int author = iterator.next().getKey().author;
            boolean needed = false;
            for (Subscription subscription : subscriptions) {
                if (subscription.isActive() && subscription.online && subscription.authors.contains(author)) {
                    needed = true;
                    break;
                }
            }
            if (!needed) {
                iterator.remove();
            }
        }
    }

    private void dispatch() {
        synchronizeSession();
        if (!loaded || inFlight != null || session == null || rejectedSessions.contains(session)
                || cache.isPaused(session, clock.getAsLong())) {
            return;
        }
        pruneQueue();
        while (!queue.isEmpty()) {
            AuthorLocationCache.Key key = queue.keySet().iterator().next();
            Job job = queue.remove(key);
            if (job.epoch != currentEpoch || cache.get(key, clock.getAsLong()) != null) {
                continue;
            }
            inFlight = job;
            try {
                job.cancellation = transport.fetch(job.session, key.author,
                        result -> completionExecutor.execute(() -> complete(job, result)));
            } catch (RuntimeException ignored) {
                completionExecutor.execute(() -> complete(job, ProfileLocationResult.failure()));
            }
            return;
        }
    }

    private void complete(Job job, ProfileLocationResult result) {
        if (inFlight != job) {
            return;
        }
        inFlight = null;
        long now = clock.getAsLong();
        // A response can already be queued here when a UI/account signal invalidates its
        // consumers. Server stops still belong to the captured scope/session, not that UI epoch.
        if (result.kind == ProfileLocationResult.Kind.RATE_LIMIT) {
            cache.pause(job.session, now, result.retryAt);
            persist.accept(cache.snapshot());
        } else if (result.kind == ProfileLocationResult.Kind.SESSION_REJECTED) {
            rejectedSessions.add(job.session);
        }
        synchronizeSession();
        if (session != null && rejectedSessions.contains(session)) {
            queue.clear();
        }
        if (job.epoch != currentEpoch || !job.epoch.valid) {
            dispatch();
            return;
        }
        switch (result.kind) {
            case SUCCESS:
                cache.observe(job.key, result.location, now);
                break;
            case FAILURE:
                cache.fail(job.key, now);
                break;
            case RATE_LIMIT:
                break;
            case SESSION_REJECTED:
                break;
        }
        if (result.kind == ProfileLocationResult.Kind.SUCCESS || result.kind == ProfileLocationResult.Kind.FAILURE) {
            persist.accept(cache.snapshot());
        }
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            if (subscription.authors.contains(job.key.author)) {
                subscription.publish();
            }
        }
        // No timer or minimum start spacing: a free slot immediately serves the next author.
        dispatch();
    }
}
