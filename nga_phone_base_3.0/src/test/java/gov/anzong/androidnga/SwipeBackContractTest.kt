package gov.anzong.androidnga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Swipe back was removed upstream because it duplicated the system edge back gesture. It is
 * restored for navigation button devices only, and deliberately without a user facing switch,
 * so the app keeps a single interaction model per navigation mode. These contracts lock that
 * shape in place.
 */
class SwipeBackContractTest {
    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String): String = File(projectRoot, relativePath).readText()

    private val deviceUtils = "lib_base_common/src/main/java/gov/anzong/androidnga/base/util/DeviceUtils.kt"
    private val swipeBackHelper = "lib_base_common/src/main/java/gov/anzong/androidnga/base/common/SwipeBackHelper.java"
    private val baseActivity = "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/BaseActivity.java"
    private val mainActivity = "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/MainActivity.java"

    @Test
    fun navigationModeDetectionCoversAospAndMiui() {
        val utilsSource = source(deviceUtils)
        assertTrue(utilsSource.contains("fun hasNavigationButtons(context: Context): Boolean"))
        assertTrue(utilsSource.contains("\"navigation_mode\""))
        // MIUI does not write the AOSP key, so checking navigation_mode alone reports its
        // full screen gesture devices as three button ones.
        assertTrue(utilsSource.contains("\"force_fsg_nav_bar\""))
        assertTrue(utilsSource.contains("mode != NAVIGATION_MODE_GESTURE"))
        assertTrue(utilsSource.contains("NAVIGATION_MODE_GESTURE = 2"))
    }

    @Test
    fun swipeBackIsWiredOnlyForNavigationButtonDevices() {
        val helperSource = source(swipeBackHelper)
        assertTrue(helperSource.contains("EDGE_SIZE_DP = 10"))
        assertTrue(helperSource.contains("setEdgeTrackingEnabled(SwipeBackLayout.EDGE_ALL)"))

        val activitySource = source(baseActivity)
        assertTrue(activitySource.contains("DeviceUtils.hasNavigationButtons(this)"))
        assertTrue(activitySource.contains("mSwipeBackHelper.onPostCreate()"))
        assertTrue(activitySource.contains("mSwipeBackHelper.findViewById(id)"))

        // The home screen already uses a horizontal swipe for the navigation drawer.
        assertTrue(source(mainActivity).contains("setSwipeBackEnable(false)"))

        assertTrue(source("lib_base_common/build.gradle").contains("me.imid.swipebacklayout.lib"))
    }

    @Test
    fun swipeBackHasNoUserFacingToggle() {
        val preferenceKeys = source("lib_base_common/src/main/java/gov/anzong/androidnga/common/PreferenceKey.java")
        assertFalse(preferenceKeys.contains("KEY_SWIPE_BACK"))
        assertFalse(source(baseActivity).contains("KEY_SWIPE_BACK"))
        assertFalse(source("nga_phone_base_3.0/src/main/res/xml/settings.xml").contains("swipe_back"))
        assertFalse(File(projectRoot, "nga_phone_base_3.0/src/main/res/xml-v28/settings.xml").exists())
    }

    @Test
    fun swipeBackRevealsTheActivityUnderneath() {
        val helperSource = source(swipeBackHelper)
        // The library reveals the page below by reflecting on the hidden
        // Activity#convertToTranslucent, blocked since Android 9 and swallowed by its own
        // try/catch. Without the public replacement the drag exposes a black gap.
        assertTrue(helperSource.contains("addSwipeListener(new TranslucentOnEdgeTouch(activity))"))
        assertTrue(helperSource.contains("mActivity.setTranslucent(true)"))
        // setTranslucent is API 30; minSdk is 29.
        assertTrue(helperSource.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.R"))
    }

    @Test
    fun swipeBackKeepsTheThemedBackgroundColor() {
        val activitySource = source(baseActivity)
        // SwipeBackLayout needs an empty decor background, so the decor paint is skipped while
        // swipe back is active. The literal below is also asserted by SystemThemeContractTest.
        assertTrue(activitySource.contains("if (mSwipeBackHelper == null)"))
        assertTrue(activitySource.contains("getWindow().getDecorView().setBackgroundColor(backgroundColor)"))
        // Falling back to the theme windowBackground would repaint the content root #202020 at
        // night instead of background_color (#080C10).
        assertTrue(
            activitySource.contains(
                "mSwipeBackHelper.setContentBackgroundColor(ContextUtils.getColor(R.color.background_color))"
            )
        )
        assertTrue(source(swipeBackHelper).contains("public void setContentBackgroundColor(@ColorInt int color)"))
    }
}
