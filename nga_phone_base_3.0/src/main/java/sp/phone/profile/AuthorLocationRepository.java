package sp.phone.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shared per-author work, driven by actual page deliveries. Methods and dispatched completions
 * run on one owner executor (the Android main thread in production); disk and network I/O do not.
 */
public final class AuthorLocationRepository {

    /** One thread/Activity owns all of its pages, including offscreen prefetch. */
    public final class Owner implements AutoCloseable {
        private boolean foreground;
        private boolean closed;

        public void setForeground(boolean foreground) {
            if (closed || this.foreground == foreground) return;
            this.foreground = foreground;
            pruneQueue();
            if (foreground) {
                boolean onlineDemand = false;
                for (Subscription subscription : new ArrayList<>(subscriptions)) {
                    if (subscription.owner == this && subscription.online && subscription.isActive()) {
                        for (int author : subscription.authors) {
                            onlineDemand |= subscription.needs(author, true);
                        }
                        enqueueMissing(subscription);
                    }
                }
                if (onlineDemand) dispatch();
            }
        }

        @Override public void close() {
            closed = true;
            foreground = false;
            for (Subscription subscription : new ArrayList<>(subscriptions)) {
                if (subscription.owner == this) subscription.close();
            }
        }
    }

    public Owner createOwner() { return new Owner(); }

    public interface Cancellation {
        void cancel();
    }

    public interface Transport {
        Cancellation fetch(ProfileSession session, int author, Consumer<ProfileLocationResult> callback);
    }

    /** Delayed actions run on the same owner executor as repository methods. */
    public interface Scheduler {
        Cancellation schedule(Runnable action, long delayMillis);
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
        private final Owner owner;
        private final Set<Integer> finished = new LinkedHashSet<>();
        private final Epoch epoch;
        private Consumer<Snapshot> listener;

        private Subscription(Collection<Integer> authors, boolean online, Owner owner, Consumer<Snapshot> listener) {
            LinkedHashSet<Integer> eligible = new LinkedHashSet<>();
            for (Integer author : authors) {
                if (author != null && author > 0) {
                    eligible.add(author);
                }
            }
            this.authors = Collections.unmodifiableSet(eligible);
            this.online = online;
            this.owner = owner;
            this.listener = listener;
            this.epoch = currentEpoch;
        }

        private boolean isActive() {
            return listener != null && epoch == currentEpoch && epoch.valid;
        }

        private boolean needs(int author, boolean foregroundOnly) {
            return isActive() && online && authors.contains(author) && !finished.contains(author)
                    && (owner == null || (!owner.closed && (!foregroundOnly || owner.foreground)));
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
        boolean cancelled;
        int networkFailures;

        Job(AuthorLocationCache.Key key, ProfileSession session, Epoch epoch) {
            this.key = key;
            this.session = session;
            this.epoch = epoch;
        }
    }

    private final class DispatchWakeup implements Runnable {
        Cancellation cancellation;

        @Override
        public void run() {
            if (wakeup != this) {
                return;
            }
            wakeup = null;
            dispatch();
        }
    }

    private final AuthorLocationCache cache = new AuthorLocationCache();
    private final Transport transport;
    private final LongSupplier clock;
    private final LongSupplier elapsedClock;
    private final LongSupplier interval;
    private final Supplier<ProfileSession> sessionSource;
    private final Executor completionExecutor;
    private final Scheduler scheduler;
    private final Consumer<List<AuthorLocationCache.Entry>> persist;
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();
    private final LinkedHashMap<AuthorLocationCache.Key, Job> queue = new LinkedHashMap<>();
    private final Map<AuthorLocationCache.Key, Job> rounds = new LinkedHashMap<>();
    private final Set<ProfileSession> rejectedSessions = new LinkedHashSet<>();
    private ProfileSession session;
    private Epoch currentEpoch = new Epoch();
    private Job inFlight;
    private DispatchWakeup wakeup;
    private long nextRequestAt;
    private boolean loaded;

    AuthorLocationRepository(Transport transport, LongSupplier clock, LongSupplier elapsedClock,
                             Supplier<ProfileSession> sessionSource, Executor completionExecutor,
                             Scheduler scheduler, Consumer<List<AuthorLocationCache.Entry>> persist) {
        this(transport, clock, elapsedClock, sessionSource, completionExecutor, scheduler, persist,
                () -> ThreadLocalRandom.current().nextLong(200, 501));
    }

    AuthorLocationRepository(Transport transport, LongSupplier clock, LongSupplier elapsedClock,
                             Supplier<ProfileSession> sessionSource, Executor completionExecutor,
                             Scheduler scheduler, Consumer<List<AuthorLocationCache.Entry>> persist,
                             LongSupplier interval) {
        this.interval = interval;
        this.transport = transport;
        this.clock = clock;
        this.elapsedClock = elapsedClock;
        this.sessionSource = sessionSource;
        this.completionExecutor = completionExecutor;
        this.scheduler = scheduler;
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
        return subscribe(authors, online, listener, subscription -> { });
    }

    /** A page owns its handle before synchronous publication can close or reset that page. */
    Subscription subscribe(Collection<Integer> authors, boolean online, Consumer<Snapshot> listener,
                           Consumer<Subscription> onRegistered) {
        return subscribe(authors, online, null, listener, onRegistered);
    }

    Subscription subscribe(Collection<Integer> authors, boolean online, Owner owner,
                           Consumer<Snapshot> listener, Consumer<Subscription> onRegistered) {
        synchronizeSession();
        Subscription subscription = new Subscription(authors, online, owner, listener);
        subscriptions.add(subscription);
        onRegistered.accept(subscription);
        subscription.publish();
        if (loaded && online && subscription.isActive()) {
            enqueueMissing(subscription);
            if (owner == null || (owner.foreground && !owner.closed)) dispatch();
        }
        return subscription;
    }

