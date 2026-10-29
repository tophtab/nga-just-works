package com.justwen.androidnga.ui.compose.miuix

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Toast in the Miuix appearance, matching Miuix Snackbar: surfaceContainerHighest background,
 * onSurfaceContainer text, 12dp corners, body2 (14sp) text with 12dp/8dp inner margin, 44dp
 * minimum height. Success, error and plain messages share this look, as Miuix uses one neutral
 * snackbar instead of coloured toasts.
 *
 * A custom-view toast is shown only while the app is in the foreground, like any app toast.
 */
object MiuixToast {

    // Miuix colour scheme: surfaceContainerHighest / onSurfaceContainer (light and dark).
    private const val LIGHT_CONTAINER = 0xFFE8E8E8.toInt()
    private const val LIGHT_CONTENT = 0xFF000000.toInt()
    private const val DARK_CONTAINER = 0xFF2D2D2D.toInt()
    private const val DARK_CONTENT = 0xFFE9E9E9.toInt()

    @JvmStatic
    fun show(context: Context, text: CharSequence, longDuration: Boolean = false) {
        val app = context.applicationContext
        val dark = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES || NgaUiStyle.isNightMode()
        val density = app.resources.displayMetrics.density
        val view = TextView(app).apply {
            this.text = text
            setTextColor(if (dark) DARK_CONTENT else LIGHT_CONTENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (44 * density).toInt()
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(if (dark) DARK_CONTAINER else LIGHT_CONTAINER)
                setStroke((0.6f * density).toInt().coerceAtLeast(1), if (dark) 0x14FFFFFF else 0x14000000)
            }
            elevation = 2 * density
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
        @Suppress("DEPRECATION")
        Toast(app).apply {
            duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            this.view = view
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, systemToastOffset(app, density))
        }.show()
    }

    /**
     * The system toast's distance from the bottom: the platform `toast_y_offset` (OEMs tune it, e.g.
     * 8dp portrait / 24dp landscape on HyperOS Pad, 48dp in AOSP) plus a small gap, so the Miuix
     * toast sits where system toasts do, just above the navigation bar, instead of a fixed 96dp.
     */
    private fun systemToastOffset(context: Context, density: Float): Int {
        val res = context.resources
        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        val id = res.getIdentifier("toast_y_offset", "dimen", "android")
        val system = if (id != 0) res.getDimensionPixelSize(id) else (48 * density).toInt()
        return system + (BOTTOM_GAP_DP * density).toInt()
    }

    private const val BOTTOM_GAP_DP = 16
}
