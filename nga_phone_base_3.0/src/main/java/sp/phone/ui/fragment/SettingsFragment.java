package sp.phone.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.bumptech.glide.Glide;

import org.apache.commons.io.FileUtils;

import java.io.IOException;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.activity.SettingsActivity;
import gov.anzong.androidnga.activity.compose.TemplateComposeActivity;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.ui.TabletSplit;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.EmoticonOrderStore;
import gov.anzong.androidnga.ui.fragment.BasePreferenceFragment;
import gov.anzong.androidnga.ui.miuix.MiuixPreferencesKt;
import sp.phone.common.UserManagerImpl;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.fragment.dialog.AlertDialogFragment;
import sp.phone.ui.fragment.dialog.ImageDomainDialogFragment;

public class SettingsFragment extends BasePreferenceFragment
        implements Preference.OnPreferenceChangeListener,
        ImageDomainDialogFragment.OnImageDomainSavedListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        mapping(getPreferenceScreen());
        configPreference();
    }

    private void configPreference() {
        findPreference(PreferenceKey.NIGHT_MODE).setEnabled(!ThemeManager.getInstance().isNightModeFollowSystem());
        findPreference(PreferenceKey.MATERIAL_THEME).setEnabled(!ThemeManager.getInstance().isNightMode());
        // 经典外观的配色方案在 Miuix 外观下不起作用。
        findPreference(PreferenceKey.MATERIAL_THEME).setVisible(!ThemeManager.getInstance().isMiuixStyle());
        // 图片域名的选择页带输入框，Miuix 外观下也沿用它，而不是普通下拉。
        findPreference(PreferenceKey.KEY_IMAGE_DOMAIN).getExtras().putBoolean(MiuixPreferencesKt.EXTRA_CUSTOM_DIALOG, true);
        updateTabletSplitVisibility();

        findPreference(PreferenceKey.KEY_CLEAR_CACHE).setOnPreferenceClickListener(preference -> {
            showClearCacheDialog();
            return true;
        });

        findPreference(PreferenceKey.KEY_RESET_EMOTICON_ORDER).setOnPreferenceClickListener(preference -> {
            showResetEmoticonOrderDialog();
            return true;
        });

    }

    /**
     * 「平板分栏」只在能真正分栏的设备上出现；手机上隐藏，避免打开后触发系统的分栏过渡。
     */
    private void updateTabletSplitVisibility() {
        Preference preference = findPreference(PreferenceKey.KEY_TABLET_SPLIT);
        if (preference != null) {
            preference.setVisible(TabletSplit.isTabletDevice(requireContext()));
        }
    }

    private void showResetEmoticonOrderDialog() {
        AlertDialogFragment dialogFragment = AlertDialogFragment.create("确认要重置所有表情的自定义顺序吗？");
        dialogFragment.setPositiveClickListener((dialog, which) -> resetEmoticonOrder());
        dialogFragment.show(((BaseActivity) getActivity()).getSupportFragmentManager(), "reset_emoticon_order");
    }

    private void resetEmoticonOrder() {
        EmoticonOrderStore.resetAll();
        ToastUtils.success("表情顺序已重置");
    }

    private void showClearCacheDialog() {
        AlertDialogFragment dialogFragment = AlertDialogFragment.create("确认要清除缓存吗？");
        dialogFragment.setPositiveClickListener((dialog, which) -> clearCache());
        dialogFragment.show(((BaseActivity)getActivity()).getSupportFragmentManager(),"clear_cache");
    }

    private void clearCache() {
        ThreadUtils.postOnSubThread(() -> {
            // 清除glide缓存
            Glide.get(ContextUtils.getContext()).clearDiskCache();
            // 清除avatar数据
            UserManagerImpl.getInstance().clearAvatarUrl();
            // 清除之前的使用过的awp缓存数据
            try {
                FileUtils.deleteDirectory(ContextUtils.getContext().getDir("awp", Context.MODE_PRIVATE));
                FileUtils.deleteDirectory(ContextUtils.getContext().getDir("sogou_webview", Context.MODE_PRIVATE));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        ToastUtils.success("缓存清除成功");
    }

    /**
     * 「图片域名」用自绘的选择页，而不是 {@code ListPreference} 默认的纯单选对话框——
     * 「自定义」那一项需要在同一页里带上域名输入框，否则输入框只能另占一行设置项。
     */
    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (PreferenceKey.KEY_IMAGE_DOMAIN.equals(preference.getKey())) {
            // 用 childFragmentManager，好让对话框能通过 getParentFragment() 找回本 fragment 回调。
            new ImageDomainDialogFragment().show(getChildFragmentManager());
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    /**
     * 选择页直写了 SharedPreferences，此处把 {@code ListPreference} 的内存值拉回一致，
     * 顺带触发 summary 重新回显选中项。
     */
    @Override
    public void onImageDomainSaved() {
        Preference preference = findPreference(PreferenceKey.KEY_IMAGE_DOMAIN);
        if (preference instanceof ListPreference) {
            ((ListPreference) preference).setValue(
                    PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0"));
        }
    }

    @Override
    public void onResume() {
        getActivity().setTitle(R.string.menu_setting);
        super.onResume();
        // 折叠屏开合会改变设备尺寸，回到设置页时重新决定「平板分栏」是否出现。
        updateTabletSplitVisibility();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference instanceof ListPreference) {
            preference.setSummary(((ListPreference) preference).getEntry());
        }

        String key = preference.getKey();
        switch (key) {
            case PreferenceKey.NIGHT_MODE:
                SettingsActivity.sRecreated = true;
                // The split divider takes the page background; refresh it for the new mode.
                ThreadUtils.postOnMainThreadDelay(() -> TabletSplit.apply(ContextUtils.getContext()), 50);
                break;
            case PreferenceKey.KEY_NIGHT_MODE_FOLLOW_SYSTEM:
                findPreference(PreferenceKey.NIGHT_MODE).setEnabled(Boolean.FALSE.equals(newValue));
                SettingsActivity.sRecreated = true;
                ThreadUtils.postOnMainThreadDelay(() -> TabletSplit.apply(ContextUtils.getContext()), 50);
                break;
            case PreferenceKey.KEY_TABLET_SPLIT:
                // The value is stored after this returns; apply it once it is.
                ThreadUtils.postOnMainThreadDelay(() -> TabletSplit.apply(ContextUtils.getContext()), 50);
                break;
            case PreferenceKey.KEY_UI_STYLE:
            case PreferenceKey.MATERIAL_THEME:
                SettingsActivity.sRecreated = true;
                ThreadUtils.postOnMainThreadDelay(() -> TabletSplit.apply(ContextUtils.getContext()), 50);
                ThreadUtils.postOnMainThreadDelay(() -> {
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                }, 200);
                break;
            default:
                break;

        }
        return true;
    }

    private void setFullScreen(boolean fullScreen) {
        int flag;
        if (fullScreen) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        Intent intent;
        if (preference.getFragment() != null && (preference.getFragment().contains("compose") || preference.getKey() != null && preference.getKey().contains("compose"))) {
            intent = new Intent(getActivity(), TemplateComposeActivity.class);
            intent.putExtra("fragment", preference.getFragment());
            startActivity(intent);
            return true;
        } else if (preference.getFragment() != null) {
            intent = new Intent(getActivity(), LauncherSubActivity.class);
            intent.putExtra("fragment", preference.getFragment());
            startActivity(intent);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

}