    /** Called synchronously on an account signal, before its values are safe to capture. */
    public void invalidateSession() {
        currentEpoch.valid = false;
        queue.clear();
        rounds.clear();
        cancelWakeup();
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
            AuthorLocationCache.Entry cached = cache.get(key, now);
            // A cache-satisfied demand is complete. Resuming it after TTL/cooldown expiry
            // is not a new online delivery and must not create another lookup.
            if (cached != null) {
                subscription.finished.add(author);
            }
            if (!subscription.needs(author, true) || cached != null || queue.containsKey(key)
                    || (inFlight != null && !inFlight.cancelled
                    && inFlight.epoch == currentEpoch && inFlight.key.equals(key))) continue;
            Job job = rounds.get(key);
            if (job == null) {
                job = new Job(key, session, currentEpoch);
                rounds.put(key, job);
            }
            queue.put(key, job);
        }
    }

    private boolean needed(int author, boolean foregroundOnly) {
        for (Subscription subscription : subscriptions) {
            if (subscription.needs(author, foregroundOnly)) return true;
        }
        return false;
    }

    private void pruneQueue() {
        queue.entrySet().removeIf(entry -> !needed(entry.getKey().author, true));
        rounds.entrySet().removeIf(entry -> !needed(entry.getKey().author, false));
        if (inFlight != null && inFlight.epoch == currentEpoch
                && !needed(inFlight.key.author, true) && !inFlight.cancelled) {
            inFlight.cancelled = true;
            if (inFlight.cancellation != null) inFlight.cancellation.cancel();
        }
        if (queue.isEmpty()) cancelWakeup();
    }

    private void cancelWakeup() {
        if (wakeup != null) {
            DispatchWakeup pending = wakeup;
            wakeup = null;
            if (pending.cancellation != null) {
                pending.cancellation.cancel();
            }
        }
    }

    private void dispatch() {
        synchronizeSession();
        if (!loaded || inFlight != null || session == null || rejectedSessions.contains(session)
                || cache.isPaused(session, clock.getAsLong())) {
            cancelWakeup();
            return;
        }
        pruneQueue();
        while (!queue.isEmpty()) {
            AuthorLocationCache.Key key = queue.keySet().iterator().next();
            Job job = queue.get(key);
            if (job.epoch != currentEpoch || cache.get(key, clock.getAsLong()) != null) {
                queue.remove(key);
                continue;
            }
            long delay = nextRequestAt - elapsedClock.getAsLong();
            if (delay > 0) {
                if (wakeup == null) {
                    DispatchWakeup pending = new DispatchWakeup();
                    wakeup = pending;
                    pending.cancellation = scheduler.schedule(pending, delay);
                }
                return;
            }
            cancelWakeup();
            queue.remove(key);
            inFlight = job;
            job.cancelled = false;
            job.cancellation = null;
            try {
                job.cancellation = transport.fetch(job.session, key.author,
                        result -> completionExecutor.execute(() -> complete(job, result)));
                // A transport may reenter owner/session state before returning its handle.
                // Cancel that handle only while this physical request still owns the slot;
                // a synchronous terminal callback may already have released it.
                if (inFlight == job && (job.cancelled || job.epoch != currentEpoch)
                        && job.cancellation != null) {
                    job.cancellation.cancel();
                }
            } catch (RuntimeException ignored) {
                completionExecutor.execute(() -> complete(job, ProfileLocationResult.failure()));
            }
            return;
        }
        cancelWakeup();
    }

    private void complete(Job job, ProfileLocationResult result) {
        if (inFlight != job) {
            return;
        }
        inFlight = null;
        // Keep the full pacing interval after every physical call, including failure/cancellation.
        // This app-wide deadline survives page and account changes; wall-clock edits cannot
        // shorten it. Waiting after completion also avoids bursts after a slow connection.
        long gap = Math.max(200, Math.min(500, interval.getAsLong()));
        nextRequestAt = AuthorLocationCache.addTime(elapsedClock.getAsLong(), gap);
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
        if (job.cancelled) {
            // Cancellation neither consumes an attempt nor populates the author cache.
            // A resume may already have queued this same round while the slot was held.
            for (Subscription subscription : new ArrayList<>(subscriptions)) enqueueMissing(subscription);
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
            case NETWORK_FAILURE:
                job.networkFailures++;
                if (job.networkFailures < 2) {
                    if (needed(job.key.author, true)) queue.put(job.key, job);
                } else {
                    cache.failNetwork(job.key, now);
                }
                break;
            case RATE_LIMIT:
                break;
            case SESSION_REJECTED:
                break;
        }
        if (result.kind != ProfileLocationResult.Kind.NETWORK_FAILURE || job.networkFailures >= 2) {
            rounds.remove(job.key);
            for (Subscription subscription : subscriptions) {
                if (subscription.online && subscription.authors.contains(job.key.author)) {
                    subscription.finished.add(job.key.author);
                }
            }
        }
        if (result.kind == ProfileLocationResult.Kind.SUCCESS || result.kind == ProfileLocationResult.Kind.FAILURE
                || (result.kind == ProfileLocationResult.Kind.NETWORK_FAILURE && job.networkFailures >= 2)) {
            persist.accept(cache.snapshot());
        }
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            if (subscription.authors.contains(job.key.author)) {
                subscription.publish();
            }
        }
        // Only pending online work gets a wakeup; cache expiry and server pauses never poll.
        dispatch();
    }
}
