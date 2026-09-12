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
                "ai_settings_model", "ai_settings_profile_prompt", "ai_settings_test"}) {
            assertNull("AI details must not be expanded on the root page", findPreference(rootSettings, key));
            assertNotNull("Missing AI setting: " + key, findPreference(aiSettings, key));
        }
        assertEquals(5, aiSettings.getElementsByTagName("Preference").getLength());
        for (String removed : new String[]{"ai_settings_privacy", "ai_settings_status",
                "ai_settings_save", "ai_settings_clear"}) {
            assertNull("Removed AI row: " + removed, findPreference(aiSettings, removed));
        }
    }

    @Test
    public void saveIsAnAccessibleToolbarActionUsingTheExistingIcon() throws Exception {
        Document menu = readXml("menu/settings_ai_option_menu.xml");
        NodeList items = menu.getElementsByTagName("item");
        assertEquals(1, items.getLength());
        Element save = (Element) items.item(0);
        assertEquals("@+id/menu_ai_settings_save", save.getAttribute("android:id"));
        assertEquals("@drawable/btn_ic_save", save.getAttribute("android:icon"));
        assertEquals("@string/ai_settings_save_title", save.getAttribute("android:title"));
        assertEquals("always", save.getAttribute("app:showAsAction"));

        String source = readSource("sp/phone/ui/fragment/SettingsAiFragment.java");
        assertTrue(source.contains("setHasOptionsMenu(true)"));
        assertTrue(source.contains("inflater.inflate(R.menu.settings_ai_option_menu, menu)"));
        assertTrue(source.contains("item.getItemId() == R.id.menu_ai_settings_save"));
        assertTrue(source.contains("new AiConfig(mEndpoint, currentApiKey(), mModel, mProfilePromptEditorState.getPrompt())"));
        assertTrue(source.contains("mConfigStore.save(config)"));
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
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_SECURE"));
        assertTrue(source.contains("input.setText(\"\")"));
        assertTrue(source.contains("if (!value.trim().isEmpty())"));
        assertFalse(source.contains("builder.setMessage("));
    }

    @Test
    public void discoveryUsesTheDraftServiceAndDoesNotRewriteTheTextEditor() throws Exception {
        String source = readSource("sp/phone/ui/fragment/SettingsAiFragment.java");
        String modelEditor = source.substring(source.indexOf("private void showModelEditor()"),
                source.indexOf("private void renderModelEditor("));
        assertTrue(modelEditor.contains("mClient.listModels(mEndpoint, currentApiKey(),"));
        assertFalse(modelEditor.contains("currentConfiguration()"));
        assertFalse(modelEditor.contains("mConfigStore.save("));
        assertTrue(modelEditor.contains("mMainHandler.post("));
        assertTrue(modelEditor.contains("ListView.CHOICE_MODE_SINGLE"));

        String rendering = source.substring(source.indexOf("private void renderModelEditor("),
                source.indexOf("private void finishModelDiscovery("));
        assertFalse("Discovery must preserve the input's text and cursor", rendering.contains("input.setText("));
        assertFalse("Discovery must preserve the input's text and cursor", rendering.contains("input.setSelection("));

        String completion = source.substring(source.indexOf("private void finishModelDiscovery("),
                source.indexOf("private void invalidateModelDiscovery()"));
        assertTrue(completion.contains("mDialog != dialog || !isAdded() || !isResumed()"));
        assertTrue(completion.contains("mModelEditorState.modelsLoaded(generation, models)"));
        assertTrue(source.contains("mModelsCall.cancel()"));
    }

    @Test
    public void profilePromptRowOpensACancellableMultilineEditorAndSavesThroughTheToolbar() throws Exception {
        Element preference = findPreference(readXml("xml/settings_ai.xml"), "ai_settings_profile_prompt");
        assertNotNull(preference);
        assertEquals("Preference", preference.getTagName());
        assertEquals("false", preference.getAttribute("android:persistent"));
        assertEquals("@string/ai_settings_profile_prompt_title", preference.getAttribute("android:title"));
        assertEquals("@string/ai_settings_profile_prompt_roast", preference.getAttribute("android:summary"));
        Document strings = readXml("values/strings_ai_settings.xml");
        assertEquals("查成分提示词", stringValue(strings, "ai_settings_profile_prompt_title"));
        assertEquals("论坛锐评风格", stringValue(strings, "ai_settings_profile_prompt_roast"));
        assertEquals("详细分析风格", stringValue(strings, "ai_settings_profile_prompt_detailed"));
        assertEquals("自定义", stringValue(strings, "ai_settings_profile_prompt_custom"));

        String source = readSource("sp/phone/ui/fragment/SettingsAiFragment.java");
        assertTrue(source.contains("mProfilePromptPreference.setOnPreferenceClickListener(this::showFieldEditor)"));
        assertTrue(source.contains("mProfilePromptEditorState.reset(config.getProfilePrompt())"));
        assertTrue(source.contains("mProfilePromptPreference.setSummary(profilePromptTitle(mProfilePromptEditorState.getPrompt().getStyle()))"));
        String editor = source.substring(source.indexOf("private void showProfilePromptEditor()"),
                source.indexOf("private void showModelEditor()"));
        assertTrue(editor.contains("AiProfilePrompt.Style.values()"));
        assertTrue(editor.contains("TYPE_TEXT_FLAG_MULTI_LINE"));
        assertTrue(editor.contains("input.setSingleLine(false)"));
        assertTrue(editor.contains("mProfilePromptEditorState.isActive(generation)"));
        assertTrue(editor.contains("mProfilePromptEditorState.confirm()"));
        assertTrue(editor.contains("setNegativeButton(android.R.string.cancel, null)"));
        assertTrue(editor.contains("dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener"));
        assertTrue(editor.contains("input.setError(error.getMessage())"));
        assertFalse(editor.contains("mConfigStore.save("));
        assertFalse(editor.contains("mClient."));
        assertFalse(editor.contains("LengthFilter"));
    }

    private static String stringValue(Document document, String name) {
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element string = (Element) strings.item(i);
            if (name.equals(string.getAttribute("name"))) {
                return string.getTextContent();
            }
        }
        throw new AssertionError("Missing string: " + name);
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
