package gov.anzong.androidnga.base.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/**
 * Created by Justwen on 2017/7/16.
 */
object DeviceUtils {

    private const val NAVIGATION_MODE = "navigation_mode"

    /** MIUI keeps its full screen gesture switch outside of [NAVIGATION_MODE]. */
    private const val MIUI_FULL_SCREEN_GESTURE = "force_fsg_nav_bar"

    private const val NAVIGATION_MODE_THREE_BUTTON = 0

    private const val NAVIGATION_MODE_GESTURE = 2

    /**
     * Whether the system shows on screen navigation buttons, either the three button bar or
     * the two button pill that still keeps a back key.
     *
     * Gesture navigation already owns the screen edge back swipe, so the in app swipe back is
     * only wired up when buttons are present. Resolved synchronously on purpose: the swipe back
     * layout has to be attached during onCreate, long before window insets are dispatched, so
     * an insets based check is not an option here.
     */
    @JvmStatic
    fun hasNavigationButtons(context: Context): Boolean {
        try {
            val resolver = context.contentResolver
            if (Settings.Global.getInt(resolver, MIUI_FULL_SCREEN_GESTURE, 0) != 0) {
                return false
            }
            val mode = Settings.Secure.getInt(resolver, NAVIGATION_MODE, NAVIGATION_MODE_THREE_BUTTON)
            return mode != NAVIGATION_MODE_GESTURE
        } catch (e: Exception) {
            // Unreadable navigation mode falls back to the pre swipe back behaviour.
            return false
        }
    }

    @JvmStatic
    fun isWifiConnected(context: Context): Boolean {
        try {
            val conMan = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = conMan.getNetworkCapabilities(conMan.activeNetwork)
            capabilities?.let {
                return it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }
}
