package sp.phone.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilderFactory;

import gov.anzong.androidnga.R;

public class LoadingTipCatalogTest {

    @Test
    public void optionalCapabilityAddsOnlyItsOwnInstruction() {
        List<Integer> base = LoadingTipCatalog.eligibleTips(false);
        List<Integer> withAi = LoadingTipCatalog.eligibleTips(true);

        assertEquals(8, base.size());
        assertEquals(base.size(), new HashSet<>(base).size());
        assertFalse(base.contains(0));
        assertFalse(base.contains(R.string.loading_tip_ai_settings));
        assertEquals(base.size() + 1, withAi.size());
        assertTrue(withAi.containsAll(base));
        assertTrue(withAi.contains(R.string.loading_tip_ai_settings));
        assertThrows(UnsupportedOperationException.class, () -> base.add(123));
        assertThrows(UnsupportedOperationException.class, () -> withAi.clear());
    }

    @Test
    public void aRealSettingsEntryAndItsBundledFragmentEnableAi() throws Exception {
        Document settings = preferences(
                "<PreferenceScreen android:key=\"pref_ai_settings\" "
                        + "android:fragment=\"sp.phone.ui.fragment.SettingsAiFragment\"/>");
        AtomicInteger destinationLookups = new AtomicInteger();

        boolean available = hasAiEntry(settings, name -> {
            destinationLookups.incrementAndGet();
            return "sp.phone.ui.fragment.SettingsAiFragment".equals(name);
        });
        assertTrue(available);
        assertEquals(1, destinationLookups.get());
        assertTrue(LoadingTipCatalog.eligibleTips(available).contains(R.string.loading_tip_ai_settings));
    }

    @Test
    public void aPreferenceWithAMissingDestinationDoesNotAdvertiseAi() throws Exception {
        Document settings = preferences(
                "<PreferenceScreen android:key=\"pref_ai_settings\" "
                        + "android:fragment=\"sp.phone.ui.fragment.SettingsAiFragment\"/>");

        boolean available = hasAiEntry(settings, name -> false);
        assertFalse(available);
        assertFalse(LoadingTipCatalog.eligibleTips(available).contains(R.string.loading_tip_ai_settings));
    }

    @Test
    public void entryAndDestinationMustBelongToTheSamePreference() throws Exception {
        Document settings = preferences(
                "<PreferenceScreen android:key=\"pref_ai_settings\" "
                        + "android:fragment=\"sp.phone.ui.fragment.SettingsLabFragment\"/>"
                        + "<PreferenceScreen android:key=\"another_feature\" "
                        + "android:fragment=\"sp.phone.ui.fragment.SettingsAiFragment\"/>");
        AtomicInteger destinationLookups = new AtomicInteger();

        assertFalse(hasAiEntry(settings, name -> {
            destinationLookups.incrementAndGet();
            return true;
        }));
        assertEquals(0, destinationLookups.get());
    }

    @Test
    public void copyOrClassPresenceWithoutTheSettingsEntryIsInsufficient() throws Exception {
        Document settings = preferences(
                "<Preference android:title=\"@string/loading_tip_ai_settings\"/>"
                        + "<PreferenceScreen android:fragment=\"sp.phone.ui.fragment.SettingsAiFragment\"/>");

        assertFalse(hasAiEntry(settings, name -> true));
    }

    private static Document preferences(String entries) throws Exception {
        String xml = "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                + entries + "</PreferenceScreen>";
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    private static boolean hasAiEntry(Document settings, Predicate<String> isBundledFragment) {
        NodeList entries = settings.getElementsByTagName("*");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (LoadingTipCatalog.isAiSettingsEntry(entry.getAttribute("android:key"),
                    entry.getAttribute("android:fragment"), isBundledFragment)) {
                return true;
            }
        }
        return false;
    }
}
