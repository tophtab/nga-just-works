package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

/** Executes the production Page handoff with the real repository and deterministic delivery. */
public class AuthorLocationPageTest {
    @Test
    public void freshResponseKeepsKnownLocationThroughoutDeferredCacheHandoff() {
        Harness h = new Harness();
        h.seed(41, "广东");
        Display display = h.open(41);
        ThreadData first = page(41);
        display.page.deliver(first, true);
        h.settle();
        assertEquals(Collections.singletonList("广东"), display.values(41));

        ThreadData refreshed = page(41);
        assertNotSame(first, refreshed);
        display.page.deliver(refreshed, true);
        // Nothing clears the display while account settlement is still queued.
        assertEquals(Collections.singletonList("广东"), display.values(41));
        h.settle();
        assertEquals(Arrays.asList("广东", "广东"), display.values(41));
        assertEquals(Collections.singletonList(41), h.requested);
        AuthorLocationPage.Delivery latest = display.latest();
        display.page.deliver(refreshed, false);
        assertSame(latest, display.latest());
        assertEquals(Arrays.asList("广东", "广东", "广东"), display.values(41));
        assertTrue(h.settlements.isEmpty());
    }

    @Test
    public void activeOutputStillReceivesResultsWhileReplacementIsPending() {
        Harness h = new Harness();
        Display display = h.open(41);
        display.page.deliver(page(41), true);
        h.settle();
        display.page.deliver(page(41, 42), true);
        h.complete(ProfileLocationResult.success("广东"));
        assertEquals(Arrays.asList(null, "广东"), display.values(41));
        h.settle();
        assertEquals(Arrays.asList(null, "广东", "广东"), display.values(41));
        assertEquals(Collections.singletonList(41), h.requested);
        h.advance(500);
        assertEquals(Arrays.asList(41, 42), h.requested);
    }

    @Test
    public void handoffSharesInflightAuthorsAndRetiresRemovedQueuedAuthors() {
        Harness h = new Harness();
        Display display = h.open(41, 42, 43);
        display.page.deliver(page(41, 42), true);
        h.settle();
        display.page.deliver(page(43, 41), true);
        h.settle();
        assertEquals(Collections.singletonList(41), h.requested);
        h.complete(ProfileLocationResult.success("广东"));
        h.advance(500);
        assertEquals(Arrays.asList(41, 43), h.requested);
        h.complete(ProfileLocationResult.success("上海"));
        assertEquals("广东", display.drawn(41));
        assertEquals("上海", display.drawn(43));
        assertNull(display.drawn(42));
    }

    @Test
    public void replacementPrunesOldQueueBeforeAnExpiredPauseCanResumeDispatch() {
        Harness h = new Harness();
        Display display = h.open(41, 42, 43);
        display.page.deliver(page(41, 42), true);
        h.settle();
        h.complete(ProfileLocationResult.rateLimit(h.now + AuthorLocationCache.RATE_LIMIT_MILLIS));
        h.now += AuthorLocationCache.RATE_LIMIT_MILLIS;
        h.advance(500);
        display.page.deliver(page(43), true);
        h.settle();
        // subscribe-new must publish/retire-old before dispatch can choose the old queued 42.
        assertEquals(Arrays.asList(41, 43), h.requested);
    }

    @Test
    public void retiredAuthorsCannotPublishIntoReplacementRows() {
        Harness h = new Harness();
        Display display = h.open(41, 43);
        display.page.deliver(page(41, 42), true);
        h.settle();
        display.page.deliver(page(43), true);
        h.settle();
        int deliveries = display.events.size();
        h.complete(ProfileLocationResult.success("广东"));
        assertEquals(deliveries, display.events.size());
        h.advance(500);
        assertEquals(Arrays.asList(41, 43), h.requested);
        h.complete(ProfileLocationResult.success("上海"));
        assertNull(display.drawn(41));
        assertEquals("上海", display.drawn(43));
    }

