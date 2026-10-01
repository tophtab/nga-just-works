package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

public class AuthorLocationRepositoryTest {

    @Test
    public void reentrantPauseBeforeFetchReturnsCancelsHandleAndKeepsPhysicalSlot() {
        for (boolean invalidateSession : new boolean[]{false, true}) {
            AuthorLocationRepository[] repository = new AuthorLocationRepository[1];
            AuthorLocationRepository.Owner[] owner = new AuthorLocationRepository.Owner[1];
            List<Consumer<ProfileLocationResult>> callbacks = new ArrayList<>();
            int[] cancellations = {0};
            repository[0] = new AuthorLocationRepository((session, author, callback) -> {
                callbacks.add(callback);
                if (invalidateSession) repository[0].invalidateSession();
                else {
                    owner[0].setForeground(false);
                    owner[0].setForeground(true);
                }
                return () -> cancellations[0]++;
            }, () -> 1000L, () -> 0L,
                    () -> ProfileSession.create("https://bbs.nga.cn", "7", "fixture", "UA"),
                    Runnable::run, (action, delay) -> () -> { }, entries -> { }, () -> 200L);
            repository[0].restore(Collections.emptyList());
            owner[0] = repository[0].createOwner();
            owner[0].setForeground(true);
            repository[0].subscribe(Arrays.asList(41, 42), true, owner[0],
                    snapshot -> { }, registered -> { });
            assertEquals(1, cancellations[0]);
            owner[0].setForeground(true);
            repository[0].subscribe(Collections.singletonList(43), true, snapshot -> { });
            assertEquals(1, callbacks.size());
            callbacks.get(0).accept(ProfileLocationResult.networkFailure());
            assertEquals(1, callbacks.size()); // terminal callback starts the pacing interval
        }
    }

    @Test
    public void closingConsumersCancelsDelayedWorkWithoutResettingTheGlobalInterval() {
        Harness h = new Harness();
        Page original = h.deliver(page(41, 42), true);
        h.complete(ProfileLocationResult.success("广东"));
        ScheduledAction stale = h.timers.get(0);
        original.subscription.close();
        assertEquals(0, h.scheduledCount());
        h.advanceElapsedBy(400);
        h.deliver(page(43), true);
        assertEquals(1, h.scheduledCount());
        // Even a cancelled callback already queued for delivery cannot consume the new wakeup.
        stale.action.run();
        assertEquals(1, h.scheduledCount());
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.advanceElapsedBy(99);
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.advanceElapsedBy(1);
        assertEquals(Arrays.asList(41, 43), h.transport.authors());
        assertEquals(Arrays.asList(0L, 500L), h.transport.startTimes());
    }

    @Test
    public void cancelledCallsKeepTheirSlotAndDelayNewWorkAfterTheTerminalCallback() {
        Harness h = new Harness();
        h.deliver(page(41), true);
        FakeTransport.Pending oldCall = h.transport.active;
        h.repository.invalidateSession();
        assertTrue(oldCall.cancelled);
        h.session = ProfileSession.create("https://bbs.nga.cn", "8", "other-fixture", "Fixture UA");
        h.deliver(page(42), true);
        h.advanceElapsedBy(2000);
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.complete(ProfileLocationResult.failure());
        assertTrue(h.persisted.isEmpty());
        h.advanceElapsedBy(499);
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.advanceElapsedBy(1);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        assertEquals(Arrays.asList(0L, 2500L), h.transport.startTimes());
        assertEquals(1, h.transport.maximumActive);
    }

    @Test
    public void wallClockChangesAndLateWakeupsCannotBurstRequests() {
        Harness h = new Harness();
        h.deliver(page(41, 42, 43), true);
        h.complete(ProfileLocationResult.success("广东"));
        h.now += AuthorLocationCache.FRESH_MILLIS;
        h.advanceElapsedBy(499);
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.now -= 2 * AuthorLocationCache.FRESH_MILLIS;
        h.advanceElapsedBy(1);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        h.complete(ProfileLocationResult.success("上海"));
        h.advanceElapsedBy(10_000);
        assertEquals(Arrays.asList(41, 42, 43), h.transport.authors());
        h.deliver(page(44), true);
        h.complete(ProfileLocationResult.success("江苏"));
        h.advanceElapsedBy(499);
        assertEquals(Arrays.asList(41, 42, 43), h.transport.authors());
        h.advanceElapsedBy(1);
        assertEquals(Arrays.asList(0L, 500L, 10_500L, 11_000L), h.transport.startTimes());
    }

