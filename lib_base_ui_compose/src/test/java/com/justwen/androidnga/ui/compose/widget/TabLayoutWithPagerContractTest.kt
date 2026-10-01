package com.justwen.androidnga.ui.compose.widget

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TabLayoutWithPagerContractTest {

    @Test
    fun renderedSlotsDriveConsecutiveMovesWithoutRecomposition() {
        val renderedOrder = listOf("bookmark", "other", "games", "wow")
        val renderedBounds = mapOf(
            "bookmark" to Rect(0f, 0f, 10f, 10f),
            "other" to Rect(10f, 0f, 20f, 10f),
            "games" to Rect(20f, 0f, 30f, 10f),
            "wow" to Rect(30f, 0f, 40f, 10f),
        )
        val visibleBounds = Rect(0f, 0f, 40f, 10f)
        val reorderableRange = 1..3

        var gestureOrder = renderedOrder
        val firstTarget = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 25f,
            visibleBounds = visibleBounds,
        )
        assertEquals(2, firstTarget)
        val first = resolveStableTabMove(
            gestureOrder,
            draggedKey = "other",
            targetIndex = requireNotNull(firstTarget),
        )
        assertNotNull(first)
        assertEquals(1, first!!.fromIndex)
        gestureOrder = first.order

        val heldSlot = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 25f,
            visibleBounds = visibleBounds,
        )
        assertNull(
            resolveStableTabMove(
                gestureOrder,
                draggedKey = "other",
                targetIndex = requireNotNull(heldSlot),
            )
        )

        val secondTarget = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 35f,
            visibleBounds = visibleBounds,
        )
        assertEquals(3, secondTarget)
        val second = resolveStableTabMove(
            gestureOrder,
            draggedKey = "other",
            targetIndex = requireNotNull(secondTarget),
        )
        assertNotNull(second)
        assertEquals(2, second!!.fromIndex)
        assertEquals(listOf("bookmark", "games", "wow", "other"), second.order)
    }
}
