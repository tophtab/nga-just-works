package com.justwen.androidnga.ui.compose.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScaffoldAppSystemBarContractTest {
    private val source = File(
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "lib_base_ui_compose").isDirectory },
        "lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/ScaffoldApp.kt",
    ).readText()

    @Test
    fun composeScreensPaintTheNavigationBarWithTheThemeSurface() {
        assertTrue(source.contains("setNavigationBarColor(MaterialTheme.colors.background"))
        assertTrue(source.contains("MaterialTheme.colors.isLight"))
    }
}
