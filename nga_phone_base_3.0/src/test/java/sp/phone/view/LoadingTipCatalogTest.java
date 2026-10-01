package sp.phone.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import gov.anzong.androidnga.R;

public class LoadingTipCatalogTest {

    @Test
    public void optionalCapabilityControlsAiInstructionMembership() {
        assertFalse(LoadingTipCatalog.eligibleTips(false).contains(R.string.loading_tip_ai_feature));
        assertTrue(LoadingTipCatalog.eligibleTips(true).contains(R.string.loading_tip_ai_feature));
    }

    @Test
    public void aRealSettingsEntryAndItsBundledFragmentEnableAi() {
        assertTrue(LoadingTipCatalog.isAiSettingsEntry("pref_ai_settings",
                "sp.phone.ui.fragment.SettingsAiFragment",
                name -> "sp.phone.ui.fragment.SettingsAiFragment".equals(name)));
    }

    @Test
    public void aPreferenceWithAMissingDestinationDoesNotAdvertiseAi() {
        assertFalse(LoadingTipCatalog.isAiSettingsEntry("pref_ai_settings",
                "sp.phone.ui.fragment.SettingsAiFragment", name -> false));
    }

    @Test
    public void entryAndDestinationMustBelongToTheSamePreference() {
        assertFalse(LoadingTipCatalog.isAiSettingsEntry("pref_ai_settings",
                "sp.phone.ui.fragment.SettingsLabFragment", name -> true));
        assertFalse(LoadingTipCatalog.isAiSettingsEntry("another_feature",
                "sp.phone.ui.fragment.SettingsAiFragment", name -> true));
    }
}
