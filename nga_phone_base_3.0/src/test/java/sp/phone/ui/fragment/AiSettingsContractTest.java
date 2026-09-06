package sp.phone.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

/** Host-side contracts for the legacy Android preference/navigation boundary. */
public class AiSettingsContractTest {

    @Test
    public void aiConfigurationLivesOnlyOnTheSecondLevelScreen() throws Exception {
        Document rootSettings = readXml("xml/settings.xml");
        Element entry = findPreference(rootSettings, "pref_ai_settings");
        assertNotNull(entry);
        assertSame(rootSettings.getDocumentElement(), entry.getParentNode());
        assertEquals("PreferenceScreen", entry.getTagName());
        assertEquals("sp.phone.ui.fragment.SettingsAiFragment", entry.getAttribute("android:fragment"));
        assertEquals(0, entry.getElementsByTagName("*").getLength());

        Document aiSettings = readXml("xml/settings_ai.xml");
        for (String key : new String[]{"ai_settings_endpoint", "ai_settings_api_key",
                "ai_settings_model", "ai_settings_save", "ai_settings_test", "ai_settings_clear"}) {
            assertNull("AI details must not be expanded on the root page", findPreference(rootSettings, key));
            assertNotNull("Missing AI setting: " + key, findPreference(aiSettings, key));
        }

        Element disclosure = findPreference(aiSettings, "ai_settings_privacy");
        assertNotNull(disclosure);
        assertEquals("false", disclosure.getAttribute("android:selectable"));
        assertEquals("@string/ai_settings_privacy", disclosure.getAttribute("android:summary"));
    }

    @Test
    public void aiSettingsUsesTheExistingChildActivityRouteAndTitle() throws Exception {
        String source = readSource("sp/phone/ui/fragment/SettingsAiFragment.java");
        String settings = readSource("sp/phone/ui/fragment/SettingsFragment.java");
        String launcher = readSource("gov/anzong/androidnga/activity/LauncherSubActivity.java");

        assertTrue(source.contains("extends BasePreferenceFragment"));
        assertTrue(source.contains("new Intent(context, LauncherSubActivity.class)"));
        assertTrue(source.contains("putExtra(\"fragment\", SettingsAiFragment.class.getName())"));
        assertTrue(settings.contains("intent.putExtra(\"fragment\", preference.getFragment())"));
        assertTrue(launcher.contains("intent.getStringExtra(\"fragment\")"));
        assertTrue(launcher.contains("fragment instanceof BasePreferenceFragment"));
        assertTrue(source.contains("setTitle(getString(R.string.ai_settings_title))"));
        assertFalse("Back must retain the existing child Activity behavior", source.contains("onBackPressed"));
    }

    @Test
    public void apiKeyBypassesAutomaticPreferenceAndViewPersistence() throws Exception {
        Document aiSettings = readXml("xml/settings_ai.xml");
        Element apiKey = findPreference(aiSettings, "ai_settings_api_key");
        assertNotNull(apiKey);
        // Even a nonpersistent EditTextPreference saves its text to a Bundle.
        assertEquals("Preference", apiKey.getTagName());
        assertEquals("false", apiKey.getAttribute("android:persistent"));
        assertFalse(apiKey.hasAttribute("android:defaultValue"));

        String source = readSource("sp/phone/ui/fragment/SettingsAiFragment.java");
        assertFalse(source.contains("import androidx.preference.EditTextPreference"));
        assertFalse(source.contains("SharedPreferences"));
        assertFalse(source.contains("PreferenceUtils"));
        assertFalse(source.contains("putString("));
        assertFalse(source.contains("setText(mPendingApiKey"));
        assertFalse(source.contains("setText(config.getApiKey()"));
        assertTrue(source.contains("input.setSaveEnabled(false)"));
        assertTrue(source.contains("input.setSaveFromParentEnabled(false)"));
        assertTrue(source.contains("TYPE_TEXT_VARIATION_PASSWORD"));
        assertTrue(source.contains("PasswordTransformationMethod.getInstance()"));
        assertTrue(source.contains("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS"));
        assertTrue(source.contains("IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS"));
        assertTrue(source.contains("IME_FLAG_NO_PERSONALIZED_LEARNING"));
    }

    private static Document readXml(String path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Paths.get("src/main/res", path).toFile());
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java", path)), StandardCharsets.UTF_8);
    }

    private static Element findPreference(Document document, String key) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                return element;
            }
        }
        return null;
    }
}
