package gov.anzong.androidnga.ui.miuix

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import gov.anzong.androidnga.R
import kotlin.math.roundToInt

/**
 * Miuix styling of the classic floor list: each floor becomes a rounded HyperOS card on the
 * Miuix surface, the refresh spinner takes the Miuix primary colour, and the list clears the
 * navigation bar because Miuix pages draw edge to edge.
 */
object MiuixReaderStyle {

    private const val PRESS_MS = 160L
    private const val CROSS_FADE_MS = 220
    private const val ROLL_MS = 260L

    private const val CARD_RADIUS_DP = 16f
    private const val CARD_MARGIN_H_DP = 12f
    private const val CARD_MARGIN_V_DP = 6f

    /**
     * Miuix icons and colours for one floor's header and action row. Called once per view
     * holder; view ids come from fragment_article_list_item.
     */
    @JvmStatic
    fun styleFloorViews(item: View) {
        val context = item.context
        val summary = ContextCompat.getColor(context, R.color.miuix_on_surface_summary)
        val tint = ColorStateList.valueOf(summary)
        // The classic layout gives thumb up/down a 24dp box with 3dp padding (18dp glyph) but
        // lets reply and more wrap their 24dp drawables, so they looked a size larger. All four
        // Miuix actions share one box and inset, so the glyphs render at the same 18dp.
        val box = (24 * item.resources.displayMetrics.density).toInt()
        val inset = (3 * item.resources.displayMetrics.density).toInt()
        // The thumbs animate their own press feedback; the platform press ripple (a grey disc that
        // lingered behind the icon) is dropped for them.
        item.findViewById<View>(R.id.iv_support)?.background = null
        item.findViewById<View>(R.id.iv_oppose)?.background = null
        listOf(
            R.id.iv_support to R.drawable.ic_miuix_like,
            R.id.iv_oppose to R.drawable.ic_miuix_dislike,
            R.id.iv_reply to R.drawable.ic_miuix_reply,
            R.id.iv_more to R.drawable.ic_miuix_more,
        ).forEach { (id, icon) ->
            item.findViewById<ImageView>(id)?.apply {
                setImageResource(icon)
                imageTintList = tint
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(inset, inset, inset, inset)
                layoutParams = layoutParams.apply {
                    width = box
                    height = box
                }
            }
        }
        // Classic beige metadata (floor, time, posts/location) becomes the Miuix summary colour.
        listOf(R.id.tv_floor, R.id.tv_post_time, R.id.tv_detail, R.id.tv_score).forEach { id ->
            item.findViewById<TextView>(id)?.setTextColor(summary)
        }
        item.findViewById<TextView>(R.id.tv_nickName)?.setTextColor(
            ContextCompat.getColor(context, R.color.miuix_primary),
        )
    }

    /** Styles a floor card once, when its view holder is created. */
    /**
     * The floor's vote state on its thumb icons: the voted one filled in the Miuix primary colour,
     * the other outlined in the summary colour. [state] is LikeTask.STATE_UP / STATE_DOWN / STATE_NONE.
     *
     * The icon each thumb shows is remembered on the view; when a thumb changes between outline
     * and filled while [animate] is true it plays the change animation, otherwise (binding,
     * recycling) the new icon is set directly.
     */
    @JvmStatic
    fun bindVoteState(support: ImageView, oppose: ImageView, state: Int, animate: Boolean) {
        bindThumb(support, state > 0, R.drawable.ic_miuix_like, R.drawable.ic_miuix_like_fill, animate)
        bindThumb(oppose, state < 0, R.drawable.ic_miuix_dislike, R.drawable.ic_miuix_dislike_fill, animate)
    }

    private fun bindThumb(icon: ImageView, voted: Boolean, outline: Int, filled: Int, animate: Boolean) {
        val shown = icon.getTag(R.id.miuix_vote_state) as? Boolean
        icon.setTag(R.id.miuix_vote_state, voted)
        icon.isSelected = voted
        if (shown == voted) return
        val context = icon.context
        val color = ContextCompat.getColor(context, if (voted) R.color.miuix_primary else R.color.miuix_on_surface_summary)
        if (!animate || shown == null) {
            (icon.getTag(R.id.miuix_vote_fade) as? ValueAnimator)?.end()
            icon.animate().cancel()
            icon.scaleX = 1f
            icon.scaleY = 1f
            icon.rotation = 0f
            icon.setImageResource(if (voted) filled else outline)
            icon.imageTintList = ColorStateList.valueOf(color)
            return
        }
        if (voted) playVoteOn(icon, filled, color) else playVoteOff(icon, outline, color)
    }

