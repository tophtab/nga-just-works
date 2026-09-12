package sp.phone.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import gov.anzong.androidnga.R;

final class LoadingTipCatalog {

    private static final List<Integer> BASE_TIPS = Collections.unmodifiableList(Arrays.asList(
            R.string.loading_tip_thread_page_gestures,
            R.string.loading_tip_bottom_button_refresh,
            R.string.loading_tip_long_press_reorder));

    private LoadingTipCatalog() {
    }

    static List<Integer> eligibleTips(boolean aiSettingsAvailable) {
        if (!aiSettingsAvailable) {
            return BASE_TIPS;
        }
        List<Integer> tips = new ArrayList<>(BASE_TIPS);
        tips.add(R.string.loading_tip_ai_feature);
        return Collections.unmodifiableList(tips);
    }

    static boolean isAiSettingsEntry(String preferenceKey, String fragmentName,
                                     Predicate<String> isBundledFragment) {
        return "pref_ai_settings".equals(preferenceKey)
                && "sp.phone.ui.fragment.SettingsAiFragment".equals(fragmentName)
                && isBundledFragment.test(fragmentName);
    }
}