    @Test
    public void authorSnapshotSkipsAnonymousInvalidAndNullRowsAndCannotBeMutated() {
        ThreadData delivered = page(41, 42, 0, -1, 41);
        delivered.getRowList().get(1).setISANONYMOUS(true);
        delivered.getRowList().add(null);
        List<Integer> ids = new ArrayList<>(ArticleAuthorIds.fromPage(delivered));
        assertEquals(Collections.singletonList(41), ids);
        java.util.Set<Integer> snapshot = ArticleAuthorIds.fromPage(delivered);
        delivered.getRowList().get(0).setAuthorid(99);
        assertEquals(Collections.singleton(41), snapshot);
        boolean immutable = false;
        try {
            snapshot.add(100);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        assertTrue(immutable);
    }

    @Test
    public void savedPageReadersUseOnlyFreshCacheAndNeverQueueMisses() {
        Harness h = new Harness();
        h.deliver(page(41), true);
        h.completeAndWaitInterval(ProfileLocationResult.success("广东"));
        Harness offline = new Harness(h.persisted);
        Page savedPage = offline.deliver(page(41, 42, 43), false);
        assertEquals("广东", savedPage.latest().location(41, offline.now));
        assertNull(savedPage.latest().location(42, offline.now));
        assertTrue(offline.transport.authors().isEmpty());
        offline.now += AuthorLocationCache.FRESH_MILLIS;
        offline.deliver(page(41, 42), false);
        assertTrue(offline.transport.authors().isEmpty());
    }

    @Test
    public void cacheOnlyDeliveryCannotResumeAnOnlineQueueAfterItsRateLimitExpires() {
        Harness h = new Harness();
        h.deliver(page(41, 42, 43), true);
        h.completeAndWaitInterval(ProfileLocationResult.success("广东"));
        h.completeAndWaitInterval(ProfileLocationResult.rateLimit(h.now + AuthorLocationCache.RATE_LIMIT_MILLIS));
        h.now += AuthorLocationCache.RATE_LIMIT_MILLIS;

        // Saved-page reads and retained-view recreation use this same cache-only subscription.
        Page cached = h.deliver(page(41, 99), false);
        assertEquals("广东", cached.latest().location(41, h.now));
        assertNull(cached.latest().location(99, h.now));
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        h.deliver(page(43), false);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());

        // A later actual online delivery can resume eligible work immediately, without a timer.
        h.deliver(page(44), true);
        assertEquals(Arrays.asList(41, 42, 43), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        h.completeAndWaitInterval(ProfileLocationResult.success("江苏"));
        assertEquals(Arrays.asList(41, 42, 43, 44), h.transport.authors());
    }

    @Test
    public void coldCacheLoadingDoesNotLoseAnAlreadyDeliveredPage() {
        Harness h = new Harness(false);
        Page page = h.deliver(page(41, 42), true);
        assertNull(page.latest().location(41, h.now));
        assertTrue(h.transport.authors().isEmpty());
        h.repository.restore(Collections.emptyList());
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.success("广东"));
        assertEquals("广东", page.latest().location(41, h.now));
    }

    @Test
    public void ordinaryFailuresCoolDownOnlyTheirKeyWithoutAutomaticRetry() {
        Harness h = new Harness();
        h.deliver(page(41, 42), true);
        h.completeAndWaitInterval(ProfileLocationResult.failure());
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        h.now += AuthorLocationCache.FAILURE_MILLIS - 1;
        h.deliver(page(41), true);
        assertEquals(2, h.transport.authors().size());
        h.now++;
        assertEquals(2, h.transport.authors().size());
        h.deliver(page(41), true);
        assertEquals(Arrays.asList(41, 42, 41), h.transport.authors());
    }

