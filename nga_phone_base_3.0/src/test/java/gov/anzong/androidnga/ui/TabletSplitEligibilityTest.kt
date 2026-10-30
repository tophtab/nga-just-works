package gov.anzong.androidnga.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The window constraints that decide whether split rules may be registered at all. */
class TabletSplitEligibilityTest {

    @Test
    fun phonePortraitIsNotEligible() {
        assertFalse(TabletSplit.canSplit(393, 852))
    }

    @Test
    fun phoneLandscapeIsNotEligible() {
        // Wide enough for the 840dp rule, but the short side is still a phone's.
        assertFalse(TabletSplit.canSplit(852, 393))
    }

    @Test
    fun tabletPortraitIsNotEligible() {
        // Portrait is disallowed by the rule, so no rules are registered in portrait.
        assertFalse(TabletSplit.canSplit(800, 1280))
    }

    @Test
    fun wideTabletPortraitIsNotEligible() {
        // Wide enough for the 840dp rule and tall enough for the 600dp rule, but still portrait,
        // which the rules never split.
        assertFalse(TabletSplit.canSplit(1024, 1366))
    }

    @Test
    fun squareWindowIsNotEligible() {
        // SplitRule.checkParentBounds treats height >= width as portrait, and portrait is
        // ALWAYS_DISALLOW.
        assertFalse(TabletSplit.canSplit(900, 900))
    }

    @Test
    fun tabletLandscapeIsEligible() {
        assertTrue(TabletSplit.canSplit(1280, 800))
    }

    @Test
    fun smallestSupportedWindowIsEligible() {
        assertTrue(TabletSplit.canSplit(840, 600))
    }

    @Test
    fun justBelowEitherBoundIsNotEligible() {
        assertFalse(TabletSplit.canSplit(839, 600))
        assertFalse(TabletSplit.canSplit(840, 599))
    }

    @Test
    fun unfoldedFoldableLandscapeIsEligible() {
        assertTrue(TabletSplit.canSplit(1000, 700))
    }
}