    @Test
    public void accountDomainAndCredentialSignalsClearDuringThePendingGap() {
        for (ProfileSession changed : Arrays.asList(
                ProfileSession.create("https://bbs.nga.cn", "8", "other-fixture", "Fixture UA"),
                ProfileSession.create("https://ngabbs.com", "7", "fixture", "Fixture UA"),
                ProfileSession.create("https://bbs.nga.cn", "7", "replacement-fixture", "Fixture UA"))) {
            Harness h = new Harness();
            h.seed(41, "广东");
            Display display = h.open(41);
            display.page.deliver(page(41), true);
            h.settle();
            AuthorLocationRepository.Snapshot formerlyDrawn = display.latest().snapshot;
            ThreadData pending = page(41, 42);
            display.page.deliver(pending, true);
            h.repository.invalidateSession();
            h.signal++;
            h.session = changed;
            // The still-active subscriber must clear text before settlement, even though a
            // re-read of the formerly drawn snapshot now also returns null.
            assertEquals(Arrays.asList("广东", null), display.values(41));
            assertNull(formerlyDrawn.location(41, h.now));
            h.settle();
            assertNull(display.drawn(41));
            display.page.deliver(pending, true);
            assertNull(display.drawn(41));
            assertEquals(Collections.singletonList(41), h.requested);
            display.page.deliver(page(42), true);
            h.settle();
            assertEquals(Arrays.asList(41, 42), h.requested);
            assertEquals(changed, h.active.session);
        }
    }

    @Test
    public void subscribeStillChecksUnobservedSessionChanges() {
        Harness h = new Harness();
        h.seed(41, "广东");
        Display display = h.open(41);
        display.page.deliver(page(41), true);
        h.settle();
        h.session = ProfileSession.create("https://ngabbs.com", "8", "other-fixture", "Fixture UA");
        display.page.deliver(page(41), true);
        h.settle();
        assertEquals(Arrays.asList("广东", null), display.values(41));
        assertEquals(h.session, h.active.session);
    }

    @Test
    public void rapidResponsesInstallOnlyTheLatestPendingAuthors() {
        Harness h = new Harness();
        Display display = h.open(41, 42);
        display.page.deliver(page(41), true);
        display.page.deliver(page(42), true);
        assertEquals(2, h.settlements.size());
        h.settle();
        assertEquals(Collections.singletonList(42), h.requested);
        assertEquals(1, display.events.size());
    }

    @Test
    public void readyReplayWhilePendingCannotPromoteCacheOnlyIntent() {
        Harness h = new Harness();
        Display display = h.open(41);
        ThreadData retained = page(41);
        display.page.deliver(retained, false);
        display.page.deliver(retained, true);
        assertEquals(1, h.settlements.size());
        h.settle();
        assertTrue(h.requested.isEmpty());
        AuthorLocationPage.Delivery first = display.latest();
        display.page.deliver(retained, true);
        assertSame(first, display.latest());
        assertTrue(h.settlements.isEmpty());
        assertTrue(h.requested.isEmpty());
        display.page.deliver(page(41), true);
        h.settle();
        assertEquals(Collections.singletonList(41), h.requested);
    }

    @Test
    public void replayAfterInvalidationUsesCurrentEmptyOutput() {
        Harness h = new Harness();
        h.seed(41, "广东");
        Display display = h.open(41);
        ThreadData data = page(41);
        display.page.deliver(data, true);
        h.settle();
        h.repository.invalidateSession();
        display.page.deliver(data, true);
        assertEquals(Arrays.asList("广东", null, null), display.values(41));
        assertTrue(h.settlements.isEmpty());
        assertEquals(Collections.singletonList(41), h.requested);
    }

    @Test
    public void readyAndCacheOnlyReplayReevaluateExpiryWithoutRequests() {
        Harness h = new Harness();
        h.seed(41, "广东");
        Display display = h.open(41);
        ThreadData data = page(41);
        display.page.deliver(data, false);
        h.settle();
        h.now += AuthorLocationCache.FRESH_MILLIS;
        display.page.replay();
        assertEquals(Arrays.asList("广东", null), display.values(41));
        display.page.deliver(data, true);
        display.page.deliver(page(41), false);
        h.settle();
        assertNull(display.drawn(41));
        assertEquals(Collections.singletonList(41), h.requested);
    }

    @Test
    public void recreatedCacheOnlyPageCannotResumeOtherPagesExpiredServerPause() {
        Harness h = new Harness();
        Display online = h.open(41, 42);
        online.page.deliver(page(41, 42), true);
        h.settle();
        h.complete(ProfileLocationResult.rateLimit(h.now + AuthorLocationCache.RATE_LIMIT_MILLIS));
        h.now += AuthorLocationCache.RATE_LIMIT_MILLIS;
        h.advance(500);
        Display recreated = h.open(99);
        ThreadData retained = page(99);
        recreated.page.deliver(retained, false);
        h.settle();
        recreated.page.deliver(retained, true);
        recreated.page.replay();
        assertEquals(Collections.singletonList(41), h.requested);
        assertTrue(h.settlements.isEmpty());
    }