    /**
     * Voting: the outline dips with a small tilt while it cross-fades into the filled icon, which
     * then springs back past its size and settles; a primary ring spreads out of it and fades.
     */
    private fun playVoteOn(icon: ImageView, filled: Int, color: Int) {
        icon.animate().cancel()
        crossFade(icon, filled, color, CROSS_FADE_MS)
        icon.animate()
            .scaleX(0.7f).scaleY(0.7f).rotation(-14f)
            .setDuration(PRESS_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                icon.animate()
                    .scaleX(1f).scaleY(1f).rotation(0f)
                    .setDuration(460)
                    .setInterpolator(OvershootInterpolator(3.5f))
                    .start()
                playRipple(icon, color)
            }
            .start()
    }

    /** Cancelling: the filled icon sinks a little while it fades back into the outline. */
    private fun playVoteOff(icon: ImageView, outline: Int, color: Int) {
        icon.animate().cancel()
        crossFade(icon, outline, color, CROSS_FADE_MS)
        icon.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(PRESS_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                icon.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Blends the current icon into [target] tinted [color] over [duration]: the view switches to
     * the new icon and fades it in (imageAlpha) while a copy of the old one, tinted as it was,
     * fades out on the view's overlay. The overlay is drawn with the view, so it follows the
     * scale/tilt of the press animation. (Layering both in one LayerDrawable failed: ImageView's
     * tint handling mutates it, which clones the layers and drew them untinted, black.)
     */
    private fun crossFade(icon: ImageView, target: Int, color: Int, duration: Int) {
        (icon.getTag(R.id.miuix_vote_fade) as? ValueAnimator)?.end()
        val oldColor = icon.imageTintList?.defaultColor
        val old = icon.drawable?.constantState?.newDrawable(icon.resources)?.mutate()
        icon.setImageResource(target)
        icon.imageTintList = ColorStateList.valueOf(color)
        if (old == null || oldColor == null || icon.width == 0) return
        old.setTint(oldColor)
        val rect = RectF(0f, 0f, old.intrinsicWidth.toFloat(), old.intrinsicHeight.toFloat())
        icon.imageMatrix.mapRect(rect)
        rect.offset(icon.paddingLeft.toFloat(), icon.paddingTop.toFloat())
        old.setBounds(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
        icon.overlay.add(old)
        icon.imageAlpha = 0
        val fade = ValueAnimator.ofInt(0, 255).apply {
            this.duration = duration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                icon.imageAlpha = value
                old.alpha = 255 - value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    icon.overlay.remove(old)
                    icon.imageAlpha = 255
                    icon.setTag(R.id.miuix_vote_fade, null)
                }
            })
        }
        icon.setTag(R.id.miuix_vote_fade, fade)
        fade.start()
    }

