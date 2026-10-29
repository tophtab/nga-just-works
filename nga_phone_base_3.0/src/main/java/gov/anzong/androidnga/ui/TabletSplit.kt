package gov.anzong.androidnga.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.window.WindowSdkExtensions
import androidx.window.embedding.DividerAttributes
import androidx.window.embedding.EmbeddingAspectRatio
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitRule
import gov.anzong.androidnga.R
import gov.anzong.androidnga.activity.ArticleListActivity
import gov.anzong.androidnga.activity.LauncherSubActivity
import gov.anzong.androidnga.activity.MainActivity
import gov.anzong.androidnga.activity.PostActivity
import gov.anzong.androidnga.activity.ProfileActivity
import gov.anzong.androidnga.activity.RecentNotificationActivity
import gov.anzong.androidnga.activity.TopicListActivity
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.common.PreferenceKey
import sp.phone.theme.ThemeManager

/**
 * Tablet two-pane layout with Jetpack Activity Embedding (the mechanism HyperOS 平行窗口 uses):
 * opening the next level shows it on the right and keeps its parent on the left —
 * home | board, board (or search / favourites / notifications / history) | thread,
 * thread | reply or author profile, board | new topic. The system draws the divider; from
 * WindowManager extensions 6 it is draggable to resize both panes.
 *
 * Splits only on landscape tablets (window at least 840dp wide, smallest width 600dp, never in
 * portrait), so phones and portrait tablets keep the stacked pages. The manifest opts out of the
 * system's own reverse adaptation (PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE=false), so
 * these rules are the only ones.
 */
object TabletSplit {

    private const val MIN_WIDTH_DP = 840
    private const val MIN_SMALLEST_WIDTH_DP = 600
    private const val SPLIT_RATIO = 0.5f

    /** Registers the rules when supported and enabled; clears them when turned off. Idempotent. */
    @JvmStatic
    fun apply(context: Context) = apply(context, context.applicationContext.resources.configuration)

    /**
     * As [apply], with the configuration to take the day/night state from. From
     * Application.onConfigurationChanged pass the new one: the application resources may still
     * hold the old configuration at that point.
     */
    @JvmStatic
    fun apply(context: Context, configuration: Configuration) {
        val app = context.applicationContext
        if (SplitController.getInstance(app).splitSupportStatus != SplitController.SplitSupportStatus.SPLIT_AVAILABLE) {
            return
        }
        val rules = RuleController.getInstance(app)
        if (!isEnabled()) {
            rules.clearRules()
            return
        }
        val color = pageBackground(app, configuration)
        val attributes = splitAttributes(color)
        rules.setRules(
            setOf(
                // home | board (also search results and favourites, which are topic lists)
                pair(app, MainActivity::class.java, TopicListActivity::class.java, attributes),
                // topic list | thread, and the other lists that open threads: notifications and
                // the sub-page lists hosted by LauncherSubActivity (browsing history ...)
                pair(app, TopicListActivity::class.java, ArticleListActivity::class.java, attributes),
                pair(app, RecentNotificationActivity::class.java, ArticleListActivity::class.java, attributes),
                pair(app, LauncherSubActivity::class.java, ArticleListActivity::class.java, attributes),
                // thread | reply, thread | author profile, topic list | new topic
                pair(app, ArticleListActivity::class.java, PostActivity::class.java, attributes),
                pair(app, ArticleListActivity::class.java, ProfileActivity::class.java, attributes),
                pair(app, TopicListActivity::class.java, PostActivity::class.java, attributes),
            ),
        )
    }

    @JvmStatic
    fun isEnabled(): Boolean = PreferenceUtils.getData(PreferenceKey.KEY_TABLET_SPLIT, true)

    // Every extension-level API below is guarded by the extensionVersion check right above it;
    // lint's RequiresWindowSdk check does not recognise these guards (androidx disables it too).
    @SuppressLint("RequiresWindowSdk")
    private fun splitAttributes(color: Int): SplitAttributes {
        val builder = SplitAttributes.Builder()
            .setSplitType(SplitAttributes.SplitType.ratio(SPLIT_RATIO))
            .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
        val extension = WindowSdkExtensions.getInstance().extensionVersion
        if (extension >= 6) {
            // The system draggable divider in the page background colour, so only its drag handle
            // in the middle stands out and the two panes read as one surface. (HyperOS leaves the
            // divider band black for a moment while a pane opens or closes.)
            val divider = DividerAttributes.DraggableDividerAttributes.Builder()
                .setColor(color)
                .setWidthDp(DividerAttributes.WIDTH_SYSTEM_DEFAULT)
                .setDragRange(DividerAttributes.DragRange.DRAG_RANGE_SYSTEM_DEFAULT)
            if (extension >= 7) divider.setDraggingToFullscreenAllowed(true)
            builder.setDividerAttributes(divider.build())
        }
        return builder.build()
    }

    /**
     * The page background the panes use: Miuix surface in the Miuix appearance, the classic
     * background otherwise, in the app's own day/night choice (it can differ from the system's).
     */
    private fun pageBackground(context: Context, configuration: Configuration): Int {
        val theme = ThemeManager.getInstance()
        val systemNight = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val night = if (theme.isNightModeFollowSystem()) systemNight else theme.isNightMode()
        val config = Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = context.createConfigurationContext(config)
        return ContextCompat.getColor(themed, if (theme.isMiuixStyle()) R.color.miuix_surface else R.color.background_color)
    }

    private fun pair(
        context: Context,
        primary: Class<*>,
        secondary: Class<*>,
        attributes: SplitAttributes,
    ): SplitPairRule {
        val filter = SplitPairFilter(ComponentName(context, primary), ComponentName(context, secondary), null)
        return SplitPairRule.Builder(setOf(filter))
            .setDefaultSplitAttributes(attributes)
            .setMinWidthDp(MIN_WIDTH_DP)
            .setMinSmallestWidthDp(MIN_SMALLEST_WIDTH_DP)
            .setMaxAspectRatioInPortrait(EmbeddingAspectRatio.ALWAYS_DISALLOW)
            .setMaxAspectRatioInLandscape(EmbeddingAspectRatio.ALWAYS_ALLOW)
            // Opening another thread replaces the right pane instead of stacking a new one.
            .setClearTop(true)
            // Back on the right pane closes only it; closing the left pane closes the right too.
            .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
            .build()
    }
}
