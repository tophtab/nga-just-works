package sp.phone.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class LoadingTipStateTest {

    @Test
    public void visibleButNotResumedPrefetchNeverConsumesATip() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();

        assertEquals(0, state.update(true, false, true, selections::incrementAndGet));
        assertEquals(0, state.update(true, false, true, selections::incrementAndGet));
        // A background completion hides the loader before its host becomes current.
        assertEquals(0, state.update(false, false, true, selections::incrementAndGet));
        assertEquals(0, state.update(false, true, true, selections::incrementAndGet));
        assertEquals(0, selections.get());
    }

    @Test
    public void resumingWithoutAnAttachedVisibleWindowDoesNotSelect() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();

        assertEquals(0, state.update(true, true, false, selections::incrementAndGet));
        assertEquals(0, selections.get());
        assertEquals(1, state.update(true, true, true, selections::incrementAndGet));
        assertEquals(1, selections.get());
    }

    @Test
    public void enteringAnUnfinishedPrefetchSelectsOnce() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();

        assertEquals(0, state.update(true, false, true, selections::incrementAndGet));
        assertEquals(1, state.update(true, true, true, selections::incrementAndGet));
        assertEquals(1, state.update(true, true, true, selections::incrementAndGet));
        assertEquals(1, selections.get());
    }

    @Test
    public void pausingAndAncestorHidingPreserveTheUnfinishedOccasion() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();
        IntSupplier choose = () -> 100 + selections.incrementAndGet();

        assertEquals(101, state.update(true, true, true, choose));
        assertEquals(0, state.update(true, false, true, choose));
        assertEquals(101, state.update(true, true, true, choose));
        assertEquals(0, state.update(true, true, false, choose));
        assertEquals(0, state.update(true, false, false, choose));
        assertEquals(101, state.update(true, true, true, choose));
        assertEquals(1, selections.get());
    }

    @Test
    public void hidingTheLoaderFinishesTheOccasionEvenWhileOffscreen() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();
        IntSupplier choose = () -> 100 + selections.incrementAndGet();

        assertEquals(101, state.update(true, true, true, choose));
        assertEquals(0, state.update(false, false, false, choose));
        assertEquals(0, state.update(false, true, true, choose));
        assertEquals(1, selections.get());
        assertEquals(102, state.update(true, true, true, choose));
        assertEquals(2, selections.get());
    }

    @Test
    public void aCompletedLoaderStaysEmptyDuringContentRefreshCallbacks() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();

        assertEquals(1, state.update(true, true, true, selections::incrementAndGet));
        for (int i = 0; i < 5; i++) {
            assertEquals(0, state.update(false, true, true, selections::incrementAndGet));
        }
        assertEquals(1, selections.get());
    }

    @Test
    public void anEmptyCatalogIsSelectedOnlyOncePerOccasion() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();
        IntSupplier choose = () -> {
            selections.incrementAndGet();
            return 0;
        };

        assertEquals(0, state.update(true, true, true, choose));
        assertEquals(0, state.update(true, false, true, choose));
        assertEquals(0, state.update(true, true, true, choose));
        assertEquals(1, selections.get());
        assertEquals(0, state.update(false, true, true, choose));
        assertEquals(0, state.update(true, true, true, choose));
        assertEquals(2, selections.get());
    }

    @Test
    public void separateViewsShareRepeatAvoidanceButKeepTheirOwnOccasion() {
        LoadingTipSelector selector = new LoadingTipSelector(new Random(12));
        IntSupplier choose = () -> selector.next(Arrays.asList(10, 20, 30));
        LoadingTipState firstView = new LoadingTipState();
        LoadingTipState secondView = new LoadingTipState();

        int first = firstView.update(true, true, true, choose);
        assertEquals(0, firstView.update(true, false, true, choose));
        int second = secondView.update(true, true, true, choose);
        assertNotEquals(first, second);
        assertEquals(first, firstView.update(true, true, true, choose));
    }

    @Test
    public void destroyingTheOwnerDropsThePreviousOccasion() {
        LoadingTipState state = new LoadingTipState();
        AtomicInteger selections = new AtomicInteger();

        assertEquals(1, state.update(true, true, true, selections::incrementAndGet));
        state.reset();
        assertEquals(0, state.update(true, false, true, selections::incrementAndGet));
        assertEquals(2, state.update(true, true, true, selections::incrementAndGet));
    }
}
