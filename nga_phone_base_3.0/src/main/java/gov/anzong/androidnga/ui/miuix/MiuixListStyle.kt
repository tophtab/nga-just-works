package gov.anzong.androidnga.ui.miuix

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import gov.anzong.androidnga.R

/**
 * Miuix look for classic RecyclerView lists that keep their adapters: every row becomes a
 * rounded Miuix card with spacing instead of a divider, primary text uses the Miuix content
 * colour, secondary text the summary colour, accents the Miuix primary.
 */
object MiuixListStyle {

    private const val RADIUS_DP = 16f
    private const val MARGIN_H_DP = 12f
    private const val MARGIN_V_DP = 5f

    /** Applies the page part: transparent list on the Miuix surface, no dividers, nav-bar room. */
    /** Empty/loading text of classic list pages in the Miuix summary colour. */
    @JvmStatic
    fun styleEmptyText(view: View) {
        val summary = ContextCompat.getColor(view.context, R.color.miuix_on_surface_summary)
        forEach(view) { if (it is TextView) it.setTextColor(summary) }
    }

    @JvmStatic
    fun styleList(list: RecyclerView) {
        val density = list.resources.displayMetrics.density
        list.setBackgroundColor(ContextCompat.getColor(list.context, R.color.miuix_surface))
        while (list.itemDecorationCount > 0) list.removeItemDecorationAt(0)
        list.clipToPadding = false
        val top = (8 * density).toInt()
        list.updatePadding(top = list.paddingTop + top)
        val base = list.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            v.updatePadding(bottom = base + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
    }

    /**
     * Styles one row once, when its view holder is created. [primaryIds] are titles/names,
     * [accentIds] get the Miuix primary colour; other TextViews become summary text.
     */
    @JvmStatic
    @JvmOverloads
    fun styleRow(row: View, primaryIds: IntArray = IntArray(0), accentIds: IntArray = IntArray(0)) {
        val context = row.context
        val density = row.resources.displayMetrics.density
        val card = ContextCompat.getColor(context, R.color.miuix_surface_container)
        val onSurface = ContextCompat.getColor(context, R.color.miuix_on_surface)
        val summary = ContextCompat.getColor(context, R.color.miuix_on_surface_summary)
        val primary = ContextCompat.getColor(context, R.color.miuix_primary)
        val shape = GradientDrawable().apply {
            cornerRadius = RADIUS_DP * density
            setColor(card)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = RADIUS_DP * density
            setColor(Color.WHITE)
        }
        row.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 0x14)),
            shape,
            mask,
        )
        (row.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
            (MARGIN_H_DP * density).toInt(),
            (MARGIN_V_DP * density).toInt(),
            (MARGIN_H_DP * density).toInt(),
            (MARGIN_V_DP * density).toInt(),
        )
        forEach(row) { view ->
            when (view) {
                is android.widget.Switch -> styleSwitch(view)
                is CompoundButton -> {
                    view.buttonTintList = ColorStateList.valueOf(primary)
                }
                is TextView -> view.setTextColor(
                    when (view.id) {
                        in primaryIds -> onSurface
                        in accentIds -> primary
                        else -> summary
                    },
                )
            }
        }
    }

    /**
     * Miuix Switch look for a framework Switch: 49x28dp capsule track (primary / secondary) and a
     * white 20dp thumb, no ripple halo; the platform still animates the thumb between the sides.
     */
    @JvmStatic
    fun styleSwitch(switch: android.widget.Switch) {
        switch.trackDrawable = ContextCompat.getDrawable(switch.context, R.drawable.miuix_switch_track)
        switch.thumbDrawable = ContextCompat.getDrawable(switch.context, R.drawable.miuix_switch_thumb)
        switch.trackTintList = null
        switch.thumbTintList = null
        switch.showText = false
        switch.switchMinWidth = (49 * switch.resources.displayMetrics.density).toInt()
        switch.background = null
    }

    private fun forEach(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) forEach(view.getChildAt(i), action)
    }
}
