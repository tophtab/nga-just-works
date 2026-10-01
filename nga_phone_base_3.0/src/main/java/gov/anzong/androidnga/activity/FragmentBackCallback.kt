package gov.anzong.androidnga.activity

import androidx.activity.OnBackPressedCallback
import java.util.function.BooleanSupplier

/** Shared system-back boundary for the two legacy fragment-hosting activities. */
internal class FragmentBackCallback(
    private val available: BooleanSupplier,
    private val consume: BooleanSupplier,
    private val fallback: Runnable,
) : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        if (available.asBoolean && consume.asBoolean) return
        val wasEnabled = isEnabled
        isEnabled = false
        try {
            fallback.run()
        } finally {
            isEnabled = wasEnabled
        }
    }
}
