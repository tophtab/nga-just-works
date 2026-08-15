package gov.anzong.androidnga.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaseActivityNavigationBarContractTest {
    private val source = File(
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "nga_phone_base_3.0").isDirectory },
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/BaseActivity.java",
    ).readText()

    @Test
    fun navigationBarColorIsNotLimitedToNightModeOrLegacyViews() {
        assertTrue(source.contains("getWindow().setNavigationBarColor(backgroundColor)"))
        assertFalse(source.contains("isNightMode() && !mComposeEnabled"))
        assertTrue(source.contains("setNavigationBarContrastEnforced(false)"))
        assertTrue(source.contains("setAppearanceLightNavigationBars(!ThemeManager.getInstance().isNightMode())"))
    }

    @Test
    fun edgeToEdgeReappliesNavigationInsets() {
        assertTrue(source.contains("WindowInsetsCompat.Type.navigationBars()"))
        assertTrue(source.contains("contentView.setPadding(0, 0, 0, navaBars.bottom)"))
        assertTrue(source.contains("getWindow().getDecorView().setBackgroundColor(backgroundColor)"))
    }
}
