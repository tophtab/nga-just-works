package gov.anzong.androidnga.activity.compose.drawer

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class NavigationDrawerGestureTest {

    private val pagerBounds = Rect(left = 0f, top = 100f, right = 300f, bottom = 600f)

    @Test
    fun openingCanStartAnywhereInsideSettledFavoritePager() {
        val eligibility = eligible()

        assertTrue(canOpenHomeDrawerAtDown(eligibility, Offset(1f, 101f)))
        assertTrue(canOpenHomeDrawerAtDown(eligibility, Offset(150f, 350f)))
        assertTrue(canOpenHomeDrawerAtDown(eligibility, Offset(299f, 599f)))
    }

    @Test
    fun toolbarLaterPageUnsettledPagerAndReorderCannotOpen() {
        assertFalse(canOpenHomeDrawerAtDown(eligible(), Offset(150f, 50f)))
        assertFalse(
            canOpenHomeDrawerAtDown(eligible().copy(settledPage = 1), Offset(150f, 350f))
        )
        assertFalse(
            canOpenHomeDrawerAtDown(eligible().copy(pagerSettled = false), Offset(150f, 350f))
        )
        assertFalse(
            canOpenHomeDrawerAtDown(
                eligible().copy(favoriteReorderActive = true),
                Offset(150f, 350f),
            )
        )
    }

    @Test
    fun closedDrawerClaimsOnlyLogicalLeadingHorizontalMovement() {
        assertEquals(
            HomeDrawerGestureOwner.Drawer,
            decide(displacement = Offset(3f, 0f), layoutDirection = LayoutDirection.Ltr),
        )
        assertEquals(
            HomeDrawerGestureOwner.Drawer,
            decide(displacement = Offset(-3f, 0f), layoutDirection = LayoutDirection.Rtl),
        )
        assertEquals(
            HomeDrawerGestureOwner.Content,
            decide(displacement = Offset(-3f, 0f), layoutDirection = LayoutDirection.Ltr),
        )
        assertEquals(
            HomeDrawerGestureOwner.Content,
            decide(displacement = Offset(3f, 0f), layoutDirection = LayoutDirection.Rtl),
        )
    }

    @Test
    fun jitterWaitsAndVerticalMovementStaysWithContent() {
        assertEquals(
            HomeDrawerGestureOwner.Undecided,
            decide(displacement = Offset(1.9f, 0f)),
        )
        assertEquals(
            HomeDrawerGestureOwner.Content,
            decide(displacement = Offset(3f, 4f)),
        )
        assertEquals(
            HomeDrawerGestureOwner.Content,
            decide(displacement = Offset(4f, 4f)),
        )
    }

    @Test
    fun openDrawerOwnsEitherHorizontalDirectionForDragClose() {
        assertEquals(
            HomeDrawerGestureOwner.Drawer,
            decideHomeDrawerGestureOwner(
                drawerVisibleAtDown = true,
                canOpenAtDown = false,
                layoutDirection = LayoutDirection.Ltr,
                displacement = Offset(-3f, 0f),
                directionJitter = 2f,
            )
        )
        assertEquals(
            HomeDrawerGestureOwner.Drawer,
            decideHomeDrawerGestureOwner(
                drawerVisibleAtDown = true,
                canOpenAtDown = false,
                layoutDirection = LayoutDirection.Rtl,
                displacement = Offset(3f, 0f),
                directionJitter = 2f,
            )
        )
        assertEquals(
            HomeDrawerGestureOwner.Content,
            decideHomeDrawerGestureOwner(
                drawerVisibleAtDown = true,
                canOpenAtDown = false,
                layoutDirection = LayoutDirection.Ltr,
                displacement = Offset(3f, 4f),
                directionJitter = 2f,
            )
        )
    }

    @Test
    fun fastFlingPicksAnchorByDirectionRegardlessOfHowFarTheSheetTravelled() {
        // 只越过 10px 就快速右甩，仍然开；已经拉开九成再快速左甩，仍然关。
        assertEquals(
            HomeDrawerValue.Open,
            settleTarget(offset = -270f, velocity = 3200f),
        )
        assertEquals(
            HomeDrawerValue.Closed,
            settleTarget(offset = -28f, currentValue = HomeDrawerValue.Open, velocity = -3200f),
        )
    }

    @Test
    fun slowReleaseFallsBackToTheHalfWidthPositionalThreshold() {
        assertEquals(
            HomeDrawerValue.Closed,
            settleTarget(offset = -141f, velocity = 120f),
        )
        assertEquals(
            HomeDrawerValue.Open,
            settleTarget(offset = -140f, velocity = 120f),
        )
        assertEquals(
            HomeDrawerValue.Open,
            settleTarget(offset = -139f, currentValue = HomeDrawerValue.Open, velocity = -120f),
        )
        assertEquals(
            HomeDrawerValue.Closed,
            settleTarget(offset = -140f, currentValue = HomeDrawerValue.Open, velocity = -120f),
        )
    }

    @Test
    fun settlingBeforeTheSheetIsMeasuredKeepsTheCurrentAnchor() {
        assertEquals(
            HomeDrawerValue.Closed,
            settleTarget(offset = 0f, sheetWidth = 0f, velocity = 3200f),
        )
    }

    @Test
    fun sheetUsesAbsolutePhysicalOffsetsForBothLayoutDirections() {
        assertEquals(-280f, homeDrawerPhysicalOffset(-280f, LayoutDirection.Ltr), 0f)
        assertEquals(280f, homeDrawerPhysicalOffset(-280f, LayoutDirection.Rtl), 0f)
        assertEquals(0f, homeDrawerPhysicalOffset(0f, LayoutDirection.Ltr), 0f)
        assertEquals(0f, homeDrawerPhysicalOffset(0f, LayoutDirection.Rtl), 0f)
    }

    @Test
    fun resizingKeepsASettledClosedDrawerAtTheClosedAnchor() {
        val drawerState = HomeDrawerState(
            initialValue = HomeDrawerValue.Closed,
            velocityThresholdPx = HomeDrawerVelocityThresholdDp,
            decayAnimationSpec = exponentialDecay(),
        )

        drawerState.updateSheetWidth(280f)
        assertEquals(-280f, drawerState.offset, 0f)

        drawerState.updateSheetWidth(700f)
        assertEquals(-700f, drawerState.offset, 0f)
        assertEquals(HomeDrawerValue.Closed, drawerState.settledValue)
    }

    @Test
    fun immediateResetRestoresTheCapturedStableAnchorAfterCoroutineCancellation() = runBlocking {
        val drawerState = HomeDrawerState(
            initialValue = HomeDrawerValue.Closed,
            velocityThresholdPx = HomeDrawerVelocityThresholdDp,
            decayAnimationSpec = exponentialDecay(),
        )
        drawerState.updateSheetWidth(280f)

        drawerState.resetTo(HomeDrawerValue.Open)
        assertEquals(0f, drawerState.offset, 0f)

        drawerState.resetTo(HomeDrawerValue.Closed)
        assertEquals(-280f, drawerState.offset, 0f)
        assertEquals(HomeDrawerValue.Closed, drawerState.settledValue)
    }

    @Test
    fun onlyUnconsumedSinglePointerReleaseSettles() {
        assertTrue(
            isValidHomeDrawerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = false,
                anyPointerPressed = false,
            )
        )
        assertFalse(
            isValidHomeDrawerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = true,
                anyPointerPressed = false,
            )
        )
        assertFalse(
            isValidHomeDrawerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = false,
                anyPointerPressed = true,
            )
        )
        assertFalse(
            isValidHomeDrawerRelease(
                eventType = PointerEventType.Move,
                trackedChangeConsumed = false,
                anyPointerPressed = false,
            )
        )
    }

    private fun eligible() = HomeDrawerGestureEligibility(
        pagerBounds = pagerBounds,
        settledPage = 0,
        pagerSettled = true,
    )

    private fun decide(
        displacement: Offset,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = decideHomeDrawerGestureOwner(
        drawerVisibleAtDown = false,
        canOpenAtDown = true,
        layoutDirection = layoutDirection,
        displacement = displacement,
        directionJitter = 2f,
    )

    private fun settleTarget(
        offset: Float,
        velocity: Float,
        currentValue: HomeDrawerValue = HomeDrawerValue.Closed,
        sheetWidth: Float = 280f,
    ) = homeDrawerSettleTarget(
        currentValue = currentValue,
        offset = offset,
        sheetWidth = sheetWidth,
        velocity = velocity,
        velocityThresholdPx = 1200f,
    )
}
