package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.ui.fragment.BasePreferenceFragment;
import okhttp3.Call;
import sp.phone.ai.AiConfig;
import sp.phone.ai.AiConfigStore;
import sp.phone.ai.AiError;
import sp.phone.ai.AiSummaryClient;

/** Keeps the legacy preference navigation while the AI store owns all persistence. */
public class SettingsAiFragment extends BasePreferenceFragment {

    private static final String KEY_ENDPOINT = "ai_settings_endpoint";
    private static final String KEY_API_KEY = "ai_settings_api_key";
    private static final String KEY_MODEL = "ai_settings_model";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private AiConfigStore mConfigStore;
    private AiSummaryClient mClient;
    private Preference mStatusPreference;
    private Preference mEndpointPreference;
    private Preference mKeyPreference;
    private Preference mModelPreference;
    private Preference mTestPreference;
    private AlertDialog mDialog;
    private Call mTestCall;
    private long mTestGeneration;

    private String mEndpoint = "";
    private String mModel = "";
    // Only the unsaved replacement is held in memory. Never put it in a Preference or Bundle.
    private String mPendingApiKey = "";
    private boolean mHasSavedConfig;
    private boolean mHasChanges;

    public static void open(@NonNull Context context) {
        Intent intent = new Intent(context, LauncherSubActivity.class);
        intent.putExtra("fragment", SettingsAiFragment.class.getName());
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.settings_ai);
        mapping(getPreferenceScreen());
        mConfigStore = new AiConfigStore(requireContext());
        mClient = new AiSummaryClient();
        mStatusPreference = findPreference("ai_settings_status");
        mEndpointPreference = findPreference(KEY_ENDPOINT);
        mKeyPreference = findPreference(KEY_API_KEY);
        mModelPreference = findPreference(KEY_MODEL);
        mTestPreference = findPreference("ai_settings_test");

        mEndpointPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mKeyPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mModelPreference.setOnPreferenceClickListener(this::showFieldEditor);
        findPreference("ai_settings_save").setOnPreferenceClickListener(preference -> {
            saveConfiguration();
            return true;
        });
        mTestPreference.setOnPreferenceClickListener(preference -> {
            testConnection();
            return true;
        });
        findPreference("ai_settings_clear").setOnPreferenceClickListener(preference -> {
            confirmClearConfiguration();
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadConfiguration();
    }

    private void loadConfiguration() {
        mEndpoint = "";
        mModel = "";
        mPendingApiKey = "";
        mHasSavedConfig = false;
        mHasChanges = false;
        try {
            AiConfig config = mConfigStore.load();
            mHasSavedConfig = config != null;
            if (config != null) {
                mEndpoint = config.getEndpoint();
                mModel = config.getModel();
            }
            renderConfiguration();
        } catch (AiConfigStore.StorageException error) {
            renderConfiguration();
            mStatusPreference.setSummary(error.getMessage());
        }
    }

    private void renderConfiguration() {
        mEndpointPreference.setSummary(TextUtils.isEmpty(mEndpoint)
                ? getString(R.string.ai_settings_endpoint_hint) : mEndpoint);
        mModelPreference.setSummary(TextUtils.isEmpty(mModel)
                ? getString(R.string.ai_settings_model_hint) : mModel);
        mKeyPreference.setSummary(!mPendingApiKey.isEmpty() ? R.string.ai_settings_key_pending
                : mHasSavedConfig ? R.string.ai_settings_key_saved : R.string.ai_settings_key_empty);
        mStatusPreference.setSummary(mHasChanges ? R.string.ai_settings_unsaved
                : mHasSavedConfig ? R.string.ai_settings_configured : R.string.ai_settings_not_configured);
    }

    private boolean showFieldEditor(Preference preference) {
        invalidateConnectionTest();
        dismissEditor();
        String key = preference.getKey();
        boolean secret = KEY_API_KEY.equals(key);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        AppCompatEditText input = new AppCompatEditText(builder.getContext());
        input.setSingleLine(true);
        input.setSaveEnabled(false);
        input.setSaveFromParentEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.editor_text_color));
        input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.editor_hint_color));
        input.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.editor_background));
        input.setMinHeight(dp(48));

        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTransformationMethod(PasswordTransformationMethod.getInstance());
            input.setHint(mHasSavedConfig || !mPendingApiKey.isEmpty()
                    ? R.string.ai_settings_key_keep_hint : R.string.ai_settings_key_hint);
            builder.setMessage(R.string.ai_settings_key_help);
        } else if (KEY_ENDPOINT.equals(key)) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setText(mEndpoint);
            input.setHint(R.string.ai_settings_endpoint_hint);
            builder.setMessage(R.string.ai_settings_endpoint_help);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setText(mModel);
            input.setHint(R.string.ai_settings_model_hint);
        }
        input.setSelection(input.length());

        FrameLayout container = new FrameLayout(builder.getContext());
        container.setPadding(dp(24), dp(8), dp(24), dp(8));
        container.setSaveEnabled(false);
        container.setSaveFromParentEnabled(false);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = builder.setTitle(preference.getTitle())
                .setView(container)
                .setPositiveButton(android.R.string.ok, (ignored, which) -> {
                    updateField(key, input.getText() == null ? "" : input.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            input.setText("");
            if (mDialog == dialog) {
                mDialog = null;
            }
        });
        mDialog = dialog;
        dialog.show();
        if (secret && dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateField(String key, String value) {
        invalidateConnectionTest();
        if (KEY_API_KEY.equals(key)) {
            // An empty editor retains the current Key, including an unsaved replacement.
            if (!value.trim().isEmpty()) {
                mPendingApiKey = value;
                mHasChanges = true;
            }
        } else if (KEY_ENDPOINT.equals(key)) {
            mHasChanges |= !mEndpoint.equals(value);
            mEndpoint = value;
        } else if (KEY_MODEL.equals(key)) {
            mHasChanges |= !mModel.equals(value);
            mModel = value;
        }
        renderConfiguration();
    }

    private AiConfig currentConfiguration() throws AiConfigStore.StorageException {
        String apiKey = mPendingApiKey;
        if (apiKey.isEmpty()) {
            AiConfig savedConfig = mConfigStore.load();
            if (savedConfig != null) {
                apiKey = savedConfig.getApiKey();
            }
        }
        return new AiConfig(mEndpoint, apiKey, mModel);
    }

    private void saveConfiguration() {
        invalidateConnectionTest();
        try {
            AiConfig config = currentConfiguration();
            mConfigStore.save(config);
            mEndpoint = config.getEndpoint();
            mModel = config.getModel();
            mPendingApiKey = "";
            mHasSavedConfig = true;
            mHasChanges = false;
            renderConfiguration();
            ToastUtils.success(R.string.ai_settings_saved);
        } catch (AiConfigStore.StorageException | IllegalArgumentException error) {
            mStatusPreference.setSummary(error.getMessage());
            ToastUtils.error(error.getMessage());
        }
    }

    private void testConnection() {
        invalidateConnectionTest();
        final AiConfig config;
        try {
            config = currentConfiguration();
        } catch (AiConfigStore.StorageException | IllegalArgumentException error) {
            mTestPreference.setSummary(error.getMessage());
            ToastUtils.error(error.getMessage());
            return;
        }
        final long generation = mTestGeneration;
        mTestPreference.setEnabled(false);
        mTestPreference.setSummary(R.string.ai_settings_testing);
        try {
            mTestCall = mClient.testConnection(config, new AiSummaryClient.Callback() {
                @Override
                public void onSuccess(String text) {
                    // Connection tests never display the service's raw response.
                    mMainHandler.post(() -> finishConnectionTest(generation, null));
                }

                @Override
                public void onError(AiError error) {
                    mMainHandler.post(() -> finishConnectionTest(generation, error.getMessage()));
                }
            });
        } catch (RuntimeException ignored) {
            finishConnectionTest(generation, getString(R.string.ai_settings_test_failed));
        }
    }

    private void finishConnectionTest(long generation, @Nullable String errorMessage) {
        if (generation != mTestGeneration || !isAdded() || !isResumed()) {
            return;
        }
        mTestCall = null;
        mTestPreference.setEnabled(true);
        if (errorMessage != null) {
            mTestPreference.setSummary(errorMessage);
            ToastUtils.error(errorMessage);
        } else {
            int message = mHasChanges || !mHasSavedConfig
                    ? R.string.ai_settings_test_success_unsaved : R.string.ai_settings_test_success;
            mTestPreference.setSummary(message);
            ToastUtils.success(message);
        }
    }

    private void invalidateConnectionTest() {
        mTestGeneration++;
        if (mTestCall != null) {
            mTestCall.cancel();
            mTestCall = null;
        }
        if (mTestPreference != null) {
            mTestPreference.setEnabled(true);
            mTestPreference.setSummary(R.string.ai_settings_test_summary);
        }
    }

    private void confirmClearConfiguration() {
        invalidateConnectionTest();
        dismissEditor();
        mDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ai_settings_clear_title)
                .setMessage(R.string.ai_settings_clear_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> clearConfiguration())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mDialog.show();
    }

    private void clearConfiguration() {
        invalidateConnectionTest();
        try {
            mConfigStore.clear();
            mPendingApiKey = "";
            mEndpoint = "";
            mModel = "";
            mHasSavedConfig = false;
            mHasChanges = false;
            renderConfiguration();
            ToastUtils.success(R.string.ai_settings_cleared);
        } catch (AiConfigStore.StorageException error) {
            mStatusPreference.setSummary(error.getMessage());
            ToastUtils.error(error.getMessage());
        }
    }

    private void dismissEditor() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.ai_settings_title));
    }

    @Override
    public void onPause() {
        invalidateConnectionTest();
        dismissEditor();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        invalidateConnectionTest();
        dismissEditor();
        mPendingApiKey = "";
        mMainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
