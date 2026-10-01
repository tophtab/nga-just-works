package gov.anzong.androidnga.activity

import org.junit.Assert.*
import org.junit.Test

class FragmentBackCallbackTest {
    @Test fun consumedPanelDoesNotLeaveActivity() {
        var consumed = 0
        val callback = FragmentBackCallback({ true }, { consumed++; true }, { fail("fallback") })
        callback.handleOnBackPressed()
        assertEquals(1, consumed)
        assertTrue(callback.isEnabled)
    }

    @Test fun unconsumedBackDelegatesOnceWhileDisabledAndRestores() {
        var delegated = 0
        lateinit var callback: FragmentBackCallback
        callback = FragmentBackCallback({ true }, { false }, {
            assertFalse(callback.isEnabled)
            delegated++
        })
        callback.handleOnBackPressed()
        assertEquals(1, delegated)
        assertTrue(callback.isEnabled)
    }

    @Test fun absentOrDestroyedViewNeverTouchesConsumer() {
        var available = true
        var consumed = 0
        var delegated = 0
        val callback = FragmentBackCallback({ available }, { consumed++; true }, { delegated++ })
        callback.handleOnBackPressed()
        available = false
        callback.handleOnBackPressed()
        assertEquals(1, consumed)
        assertEquals(1, delegated)
    }

    @Test fun fallbackFailureStillRestoresCallback() {
        val failure = IllegalStateException("synthetic fallback")
        val callback = FragmentBackCallback({ false }, { fail("consumer"); false }, { throw failure })
        try {
            callback.handleOnBackPressed()
            fail("expected failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertTrue(callback.isEnabled)
    }
}
