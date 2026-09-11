package sp.phone.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import gov.anzong.androidnga.common.util.NgaImageHost;

public class DefaultSettingsContractTest {

    @Test
    public void preferenceDefaultsMatchStandardSettings() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertDefault(document, "nga_domain", "1");
        assertDefault(document, "pref_image_domain", "0");
        assertDefault(document, "nightmode", "false");
        assertDefault(document, "key_night_mode_follow_system",
                Boolean.toString(Constants.NIGHT_MODE_FOLLOW_SYSTEM_DEFAULT));
        assertDefault(document, "use_solid_color_bg", "true");
        assertDefault(document, "enableNotification", "true");
        assertDefault(document, "notificationSound",
                Boolean.toString(Constants.NOTIFICATION_SOUND_DEFAULT));
        assertDefault(document, "material_theme", Constants.MATERIAL_THEME_DEFAULT);
        assertDefault(document, "sort_by_post", "false");
        assertDefault(document, "filter_sub_board", "false");
        assertDefault(document, "@string/pref_load_pic_strategy", "0");
        assertDefault(document, "@string/pref_load_avatar_strategy", "0");
        assertDefault(document, "showSignature", "false");
        assertDefault(document, "showColortxt", "false");
        assertDefault(document, "refresh_after_post_setting_mode", "true");
    }

    @Test
    public void removedPreferencesAreNotExposed() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertMissingPreference(document, "left_hand");
        assertMissingPreference(document, "bottom_tab");
    }

    @Test
    public void appCompatibilityIsAnIndependentDefaultOffLaboratorySetting() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings_lab.xml"));
        assertDefault(document, "@string/pref_show_with_app_api", "false");
        assertDefault(document, "@string/pref_show_with_webview", "true");
        assertAttribute(document, "@string/pref_show_with_app_api", "android:dependency", "");
        List<String> keys = new ArrayList<>();
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                keys.add(((Element) children.item(i)).getAttribute("android:key"));
            }
        }
        assertEquals(Arrays.asList("@string/pref_show_with_app_api", "@string/pref_show_with_webview",
                "preference_key_ua", "@string/pref_local_debug_switch", "@string/pref_check_in",
                "@string/pref_auto_check_in"), keys);
    }

    /**
     * 自定义图片域名是「图片域名」选择页内的输入框，不是独立设置行。
     *
     * <p>初版把它做成紧邻的 {@code EditTextPreference}，于是它在未选「自定义」时白占一行、还得常灰着，
     * 一个设置需求撑出两行界面。这条断言用来钉住那个方案不要被改回来。
     */
    @Test
    public void customImageDomainIsNotItsOwnSettingsRow() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertMissingPreference(document, "pref_image_domain_custom");
    }

    @Test
    public void imageDomainModesAndDialogOrderStayAligned() throws Exception {
        assertEquals(0, NgaImageHost.MODE_AUTO);
        assertEquals(1, NgaImageHost.MODE_DEFAULT);
        assertEquals(2, NgaImageHost.MODE_IMG9);
        assertEquals(3, NgaImageHost.MODE_CUSTOM);

        Document arrays = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("../lib_base_common/src/main/res/values/arrays.xml"));
        assertEquals(Arrays.asList(
                        "自动", "https://img.nga.cn", "http://img9.nga.cn", "自定义"),
                readStringArray(arrays, "image_domain"));
        assertEquals(Arrays.asList("0", "1", "2", "3"),
                readStringArray(arrays, "image_domain_value"));

        Document layout = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/layout/dialog_image_domain.xml"));
        NodeList radioButtons = layout.getElementsByTagName("RadioButton");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < radioButtons.getLength(); i++) {
            ids.add(((Element) radioButtons.item(i)).getAttribute("android:id"));
        }
        assertEquals(Arrays.asList(
                "@+id/rb_image_domain_auto",
                "@+id/rb_image_domain_default",
                "@+id/rb_image_domain_alt",
                "@+id/rb_image_domain_custom"), ids);
    }

    @Test
    public void settingsAreGroupedByPurpose() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));
        Element preferenceScreen = document.getDocumentElement();

        assertTopLevelSections(preferenceScreen,
                "PreferenceCategory:域名与账号",
                "PreferenceCategory:外观设置",
                "PreferenceCategory:通知设置",
                "PreferenceCategory:其他设置",
                "PreferenceCategory:主题列表设置",
                "PreferenceCategory:主题详情设置",
                "PreferenceCategory:发帖设置",
                "PreferenceScreen:实验室");
        assertCategory(preferenceScreen, "域名与账号",
                "nga_domain", "pref_image_domain", "pref_user_compose");
        assertCategory(preferenceScreen, "外观设置",
                "nightmode", "key_night_mode_follow_system", "use_solid_color_bg",
                "material_theme", "adjust_size");
        assertCategory(preferenceScreen, "通知设置",
                "enableNotification", "notificationSound");
        assertCategory(preferenceScreen, "其他设置",
                "pref_black_list_new", "key_clear_cache", "key_reset_emoticon_order");
        assertCategory(preferenceScreen, "主题列表设置",
                "sort_by_post", "filter_sub_board");
        assertCategory(preferenceScreen, "主题详情设置",
                "@string/pref_load_pic_strategy", "@string/pref_load_avatar_strategy",
                "showSignature");
        assertCategory(preferenceScreen, "发帖设置",
                "showColortxt", "refresh_after_post_setting_mode");
    }

    @Test
    public void regroupedPreferencesRetainBehavioralAttributes() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertAttribute(document, "notificationSound", "android:dependency",
                "enableNotification");
        assertAttribute(document, "pref_user_compose", "android:fragment",
                "com.justwent.androidnga.bu.user.UserManagerFragment");
        assertAttribute(document, "adjust_size", "android:fragment",
                "sp.phone.ui.fragment.SettingsSizeFragment");
        assertAttribute(document, "pref_black_list_new", "android:fragment",
                "gov.anzong.androidnga.activity.compose.filter.FilterWordFragment");
    }

    @Test
    public void sizeDefaultsMatchStandardSettings() {
        assertEquals(20, Constants.TOPIC_TITLE_SIZE_DEFAULT);
        assertEquals(100, Constants.AVATAR_SIZE_DEFAULT);
        assertEquals(60, Constants.EMOTICON_SIZE_DEFAULT);
        assertEquals(80, Constants.WEBVIEW_DEFAULT_TEXT_ZOOM);
    }

    @Test
    public void runtimeFallbacksMatchPreferenceDefaults() {
        assertFalse(Constants.NOTIFICATION_SOUND_DEFAULT);
        assertEquals("2", Constants.MATERIAL_THEME_DEFAULT);
        assertTrue(Constants.NIGHT_MODE_FOLLOW_SYSTEM_DEFAULT);
    }

    private static void assertDefault(Document document, String key, String expected) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                assertEquals(expected, element.getAttribute("android:defaultValue"));
                return;
            }
        }
        throw new AssertionError("Missing preference key: " + key);
    }

    private static List<String> readStringArray(Document document, String name) {
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (!name.equals(array.getAttribute("name"))) {
                continue;
            }
            List<String> items = new ArrayList<>();
            NodeList children = array.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "item".equals(child.getNodeName())) {
                    items.add(child.getTextContent().trim());
                }
            }
            return items;
        }
        throw new AssertionError("Missing string-array: " + name);
    }

    private static void assertMissingPreference(Document document, String key) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                throw new AssertionError("Unexpected preference key: " + key);
            }
        }
    }

    private static void assertAttribute(
            Document document, String key, String attribute, String expected) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                assertEquals(expected, element.getAttribute(attribute));
                return;
            }
        }
        throw new AssertionError("Missing preference key: " + key);
    }

    private static void assertTopLevelSections(Element preferenceScreen, String... expected) {
        List<String> actual = new ArrayList<>();
        NodeList children = preferenceScreen.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                actual.add(element.getTagName() + ":" + element.getAttribute("android:title"));
            }
        }
        assertEquals(Arrays.asList(expected), actual);
    }

    private static void assertCategory(
            Element preferenceScreen, String title, String... expectedKeys) {
        NodeList children = preferenceScreen.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element category = (Element) child;
            if (!"PreferenceCategory".equals(category.getTagName())
                    || !title.equals(category.getAttribute("android:title"))) {
                continue;
            }

            List<String> actualKeys = new ArrayList<>();
            NodeList preferences = category.getChildNodes();
            for (int j = 0; j < preferences.getLength(); j++) {
                Node preference = preferences.item(j);
                if (preference.getNodeType() == Node.ELEMENT_NODE) {
                    actualKeys.add(((Element) preference).getAttribute("android:key"));
                }
            }
            assertEquals(Arrays.asList(expectedKeys), actualKeys);
            return;
        }
        throw new AssertionError("Missing preference category: " + title);
    }
}