    @Test
    public void rateLimitPausesTheWholeScopeAndRespectsLongerServerDelay() {
        Harness h = new Harness();
        h.deliver(page(41, 42, 43), true);
        long retry = h.now + 2 * AuthorLocationCache.RATE_LIMIT_MILLIS;
        h.completeAndWaitInterval(ProfileLocationResult.rateLimit(retry));
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.now = retry - 1;
        h.deliver(page(44), true);
        assertEquals(Collections.singletonList(41), h.transport.authors());
        h.now++;
        assertEquals(Collections.singletonList(41), h.transport.authors());
        // No timer retries work. New page delivery after the pause can use the free slot.
        h.deliver(page(45), true);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        assertEquals(1, h.transport.maximumActive);
    }

    @Test
    public void rateLimitHasThirtyMinuteMinimumAndIsSharedAfterProcessRecreation() {
        Harness h = new Harness();
        h.deliver(page(41), true);
        h.completeAndWaitInterval(ProfileLocationResult.rateLimit(h.now + 1000));
        Harness recreated = new Harness(h.persisted);
        recreated.now += AuthorLocationCache.RATE_LIMIT_MILLIS - 1;
        recreated.deliver(page(42), true);
        assertTrue(recreated.transport.authors().isEmpty());
        recreated.now++;
        recreated.deliver(page(43), true);
        assertEquals(Collections.singletonList(42), recreated.transport.authors());
    }

    @Test
    public void siteRejectionStopsThatSessionWithoutRotatingAccountsOrRestartingOnRevisit() {
        Harness h = new Harness();
        h.deliver(page(41, 42), true);
        h.completeAndWaitInterval(ProfileLocationResult.rejected());
        h.now += 2 * AuthorLocationCache.FRESH_MILLIS;
        h.deliver(page(43), true);
        assertEquals(Collections.singletonList(41), h.transport.authors());

        ProfileSession rejected = h.session;
        h.session = ProfileSession.create("https://bbs.nga.cn", "8", "other-fixture", "Fixture UA");
        h.deliver(page(42), true);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        h.session = rejected;
        h.deliver(page(44), true);
        assertEquals(2, h.transport.authors().size());
        h.session = ProfileSession.create("https://bbs.nga.cn", "7", "new-login-fixture", "Fixture UA");
        h.deliver(page(44), true);
        assertEquals(Arrays.asList(41, 42, 44), h.transport.authors());
    }

    @Test
    public void queuedStopResponseSurvivesSameSessionConsumerInvalidation() {
        for (ProfileLocationResult.Kind kind : new ProfileLocationResult.Kind[]{
                ProfileLocationResult.Kind.RATE_LIMIT, ProfileLocationResult.Kind.SESSION_REJECTED}) {
            Harness h = new Harness();
            Page original = h.deliver(page(41, 42), true);
            h.networkCompleted(kind == ProfileLocationResult.Kind.RATE_LIMIT
                    ? ProfileLocationResult.rateLimit(h.now + AuthorLocationCache.RATE_LIMIT_MILLIS)
                    : ProfileLocationResult.rejected());
            h.repository.invalidateSession();
            int oldEvents = original.results.size();
            h.deliver(page(43), true);
            h.drainCompletions();
            assertEquals(kind.name(), Collections.singletonList(41), h.transport.authors());
            assertEquals(oldEvents, original.results.size());
        }
    }

    @Test
    public void queuedStopIsOwnedByCapturedAccountAndStillAppliesWhenReturningToIt() {
        for (ProfileLocationResult.Kind kind : new ProfileLocationResult.Kind[]{
                ProfileLocationResult.Kind.RATE_LIMIT, ProfileLocationResult.Kind.SESSION_REJECTED}) {
            Harness h = new Harness();
            ProfileSession original = h.session;
            h.deliver(page(41), true);
            h.networkCompleted(kind == ProfileLocationResult.Kind.RATE_LIMIT
                    ? ProfileLocationResult.rateLimit(h.now + AuthorLocationCache.RATE_LIMIT_MILLIS)
                    : ProfileLocationResult.rejected());
            h.session = ProfileSession.create("https://bbs.nga.cn", "8", "different-fixture", "Fixture UA");
            Page otherAccount = h.deliver(page(42), true);
            h.drainCompletions();
            h.advanceElapsedBy(500);
            assertEquals(Arrays.asList(41, 42), h.transport.authors());
            h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
            assertEquals("上海", otherAccount.latest().location(42, h.now));
            h.session = original;
            h.deliver(page(43), true);
            assertEquals(kind.name(), Arrays.asList(41, 42), h.transport.authors());
        }
    }