    /**
     * Sets the like count; with [animate] it rolls like an odometer: the old number slides out
     * (up when the count grows, down when it drops) while the new one slides in from the other
     * side, both fading. The two numbers are drawn on the view's overlay (the parent clips them to
     * the count's box) while the view's own text is transparent. Plain binds end a running roll.
     */
    @JvmStatic
    fun setScore(view: TextView, text: String, animate: Boolean) {
        (view.getTag(R.id.miuix_score_roll) as? ValueAnimator)?.end()
        val oldText = view.text.toString()
        if (!animate || oldText == text || oldText.isEmpty() || text.isEmpty() || view.width == 0 || view.height == 0) {
            view.text = text
            return
        }
        val grows = (text.toIntOrNull() ?: 0) >= (oldText.toIntOrNull() ?: 0)
        val colors = view.textColors
        val outgoing = NumberDrawable(view, oldText)
        val incoming = NumberDrawable(view, text)
        view.text = text
        view.setTextColor(Color.TRANSPARENT)
        view.overlay.add(outgoing)
        view.overlay.add(incoming)
        val w = view.width
        val h = view.height
        val out = if (grows) -1 else 1
        fun place(f: Float) {
            val outY = (out * h * f).roundToInt()
            outgoing.setBounds(0, outY, w, outY + h)
            outgoing.alpha = ((1f - f) * 255).roundToInt()
            val inY = (-out * h * (1f - f)).roundToInt()
            incoming.setBounds(0, inY, w, inY + h)
            incoming.alpha = (f * 255).roundToInt()
        }
        place(0f)
        val roll = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ROLL_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator -> place(animator.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.overlay.remove(outgoing)
                    view.overlay.remove(incoming)
                    view.setTextColor(colors)
                    view.setTag(R.id.miuix_score_roll, null)
                }
            })
        }
        view.setTag(R.id.miuix_score_roll, roll)
        roll.start()
    }

    /** One number drawn the way [view] draws its single-line text, offset by the drawable's top. */
    private class NumberDrawable(view: TextView, private val text: String) : Drawable() {
        private val paint = TextPaint(view.paint).apply { color = view.currentTextColor }
        private val baseAlpha = Color.alpha(view.currentTextColor)
        private val x: Float
        private val baseline = view.baseline.toFloat()

        init {
            val textWidth = paint.measureText(text)
            val left = view.compoundPaddingLeft
            val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
            val gravity = Gravity.getAbsoluteGravity(view.gravity, view.layoutDirection)
            x = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.CENTER_HORIZONTAL -> left + (width - textWidth) / 2f
                Gravity.RIGHT -> left + width - textWidth
                else -> left.toFloat()
            }.coerceAtLeast(left.toFloat())
        }

        override fun draw(canvas: Canvas) {
            canvas.drawText(text, x, bounds.top + baseline, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = baseAlpha * alpha / 255
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** A thin ring in [color] that grows from the icon to about twice its size and fades out. */
    private fun playRipple(icon: ImageView, color: Int) {
        val density = icon.resources.displayMetrics.density
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((2f * density).toInt().coerceAtLeast(1), color)
        }
        val inset = (icon.width * 0.2f).toInt()
        ring.setBounds(inset, inset, icon.width - inset, icon.height - inset)
        icon.overlay.add(ring)
        val cx = icon.width / 2f
        val cy = icon.height / 2f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedValue as Float
                val half = (icon.width / 2f - inset) * (1f + 0.8f * f)
                ring.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                ring.alpha = ((1f - animator.animatedFraction) * 220).toInt()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    icon.overlay.remove(ring)
                }
            })
            start()
        }
    }

    @JvmStatic
    fun styleFloorCard(item: View) {
        val density = item.resources.displayMetrics.density
        item.background = GradientDrawable().apply {
            cornerRadius = CARD_RADIUS_DP * density
            setColor(ContextCompat.getColor(item.context, R.color.miuix_surface_container))
        }
        (item.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
            (CARD_MARGIN_H_DP * density).toInt(),
            (CARD_MARGIN_V_DP * density).toInt(),
            (CARD_MARGIN_H_DP * density).toInt(),
            (CARD_MARGIN_V_DP * density).toInt(),
        )
    }

    @JvmStatic
    fun styleFloorList(list: RecyclerView, refresh: SwipeRefreshLayout?, loading: View?) {
        val context = list.context
        val density = list.resources.displayMetrics.density
        list.setBackgroundColor(Color.TRANSPARENT)
        list.updatePadding(top = list.paddingTop + (CARD_MARGIN_V_DP * density).toInt())
        list.clipToPadding = false
        refresh?.setColorSchemeColors(ContextCompat.getColor(context, R.color.miuix_primary))
        refresh?.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.miuix_surface_container))
        loading?.setBackgroundColor(ContextCompat.getColor(context, R.color.miuix_surface))
        clearNavigationBar(list)
    }

    /** Adds the navigation bar height to the bottom padding, once insets are known. */
    private fun clearNavigationBar(list: RecyclerView) {
        val basePadding = list.paddingBottom
        fun apply(insets: WindowInsetsCompat?) {
            val bottom = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: return
            list.updatePadding(bottom = basePadding + bottom)
        }
        ViewCompat.setOnApplyWindowInsetsListener(list) { _, insets ->
            apply(insets)
            insets
        }
        // Views hosted inside Compose may not receive dispatched insets; read the root insets.
        list.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = apply(ViewCompat.getRootWindowInsets(v))
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
