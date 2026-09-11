package sp.phone.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LoadingTipSelectorTest {

    @Test
    public void emptyPoolHasNoTipAndDoesNotForgetThePreviousChoice() {
        LoadingTipSelector selector = new LoadingTipSelector(new Random(12));
        List<Integer> tips = Arrays.asList(10, 20);
        int first = selector.next(tips);

        assertEquals(0, selector.next(Collections.emptyList()));
        assertNotEquals(first, selector.next(tips));
    }

    @Test
    public void singleItemPoolCanBeReused() {
        LoadingTipSelector selector = new LoadingTipSelector(new Random(12));
        for (int i = 0; i < 10; i++) {
            assertEquals(10, selector.next(Collections.singletonList(10)));
        }
    }

    @Test
    public void eachChoiceBelongsToThePoolAndNeverImmediatelyRepeats() {
        LoadingTipSelector selector = new LoadingTipSelector(new Random(12));
        List<Integer> tips = Collections.unmodifiableList(Arrays.asList(10, 20, 30, 40));
        int previous = 0;
        for (int i = 0; i < 200; i++) {
            int selected = selector.next(tips);
            assertTrue(tips.contains(selected));
            assertNotEquals(previous, selected);
            previous = selected;
        }
        assertEquals(Arrays.asList(10, 20, 30, 40), tips);
    }

    @Test
    public void changingEligibilityUsesIdentityInsteadOfTheOldIndex() {
        LoadingTipSelector selector = new LoadingTipSelector(new Random(12));
        assertEquals(30, selector.next(Collections.singletonList(30)));
        assertEquals(10, selector.next(Arrays.asList(30, 10)));
        assertEquals(20, selector.next(Arrays.asList(20, 10)));

        int selected = selector.next(Arrays.asList(30, 40));
        assertTrue(selected == 30 || selected == 40);
    }
}
