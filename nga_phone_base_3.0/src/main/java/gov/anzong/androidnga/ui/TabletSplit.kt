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
 *
 * The rules are only handed to the system when the current configuration could actually split.
 * Registering them on a phone makes the system start the embedding transition before it rejects
 * the rule, which shows as panes sliding in and then flashing back to full screen.
 */
object TabletSplit {

    private const val MIN_WIDTH_DP = 840
    private const val MIN_SMALLEST_WIDTH_DP = 600
    private const val SPLIT_RATIO = 0.5f

    /** The last rules state given to the system, so repeated calls stay side-effect free. */
    private var lastAppliedSignature: String? = null

    /** Registers the rules when supported and enabled; clears them when turned off. Idempotent. */
    @JvmStatic
    fun apply(context: Context) = apply(context, context.applicationContext.resources.configuration)

    /**
     * Whether a window with these dimensions can split. These are the three launch-time bounds
     * the split rule evaluates: the 840dp width, the 600dp short side, and the portrait aspect
     * ratio, which the rule disallows — and which the library treats as `height >= width`, so a
     * square window is portrait too.
     */
    internal fun canSplit(screenWidthDp: Int, screenHeightDp: Int): Boolean =
        screenWidthDp > screenHeightDp &&
            screenWidthDp >= MIN_WIDTH_DP &&
            minOf(screenWidthDp, screenHeightDp) >= MIN_SMALLEST_WIDTH_DP

    /**
     * Whether a smallest screen width belongs to a tablet: the `sw600dp` resource-qualifier
     * threshold, the platform convention for tablet-class devices.
     */
    internal fun isTabletSmallestWidth(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp >= MIN_SMALLEST_WIDTH_DP

    /** Whether the device itself is tablet-sized (the sw600dp resource qualifier). */
    @JvmStatic
    fun isTabletDevice(context: Context): Boolean =
        isTabletSmallestWidth(
            context.applicationContext.resources.configuration.smallestScreenWidthDp,
        )

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
        val active = isEnabled() &&
            canSplit(configuration.screenWidthDp, configuration.screenHeightDp)
        val color = if (active) pageBackground(app, configuration) else 0
        val signature = if (active) "on:$color" else "off"
        if (signature == lastAppliedSignature) {
            return
        }
        lastAppliedSignature = signature
        val rules = RuleController.getInstance(app)
        if (!active) {
            rules.clearRules()
            return
        }
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
