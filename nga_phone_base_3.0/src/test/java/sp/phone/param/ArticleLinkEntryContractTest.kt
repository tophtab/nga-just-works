package sp.phone.param

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Device-free wiring checks; actual chooser behavior remains a platform/device scenario. */
class ArticleLinkEntryContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    @Test fun schemeHasItsOwnBrowsableFilter() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "nga_phone_base_3.0/src/main/AndroidManifest.xml"))
        val activities = xml.getElementsByTagName("activity")
        val activity = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttribute("android:name") == ".activity.ArticleListActivity" }
        val filters = activity.getElementsByTagName("intent-filter")
        val custom = (0 until filters.length).map { filters.item(it) as Element }.single { filter ->
            val data = filter.getElementsByTagName("data")
            (0 until data.length).any { (data.item(it) as Element).getAttribute("android:scheme") == "nga" }
        }
        val data = custom.getElementsByTagName("data")
        assertEquals(1, data.length)
        assertEquals("", (data.item(0) as Element).getAttribute("android:host"))
        val categories = custom.getElementsByTagName("category")
        assertTrue((0 until categories.length).any {
            (categories.item(it) as Element).getAttribute("android:name") == "android.intent.category.BROWSABLE"
        })
        assertTrue(filters.length >= 2)
    }
}
