package sp.phone.mvp.viewmodel;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ArticlePagePrefetchPlannerTest {

    @Test
    public void plansOnlyTheNextTwoNonFinalPages() {
        assertEquals(Arrays.asList(4, 5), ArticlePagePrefetchPlanner.plan(3, 6));
        assertEquals(Collections.singletonList(4), ArticlePagePrefetchPlanner.plan(3, 5));
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(3, 4));
    }

    @Test
    public void handlesFirstPenultimateAndFinalPages() {
        assertEquals(Arrays.asList(2, 3), ArticlePagePrefetchPlanner.plan(1, 5));
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(4, 5));
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(5, 5));
    }

    @Test
    public void rejectsInvalidAndOverflowingBoundaries() {
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(0, 5));
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(1, 0));
        assertEquals(Collections.emptyList(), ArticlePagePrefetchPlanner.plan(6, 5));
        assertEquals(
                Collections.singletonList(Integer.MAX_VALUE - 1),
                ArticlePagePrefetchPlanner.plan(Integer.MAX_VALUE - 2, Integer.MAX_VALUE));
    }

}