    @Test
    public void queuedRateLimitPauseIsPersistedAfterConsumerInvalidation() {
        Harness h = new Harness();
        h.deliver(page(41), true);
        long retry = h.now + 2 * AuthorLocationCache.RATE_LIMIT_MILLIS;
        h.networkCompleted(ProfileLocationResult.rateLimit(retry));
        h.repository.invalidateSession();
        h.drainCompletions();

        Harness recreated = new Harness(h.persisted);
        recreated.now = retry - 1;
        recreated.deliver(page(42), true);
        assertTrue(recreated.transport.authors().isEmpty());
        recreated.now++;
        assertTrue(recreated.transport.authors().isEmpty());
        recreated.deliver(page(43), true);
        assertEquals(Collections.singletonList(42), recreated.transport.authors());
    }

    @Test
    public void queuedRejectionIsBoundToTheOriginalCredentialsForTheSameUid() {
        Harness h = new Harness();
        ProfileSession original = h.session;
        h.deliver(page(41), true);
        h.networkCompleted(ProfileLocationResult.rejected());
        h.repository.invalidateSession();
        h.session = ProfileSession.create("https://bbs.nga.cn", "7", "replacement-fixture", "Fixture UA");
        h.deliver(page(42), true);
        h.drainCompletions();
        h.advanceElapsedBy(500);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        assertEquals(h.session, h.transport.active.session);
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));

        h.session = original;
        h.deliver(page(43), true);
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
    }

    @Test
    public void sharedConsumersDetachIndependentlyAndUnsentOrphanAuthorsAreRemoved() {
        Harness h = new Harness();
        Page destroyed = h.deliver(page(41, 42), true);
        Page alive = h.deliver(page(41, 43), true);
        destroyed.subscription.close();
        int oldEvents = destroyed.results.size();
        h.completeAndWaitInterval(ProfileLocationResult.success("广东"));
        assertEquals(Arrays.asList(41, 43), h.transport.authors());
        assertEquals(oldEvents, destroyed.results.size());
        assertEquals("广东", alive.latest().location(41, h.now));
        alive.subscription.close();
        int remainingEvents = alive.results.size();
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        assertEquals(remainingEvents, alive.results.size());
        assertEquals(2, h.transport.authors().size());
        // An orphaned physical call is cancelled and cannot populate cache, even if its
        // success was already queued. A genuinely new online delivery starts a new call.
        Page revisited = h.deliver(page(43), true);
        assertNull(revisited.latest().location(43, h.now));
        assertEquals(3, h.transport.authors().size());
    }

    @Test
    public void accountSignalInvalidatesImmediatelyAndSameUidCredentialReplacementCannotLeak() {
        Harness h = new Harness();
        Page old = h.deliver(page(41, 42), true);
        FakeTransport.Pending oldCall = h.transport.active;
        h.repository.invalidateSession();
        assertTrue(oldCall.cancelled);
        assertNull(old.latest().location(41, h.now));
        int oldEvents = old.results.size();
        h.session = ProfileSession.create("https://bbs.nga.cn", "7", "replacement-fixture", "Fixture UA");
        Page newPage = h.deliver(page(41), true);
        // Cancelling does not release the physical slot before the terminal callback.
        assertEquals(1, h.transport.authors().size());
        h.completeAndWaitInterval(ProfileLocationResult.success("旧结果"));
        assertEquals(oldEvents, old.results.size());
        assertEquals(2, h.transport.authors().size());
        assertEquals("ngaPassportUid=7; ngaPassportCid=replacement-fixture", h.transport.active.session.cookie);
        assertNull(newPage.latest().location(41, h.now));
        assertTrue(h.persisted.isEmpty());
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        assertEquals("上海", newPage.latest().location(41, h.now));
        assertEquals(1, h.transport.maximumActive);
    }

    @Test
    public void everyCompletionRechecksSessionEvenWhenLifecycleObserversWereStopped() {
        Harness h = new Harness();
        Page old = h.deliver(page(41, 42), true);
        h.session = ProfileSession.create("https://ngabbs.com", "8", "another-fixture", "Fixture UA");
        h.completeAndWaitInterval(ProfileLocationResult.success("旧结果"));
        assertNull(old.latest().location(41, h.now));
        assertEquals(Collections.singletonList(41), h.transport.authors());
        assertTrue(h.persisted.isEmpty());
        h.deliver(page(41), true);
        assertEquals(2, h.transport.authors().size());
        assertEquals("https://ngabbs.com", h.transport.active.session.origin);
    }

    @Test
    public void threadPauseCancelsPhysicalCallAndResumePreservesRetryRound() {
        Harness h = new Harness();
        AuthorLocationRepository.Owner owner = h.repository.createOwner();
        owner.setForeground(true);
        h.deliver(owner, true, 41, 42);
        h.completeAndWaitInterval(ProfileLocationResult.networkFailure());
        assertEquals(Arrays.asList(41, 42), h.transport.authors());
        owner.setForeground(false);
        assertTrue(h.transport.active.cancelled);
        owner.setForeground(true);
        h.advanceElapsedBy(1000);
        assertEquals(2, h.transport.authors().size());
        h.completeAndWaitInterval(ProfileLocationResult.networkFailure()); // cancelled 42
        assertTrue(h.persisted.isEmpty());
        // Demand order may change on resume, but 41 still has exactly one retry left.
        assertEquals(41, h.transport.active.author);
        h.completeAndWaitInterval(ProfileLocationResult.networkFailure());
        assertEquals(AuthorLocationCache.Kind.NETWORK_FAILURE, h.persisted.get(0).kind);
        assertEquals(30_000, h.persisted.get(0).expiresAt - h.persisted.get(0).observedAt);
        h.completeAndWaitInterval(ProfileLocationResult.success("上海"));
        h.now += 30_000;
        owner.setForeground(false);
        owner.setForeground(true);
        assertEquals(Arrays.asList(41, 42, 41, 42), h.transport.authors());
        h.deliver(owner, true, 41);
        assertEquals(Arrays.asList(41, 42, 41, 42, 41), h.transport.authors());
    }

    @Test
    public void sharedForegroundOwnerKeepsCallAndOfflineOwnerNeverResumesWork() {
        Harness h = new Harness();
        AuthorLocationRepository.Owner first = h.repository.createOwner();
        AuthorLocationRepository.Owner second = h.repository.createOwner();
        first.setForeground(true);
        second.setForeground(true);
        h.deliver(first, true, 41, 42);
        h.deliver(second, true, 41, 43);
        first.setForeground(false);
        assertFalse(h.transport.active.cancelled);
        h.completeAndWaitInterval(ProfileLocationResult.success("广东"));
        assertEquals(Arrays.asList(41, 43), h.transport.authors());
        second.close();
        assertTrue(h.transport.active.cancelled);
        h.completeAndWaitInterval(ProfileLocationResult.failure());
        h.deliver(first, false, 99);
        first.setForeground(true);
        assertEquals(Arrays.asList(41, 43, 42), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.success(null));
        assertNull(h.transport.active);
        first.close();
    }

    @Test
    public void networkRetryGoesToTailOnceAndCooldownChurnCannotCreateThirdAttempt() {
        Harness h = new Harness();
        AuthorLocationRepository.Owner owner = h.repository.createOwner();
        owner.setForeground(true);
        h.deliver(owner, true, 41, 42, 43);
        h.complete(ProfileLocationResult.networkFailure());
        h.deliver(owner, true, 41);
        h.advanceElapsedBy(500);
        h.completeAndWaitInterval(ProfileLocationResult.success(null));
        h.completeAndWaitInterval(ProfileLocationResult.success(null));
        assertEquals(Arrays.asList(41, 42, 43, 41), h.transport.authors());
        h.completeAndWaitInterval(ProfileLocationResult.networkFailure());
        h.deliver(owner, true, 41); // born inside cooldown, cannot become a new round on resume
        assertEquals(0, h.scheduledCount());
        h.now += 30_000;
        h.advanceElapsedBy(30_000);
        owner.setForeground(false);
        owner.setForeground(true);
        assertEquals(4, h.transport.authors().size());
        h.deliver(owner, true, 41);
        assertEquals(5, h.transport.authors().size());
    }

    @Test
    public void bothRandomIntervalEndpointsApplyOnlyAfterTerminalCallback() {
        for (long gap : new long[]{200, 500}) {
            Harness h = new Harness();
            h.interval = gap;
            h.deliver(page(41, 42), true);
            assertEquals(Collections.singletonList(0L), h.transport.startTimes());
            h.advanceElapsedBy(900);
            h.complete(ProfileLocationResult.success(null));
            h.advanceElapsedBy(gap - 1);
            assertEquals(1, h.transport.authors().size());
            h.advanceElapsedBy(1);
            assertEquals(Arrays.asList(0L, 900 + gap), h.transport.startTimes());
        }
    }

    @Test
    public void pausedOwnersStillRecordLateServerStopsBeforeDiscardingCancelledResults() {
        for (ProfileLocationResult stop : Arrays.asList(ProfileLocationResult.rejected(),
                ProfileLocationResult.rateLimit(0))) {
            Harness h = new Harness();
            AuthorLocationRepository.Owner owner = h.repository.createOwner();
            owner.setForeground(true);
            h.deliver(owner, true, 41, 42);
            h.networkCompleted(stop);
            owner.setForeground(false);
            owner.setForeground(true);
            h.drainCompletions();
            h.advanceElapsedBy(500);
            h.deliver(owner, true, 43);
            assertEquals(Collections.singletonList(41), h.transport.authors());
            assertEquals(0, h.scheduledCount());
            if (stop.kind == ProfileLocationResult.Kind.RATE_LIMIT) {
                assertEquals(AuthorLocationCache.Kind.RATE_LIMIT, h.persisted.get(0).kind);
            }
        }
    }

    @Test
    public void resumeAfterFirstFailureStillOnlyHasOneRetryEvenWithDuplicateConsumer() {
        Harness h = new Harness();
        AuthorLocationRepository.Owner owner = h.repository.createOwner();
        owner.setForeground(true);
        h.deliver(owner, true, 41);
        h.complete(ProfileLocationResult.networkFailure());
        owner.setForeground(false); // retry waiting for deadline
        h.deliver(owner, true, 41);
        h.advanceElapsedBy(1000);
        assertNull(h.transport.active);
        owner.setForeground(true);
        h.completeAndWaitInterval(ProfileLocationResult.networkFailure());
        assertEquals(Arrays.asList(41, 41), h.transport.authors());
        assertEquals(AuthorLocationCache.Kind.NETWORK_FAILURE, h.persisted.get(0).kind);
    }

    private static ThreadData page(int... authors) {
        ThreadData page = new ThreadData();
        List<ThreadRowInfo> rows = new ArrayList<>();
        for (int uid : authors) {
            ThreadRowInfo row = new ThreadRowInfo();
            row.setAuthorid(uid);
            rows.add(row);
        }
        page.setRowList(rows);
        page.setRowNum(rows.size());
        return page;
    }

    private static final class Page {
        AuthorLocationRepository.Subscription subscription;
        final List<AuthorLocationRepository.Snapshot> results = new ArrayList<>();

        AuthorLocationRepository.Snapshot latest() {
            return results.get(results.size() - 1);
        }
    }

    private static final class Harness {
        long now = 1_700_000_000_000L;
        long elapsed;
        long interval = 500;
        ProfileSession session = ProfileSession.create("https://bbs.nga.cn", "7", "fixture-session", "Fixture UA");
        List<AuthorLocationCache.Entry> persisted = Collections.emptyList();
        final FakeTransport transport = new FakeTransport(() -> elapsed);
        final Queue<Runnable> completions = new ArrayDeque<>();
        final List<ScheduledAction> timers = new ArrayList<>();
        final AuthorLocationRepository repository = new AuthorLocationRepository(transport, () -> now,
                () -> elapsed, () -> session, completions::add, this::schedule, entries -> persisted = entries, () -> interval);

        Harness() {
            this(Collections.emptyList());
        }

        Harness(List<AuthorLocationCache.Entry> entries) {
            repository.restore(entries);
        }

        Harness(boolean loadCache) {
            if (loadCache) {
                repository.restore(Collections.emptyList());
            }
        }

        Page deliver(ThreadData data, boolean online) {
            Page page = new Page();
            page.subscription = repository.subscribe(ArticleAuthorIds.fromPage(data), online, page.results::add);
            return page;
        }

        void deliver(AuthorLocationRepository.Owner owner, boolean online, Integer... authors) {
            repository.subscribe(Arrays.asList(authors), online, owner, snapshot -> { }, registered -> { });
        }

        void complete(ProfileLocationResult result) {
            networkCompleted(result);
            drainCompletions();
        }

        void completeAndWaitInterval(ProfileLocationResult result) {
            complete(result);
            advanceElapsedBy(500);
        }

        AuthorLocationRepository.Cancellation schedule(Runnable action, long delayMillis) {
            ScheduledAction pending = new ScheduledAction(action, elapsed + delayMillis);
            timers.add(pending);
            return () -> pending.cancelled = true;
        }

        int scheduledCount() {
            int count = 0;
            for (ScheduledAction timer : timers) {
                if (!timer.cancelled && !timer.ran) {
                    count++;
                }
            }
            return count;
        }

        void advanceElapsedBy(long millis) {
            elapsed += millis;
            while (true) {
                ScheduledAction ready = null;
                for (ScheduledAction timer : timers) {
                    if (!timer.cancelled && !timer.ran && timer.at <= elapsed
                            && (ready == null || timer.at < ready.at)) {
                        ready = timer;
                    }
                }
                if (ready == null) {
                    return;
                }
                ready.ran = true;
                ready.action.run();
            }
        }

        void networkCompleted(ProfileLocationResult result) {
            FakeTransport.Pending pending = transport.active;
            transport.active = null;
            pending.callback.accept(result);
        }

        void drainCompletions() {
            while (!completions.isEmpty()) {
                completions.remove().run();
            }
        }
    }

    private static final class ScheduledAction {
        final Runnable action;
        final long at;
        boolean cancelled;
        boolean ran;

        ScheduledAction(Runnable action, long at) {
            this.action = action;
            this.at = at;
        }
    }

    private static final class FakeTransport implements AuthorLocationRepository.Transport {
        static final class Pending {
            ProfileSession session;
            int author;
            long startedAt;
            Consumer<ProfileLocationResult> callback;
            boolean cancelled;
        }

        Pending active;
        final List<Pending> requests = new ArrayList<>();
        int maximumActive;
        final LongSupplier elapsedClock;

        FakeTransport(LongSupplier elapsedClock) {
            this.elapsedClock = elapsedClock;
        }

        @Override
        public AuthorLocationRepository.Cancellation fetch(ProfileSession session, int author,
                                                            Consumer<ProfileLocationResult> callback) {
            assertNull("One physical request may be in flight", active);
            active = new Pending();
            active.session = session;
            active.author = author;
            active.startedAt = elapsedClock.getAsLong();
            active.callback = callback;
            requests.add(active);
            maximumActive = Math.max(maximumActive, 1);
            Pending pending = active;
            return () -> pending.cancelled = true;
        }

        List<Integer> authors() {
            List<Integer> result = new ArrayList<>();
            for (Pending request : requests) {
                result.add(request.author);
            }
            return result;
        }

        List<Long> startTimes() {
            List<Long> result = new ArrayList<>();
            for (Pending request : requests) {
                result.add(request.startedAt);
            }
            return result;
        }
    }
}
