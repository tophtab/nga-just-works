package gov.anzong.androidnga.activity.compose.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoardOrderTest {

    private val defaults = listOf("other", "games", "wow", "bliz", "club")

    @Test
    fun savedOrderDropsUnknownAndDuplicateIdsThenAppendsMissingDefaults() {
        val resolved = HomeBoardOrderResolver.resolve(
            defaults,
            listOf("wow", "unknown", "wow", "club"),
        )

        assertEquals(listOf("wow", "club", "other", "games", "bliz"), resolved)
    }

    @Test
    fun malformedPreferenceFallsBackToDefaults() {
        val decoded = HomeBoardOrderResolver.decodeSavedIds("{broken")

        assertNull(decoded)
        assertEquals(defaults, HomeBoardOrderResolver.resolve(defaults, decoded))
    }

    @Test
    fun moveSupportsBothDirectionsAndRejectsInvalidOrNoOpIndices() {
        val forward = defaults.toMutableList()
        assertTrue(HomeBoardOrderResolver.move(forward, 0, 2))
        assertEquals(listOf("games", "wow", "other", "bliz", "club"), forward)

        val backward = defaults.toMutableList()
        assertTrue(HomeBoardOrderResolver.move(backward, 3, 1))
        assertEquals(listOf("other", "bliz", "games", "wow", "club"), backward)

        val unchanged = defaults.toMutableList()
        assertFalse(HomeBoardOrderResolver.move(unchanged, -1, 1))
        assertFalse(HomeBoardOrderResolver.move(unchanged, 1, unchanged.size))
        assertFalse(HomeBoardOrderResolver.move(unchanged, 2, 2))
        assertEquals(defaults, unchanged)
    }

    @Test
    fun defaultOrderProducesNoPreferenceValue() {
        assertNull(HomeBoardOrderResolver.preferenceValue(defaults, defaults))
        assertTrue(
            HomeBoardOrderResolver.preferenceValue(defaults, defaults.reversed())
                ?.contains("club") == true
        )
    }

    @Test
    fun olderFailedCandidateCannotRestoreOverNewerOrder() {
        val items = defaults.toMutableList()
        val candidate = listOf("games", "other", "wow", "bliz", "club")
        HomeBoardOrderResolver.move(items, 0, 1)
        items.add("future")

        assertFalse(HomeBoardOrderResolver.restoreIfCurrent(items, candidate, defaults))
        assertEquals(candidate + "future", items)

        assertTrue(HomeBoardOrderResolver.restoreIfCurrent(items, items.toList(), defaults))
        assertEquals(defaults, items)
    }
}
