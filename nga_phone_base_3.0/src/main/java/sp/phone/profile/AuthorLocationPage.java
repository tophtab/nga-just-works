package sp.phone.profile;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import sp.phone.http.bean.ThreadData;

/** View-owned metadata delivery, independent of Android scheduling and lifecycle classes. */
final class AuthorLocationPage implements AutoCloseable {
    static final class Delivery {
        final long generation;
        final AuthorLocationRepository.Snapshot snapshot;

        Delivery(long generation, AuthorLocationRepository.Snapshot snapshot) {
            this.generation = generation;
            this.snapshot = snapshot;
        }
    }

    private final AuthorLocationRepository repository;
    private final Executor whenSessionSettled;
    private final LongSupplier sessionSignal;
    private final Consumer<Delivery> display;
    private AuthorLocationRepository.Subscription subscription;
    private ThreadData lastDeliveredData;
    private Delivery latest;
    private long pendingSequence;
    private long generation;
    private boolean closed;

    AuthorLocationPage(AuthorLocationRepository repository, Executor whenSessionSettled,
                       LongSupplier sessionSignal, Consumer<Delivery> display) {
        this.repository = repository;
        this.whenSessionSettled = whenSessionSettled;
        this.sessionSignal = sessionSignal;
        this.display = display;
    }

    void deliver(ThreadData data, boolean online) {
        if (closed) return;
        if (data != null && data == lastDeliveredData) {
            // READY replay retains the original online/cache-only intent, even while pending.
            replay();
            return;
        }
        lastDeliveredData = data;
        long sequence = ++pendingSequence;
        if (data == null) {
            clear();
            return;
        }
        long signal = sessionSignal.getAsLong();
        Set<Integer> authors = ArticleAuthorIds.fromPage(data);
        // Keep the active consumer and generation alive: it must still publish invalidation
        // while the replacement waits for settled account values.
        WeakReference<AuthorLocationPage> owner = new WeakReference<>(this);
        whenSessionSettled.execute(() -> {
            AuthorLocationPage page = owner.get();
            if (page != null) page.replace(sequence, signal, authors, online);
        });
    }

    private void replace(long sequence, long signal, Set<Integer> authors, boolean online) {
        if (closed || pendingSequence != sequence) return;
        if (sessionSignal.getAsLong() != signal) {
            clear();
            return;
        }
        long version = ++generation;
        AuthorLocationRepository.Subscription previous = subscription;
        subscription = null;
        AuthorLocationRepository.Subscription replacement = repository.subscribe(authors, online,
                new DeliverySink(this, version, previous), registered -> {
                    if (!closed && generation == version) subscription = registered;
                    else registered.close();
                });
        // subscribe publishes synchronously. Its callback may close/reset this page or even
        // install another subscription; never retain that obsolete returned handle.
        if (closed || generation != version) {
            replacement.close();
        }
    }

    void replay() {
        if (isCurrent(latest)) display.accept(latest);
    }

    boolean isCurrent(Delivery delivery) {
        return !closed && delivery != null && delivery.generation == generation;
    }

    private void accept(long version, AuthorLocationRepository.Snapshot snapshot) {
        if (!closed && generation == version) {
            latest = new Delivery(version, snapshot);
            display.accept(latest);
        }
    }

    private void clear() {
        generation++;
        closeSubscription();
        accept(generation, AuthorLocationRepository.Snapshot.empty());
    }

    private void closeSubscription() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        lastDeliveredData = null;
        latest = null;
        pendingSequence++;
        generation++;
        closeSubscription();
    }

    /** The app repository must not retain a page or its lifecycle/adapter output callback. */
    private static final class DeliverySink implements Consumer<AuthorLocationRepository.Snapshot> {
        private final WeakReference<AuthorLocationPage> owner;
        private final long generation;
        private AuthorLocationRepository.Subscription previous;

        DeliverySink(AuthorLocationPage owner, long generation,
                     AuthorLocationRepository.Subscription previous) {
            this.owner = new WeakReference<>(owner);
            this.generation = generation;
            this.previous = previous;
        }

        @Override
        public void accept(AuthorLocationRepository.Snapshot snapshot) {
            // The first publication is synchronous, after the new consumer is registered and
            // before subscribe dispatches. Retire the old consumer here so removed queued
            // authors cannot start during the handoff, and invalidation has no observer gap.
            if (previous != null) {
                previous.close();
                previous = null;
            }
            AuthorLocationPage page = owner.get();
            if (page != null) page.accept(generation, snapshot);
        }
    }
}
