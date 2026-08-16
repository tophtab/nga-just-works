package gov.anzong.androidnga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemThemeContractTest {
    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String): String = File(projectRoot, relativePath).readText()

    @Test
    fun legacyNavigationBarIsConfiguredForBothThemesAndComposeActivities() {
        val javaSource = source(
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/BaseActivity.java"
        )
        assertTrue(javaSource.contains("getWindow().setNavigationBarColor(backgroundColor)"))
        assertTrue(javaSource.contains("setNavigationBarContrastEnforced(false)"))
        assertTrue(javaSource.contains("setAppearanceLightNavigationBars(!ThemeManager.getInstance().isNightMode())"))
        assertFalse(javaSource.contains("isNightMode() && !mComposeEnabled"))
        assertTrue(javaSource.contains("getWindow().getDecorView().setBackgroundColor(backgroundColor)"))
        assertTrue(javaSource.contains("WindowInsetsCompat.Type.navigationBars()"))
        assertTrue(javaSource.contains("initialPaddingBottom + navaBars.bottom"))

        val kotlinSource = source("lib_base_ui/src/main/java/com/justwen/androidnga/ui/BaseActivity.kt")
        assertTrue(kotlinSource.contains("window.navigationBarColor = backgroundColor"))
        assertTrue(kotlinSource.contains("window.isNavigationBarContrastEnforced = false"))
        assertTrue(kotlinSource.contains("window.decorView.setBackgroundColor(backgroundColor)"))
        assertTrue(kotlinSource.contains("controller.isAppearanceLightNavigationBars"))
    }

    @Test
    fun filterContentUsesMaterialTwoAndSizeSlidersUseThemeTextColor() {
        val filterSource = source(
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterWordFragment.kt"
        )
        assertTrue(filterSource.contains("import androidx.compose.material.Icon"))
        assertTrue(filterSource.contains("import androidx.compose.material.Text"))
        assertFalse(filterSource.contains("import androidx.compose.material3.Icon"))
        assertFalse(filterSource.contains("import androidx.compose.material3.Text"))
        assertTrue(Regex("color = Color\\.Gray").findAll(filterSource).count() == 2)

        val sizeLayout = source("nga_phone_base_3.0/src/main/res/layout/fragment_settings_size.xml")
        assertTrue(Regex("app:ssb_second_track_color=\"@color/text_color\"").findAll(sizeLayout).count() == 4)
        assertTrue(Regex("app:ssb_track_color=\"@color/text_color_disabled\"").findAll(sizeLayout).count() == 4)

        val kotlinSource = source("lib_base_ui/src/main/java/com/justwen/androidnga/ui/BaseActivity.kt")
        assertTrue(kotlinSource.contains("val initialPaddingBottom = contentView.paddingBottom"))
        assertTrue(kotlinSource.contains("initialPaddingBottom + navaBars.bottom"))
    }

    @Test
    fun remainingComposeAndLegacyEditorsUseThemeAwareContentColors() {
        val drawerSource = source(
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt"
        )
        assertFalse(drawerSource.contains("color = Color.DarkGray"))
        assertFalse(drawerSource.contains("tint = Color.DarkGray"))
        assertTrue(drawerSource.contains("MaterialTheme.colors.onBackground"))

        val messageSource = source(
            "lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/detail/MessageDetailActivity.kt"
        )
        assertFalse(messageSource.contains("0xFF294563"))
        assertTrue(messageSource.contains("MaterialTheme.colors.onSurface"))

        val searchSource = source(
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/SearchActivity.kt"
        )
        assertFalse(searchSource.contains("Color.White"))
        assertFalse(searchSource.contains("Color.Gray"))
        assertTrue(searchSource.contains("MaterialTheme.colors.surface"))
        assertTrue(searchSource.contains("TextStyle.Default.copy(color = fieldContent)"))

        val avatarLayout = source("nga_phone_base_3.0/src/main/res/layout/activity_change_avatar.xml")
        val signLayout = source("nga_phone_base_3.0/src/main/res/layout/activity_change_sign_reply.xml")
        assertTrue(avatarLayout.contains("@color/editor_background"))
        assertTrue(avatarLayout.contains("@color/editor_text_color"))
        assertTrue(signLayout.contains("@color/editor_background"))
        assertTrue(signLayout.contains("@color/editor_text_color"))

        val lightColors = source("nga_phone_base_3.0/src/main/res/values/colors.xml")
        val nightColors = source("nga_phone_base_3.0/src/main/res/values-night/colors.xml")
        assertTrue(lightColors.contains("<color name=\"editor_background\">"))
        assertTrue(nightColors.contains("<color name=\"editor_background\">"))
        assertTrue(nightColors.contains("<color name=\"editor_text_color\">"))
    }
}