    @Test
    public void newRowsUseTheirOwnAuthorsAndAnonymousOrMissingValuesStayEmpty() {
        Harness h = new Harness();
        h.seed(41, "广东");
        h.seed(42, "上海");
        h.seed(44, null);
        Display display = h.open(41, 42, 43, 44, 0, -1);
        display.page.deliver(page(41, 42), true);
        h.settle();
        ThreadData replacement = page(42, 43, 44, 41, 0, -1);
        replacement.getRowList().get(3).setISANONYMOUS(true);
        replacement.getRowList().add(null);
        display.page.deliver(replacement, true);
        h.settle();
        assertNull(display.drawn(41));
        assertEquals("上海", display.drawn(42));
        assertNull(display.drawn(43));
        assertNull(display.drawn(44));
        assertNull(display.drawn(0));
        assertNull(display.drawn(-1));
        assertEquals(Arrays.asList(41, 42, 44, 43), h.requested);
        h.complete(ProfileLocationResult.success("江苏"));
        assertEquals("江苏", display.drawn(43));
        assertEquals("上海", display.drawn(42));
    }

    @Test
    public void nullAlwaysClearsIdentityAndObsoletesPendingWork() {
        Harness h = new Harness();
        Display display = h.open(41);
        ThreadData data = page(41);
        display.page.deliver(data, true);
        display.page.deliver(null, false);
        display.page.deliver(null, false);
        assertEquals(Arrays.asList(null, null), display.values(41));
        h.settle();
        assertTrue(h.requested.isEmpty());
        display.page.deliver(data, true);
        h.settle();
        assertEquals(Collections.singletonList(41), h.requested);
    }

    @Test
    public void closeBeforeSettlementOrAfterRegistrationReleasesWorkAndSuppressesOutput() {
        Harness h = new Harness();
        Display pending = h.open(41);
        pending.page.deliver(page(41), true);
        pending.page.close();
        h.settle();
        assertTrue(h.requested.isEmpty());
        assertTrue(pending.events.isEmpty());
        pending.page.deliver(page(41), true);
        pending.page.replay();
        assertTrue(h.settlements.isEmpty());

        Display active = h.open(42, 43);
        active.page.deliver(page(42, 43), true);
        h.settle();
        AuthorLocationPage.Delivery previous = active.latest();
        active.page.close();
        active.page.close();
        assertFalse(active.page.isCurrent(previous));
        int emitted = active.events.size();
        h.complete(ProfileLocationResult.success("广东"));
        h.advance(500);
        assertEquals(emitted, active.events.size());
        assertEquals(Collections.singletonList(42), h.requested);
    }

    @Test
    public void closingInSynchronousInitialOutputDisposesTheReturnedSubscription() {
        Harness h = new Harness();
        Display unrelated = h.open(99);
        unrelated.page.deliver(page(99), true);
        h.settle();
        Display closing = h.open(41, 42);
        closing.afterOutput = delivery -> closing.page.close();
        closing.page.deliver(page(41, 42), true);
        h.settle();
        assertFalse(closing.page.isCurrent(closing.latest()));
        h.complete(ProfileLocationResult.success("广东"));
        h.advance(500);
        assertEquals(Collections.singletonList(99), h.requested);
        h.repository.invalidateSession();
        assertEquals(1, closing.events.size());
    }

    @Test
    public void synchronousCloseOrNullOutputCannotDispatchAfterThePageHasRetired() {
        for (boolean clear : Arrays.asList(false, true)) {
            Harness h = new Harness();
            Display display = h.open(41, 42);
            display.afterOutput = delivery -> {
                display.afterOutput = null;
                if (clear) display.page.deliver(null, false);
                else display.page.close();
            };
            display.page.deliver(page(41, 42), true);
            h.settle();
            assertTrue("A retired page must not dispatch from its initial output", h.requested.isEmpty());
            h.advance(500);
            assertTrue(h.requested.isEmpty());
        }
    }

