package gov.anzong.androidnga.activity.compose.board

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.TimeUnit

/** All calls run on Main; suspension never transfers ownership of the guard. */
internal class BoardRefreshGate(
    private val readLastAttempt: () -> Long,
    private val writeLastAttempt: (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var inFlight = false

    suspend fun <T> refresh(request: suspend () -> T?, apply: (T) -> Unit) {
        if (inFlight || clock() - readLastAttempt() < TimeUnit.DAYS.toMillis(1)) return
        inFlight = true
        try {
            val result = request()
            currentCoroutineContext().ensureActive()
            result?.let(apply)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the local tree and normal daily attempt throttle on failure.
        } finally {
            try {
                // Cancellation releases ownership but must not create a rapid retry loop.
                writeLastAttempt(clock())
            } finally {
                inFlight = false
            }
        }
    }
}
