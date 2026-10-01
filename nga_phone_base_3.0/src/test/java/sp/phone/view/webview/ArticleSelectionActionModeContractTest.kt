package sp.phone.view.webview

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Narrow static guard against implicit article-text export to external apps. */
class ArticleSelectionActionModeContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val callbackSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionActionModeCallback.java")

    @Test
    fun noProcessTextEntryPointIsReintroduced() {
        assertFalse(callbackSource.contains("ACTION_PROCESS_TEXT"))
        assertFalse(callbackSource.contains("ACTION_SEND"))
        assertFalse(callbackSource.contains("createChooser"))
    }
}