    @Test
    public void synchronousResetAndReplacementCannotLeakAConsumerOrOverrideLatestOutput() {
        for (boolean clear : Arrays.asList(false, true)) {
            Harness h = new Harness();
            Display unrelated = h.open(99);
            unrelated.page.deliver(page(99), true);
            h.settle();
            Display replacing = h.open(41, 42);
            replacing.afterOutput = delivery -> {
                replacing.afterOutput = null;
                replacing.page.deliver(clear ? null : page(42), true);
            };
            replacing.page.deliver(page(41), true);
            h.settle();
            assertTrue(replacing.page.isCurrent(replacing.latest()));
            assertFalse(replacing.page.isCurrent(replacing.events.get(0)));
            h.complete(ProfileLocationResult.success("广东"));
            h.advance(500);
            assertEquals(clear ? Collections.singletonList(99) : Arrays.asList(99, 42), h.requested);
        }
    }

    private static ThreadData page(int... authors) {
        ThreadData data = new ThreadData();
        List<ThreadRowInfo> rows = new ArrayList<>();
        for (int author : authors) {
            ThreadRowInfo row = new ThreadRowInfo();
            row.setAuthorid(author);
            rows.add(row);
        }
        data.setRowList(rows);
        return data;
    }

    private static final class Display {
        final AuthorLocationPage page;
        final List<AuthorLocationPage.Delivery> events = new ArrayList<>();
        final List<Map<Integer, String>> drawn = new ArrayList<>();
        Consumer<AuthorLocationPage.Delivery> afterOutput;

        Display(Harness h, int... authors) {
            page = new AuthorLocationPage(h.repository, h.settlements::add, () -> h.signal, delivery -> {
                events.add(delivery);
                Map<Integer, String> values = new LinkedHashMap<>();
                // Capture at emission time: invalidation/expiry must not rewrite our history.
                for (int author : authors) values.put(author, delivery.snapshot.location(author, h.now));
                drawn.add(values);
                if (afterOutput != null) afterOutput.accept(delivery);
            });
        }

        AuthorLocationPage.Delivery latest() { return events.get(events.size() - 1); }

        String drawn(int author) { return drawn.get(drawn.size() - 1).get(author); }

        List<String> values(int author) {
            List<String> values = new ArrayList<>();
            for (Map<Integer, String> event : drawn) values.add(event.get(author));
            return values;
        }
    }

    private static final class Harness {
        long now = 1_700_000_000_000L;
        long elapsed;
        long signal;
        ProfileSession session = ProfileSession.create("https://bbs.nga.cn", "7", "fixture", "Fixture UA");
        final Queue<Runnable> settlements = new ArrayDeque<>();
        final Queue<Runnable> completions = new ArrayDeque<>();
        final List<Timer> timers = new ArrayList<>();
        final List<Integer> requested = new ArrayList<>();
        Pending active;
        final AuthorLocationRepository repository = new AuthorLocationRepository(
                (requestSession, author, callback) -> {
                    assertNull("Only one physical profile request", active);
                    Pending pending = new Pending(requestSession, callback);
                    active = pending;
                    requested.add(author);
                    return () -> pending.cancelled = true;
                }, () -> now, () -> elapsed, () -> session, completions::add,
                (action, delay) -> {
                    Timer timer = new Timer(action, elapsed + delay);
                    timers.add(timer);
                    return () -> timer.cancelled = true;
                }, entries -> { });

        Harness() { repository.restore(Collections.emptyList()); }

        Display open(int... authors) { return new Display(this, authors); }

        void seed(int author, String location) {
            AuthorLocationRepository.Subscription seed = repository.subscribe(
                    Collections.singleton(author), true, snapshot -> { });
            complete(ProfileLocationResult.success(location));
            seed.close();
            advance(500);
        }

        void settle() {
            while (!settlements.isEmpty()) settlements.remove().run();
        }

        void complete(ProfileLocationResult result) {
            Pending request = active;
            active = null;
            request.callback.accept(result);
            while (!completions.isEmpty()) completions.remove().run();
        }

        void advance(long millis) {
            elapsed += millis;
            for (Timer timer : new ArrayList<>(timers)) {
                if (!timer.cancelled && timer.at <= elapsed) {
                    timer.cancelled = true;
                    timer.action.run();
                }
            }
        }
    }

    private static final class Pending {
        final ProfileSession session;
        final Consumer<ProfileLocationResult> callback;
        boolean cancelled;

        Pending(ProfileSession session, Consumer<ProfileLocationResult> callback) {
            this.session = session;
            this.callback = callback;
        }
    }

    private static final class Timer {
        final Runnable action;
        final long at;
        boolean cancelled;

        Timer(Runnable action, long at) {
            this.action = action;
            this.at = at;
        }
    }
}
