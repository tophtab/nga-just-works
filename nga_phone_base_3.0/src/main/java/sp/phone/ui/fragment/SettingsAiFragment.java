package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.ui.fragment.BasePreferenceFragment;
import okhttp3.Call;
import sp.phone.ai.AiConfig;
import sp.phone.ai.AiConfigStore;
import sp.phone.ai.AiError;
import sp.phone.ai.AiProfilePrompt;
import sp.phone.ai.AiSummaryClient;

/** Keeps the legacy preference navigation while the AI store owns all persistence. */
public class SettingsAiFragment extends BasePreferenceFragment {

    private static final String KEY_ENDPOINT = "ai_settings_endpoint";
    private static final String KEY_API_KEY = "ai_settings_api_key";
    private static final String KEY_MODEL = "ai_settings_model";
    private static final String KEY_PROFILE_PROMPT = "ai_settings_profile_prompt";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final AiModelEditorState mModelEditorState = new AiModelEditorState();
    private final AiProfilePromptEditorState mProfilePromptEditorState = new AiProfilePromptEditorState();

    private AiConfigStore mConfigStore;
    private AiSummaryClient mClient;
    private Preference mEndpointPreference;
    private Preference mKeyPreference;
    private Preference mModelPreference;
    private Preference mProfilePromptPreference;
    private Preference mTestPreference;
    private AlertDialog mDialog;
    private Call mTestCall;
    private Call mModelsCall;
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
        setHasOptionsMenu(true);
        addPreferencesFromResource(R.xml.settings_ai);
        mapping(getPreferenceScreen());
        mConfigStore = new AiConfigStore(requireContext());
        mClient = new AiSummaryClient();
        mEndpointPreference = findPreference(KEY_ENDPOINT);
        mKeyPreference = findPreference(KEY_API_KEY);
        mModelPreference = findPreference(KEY_MODEL);
        mProfilePromptPreference = findPreference(KEY_PROFILE_PROMPT);
        mTestPreference = findPreference("ai_settings_test");

        mEndpointPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mKeyPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mModelPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mProfilePromptPreference.setOnPreferenceClickListener(this::showFieldEditor);
        mTestPreference.setOnPreferenceClickListener(preference -> {
            testConnection();
            return true;
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.settings_ai_option_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_ai_settings_save) {
            saveConfiguration();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        mModelEditorState.clearModels();
        mProfilePromptEditorState.reset(AiProfilePrompt.DEFAULT);
        try {
            AiConfig config = mConfigStore.load();
            mHasSavedConfig = config != null;
            if (config != null) {
                mEndpoint = config.getEndpoint();
                mModel = config.getModel();
                mProfilePromptEditorState.reset(config.getProfilePrompt());
            }
            renderConfiguration();
        } catch (AiConfigStore.StorageException error) {
            renderConfiguration();
            ToastUtils.error(error.getMessage());
        }
    }

    private void renderConfiguration() {
        mEndpointPreference.setSummary(TextUtils.isEmpty(mEndpoint)
                ? getString(R.string.ai_settings_endpoint_hint) : mEndpoint);
        mModelPreference.setSummary(TextUtils.isEmpty(mModel)
                ? getString(R.string.ai_settings_model_hint) : mModel);
        mProfilePromptPreference.setSummary(profilePromptTitle(mProfilePromptEditorState.getPrompt().getStyle()));
        mKeyPreference.setSummary(!mPendingApiKey.isEmpty() ? R.string.ai_settings_key_pending
                : mHasSavedConfig ? R.string.ai_settings_key_saved : R.string.ai_settings_key_empty);
    }

    private boolean showFieldEditor(Preference preference) {
        invalidateConnectionTest();
        dismissEditor();
        String key = preference.getKey();
        if (KEY_MODEL.equals(key)) {
            showModelEditor();
            return true;
        }
        if (KEY_PROFILE_PROMPT.equals(key)) {
            showProfilePromptEditor();
            return true;
        }
        boolean secret = KEY_API_KEY.equals(key);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        AppCompatEditText input = createInput(builder.getContext());

        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTransformationMethod(PasswordTransformationMethod.getInstance());
            input.setHint(mHasSavedConfig || !mPendingApiKey.isEmpty()
                    ? R.string.ai_settings_key_keep_hint : R.string.ai_settings_key_hint);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setText(mEndpoint);
            input.setHint(R.string.ai_settings_endpoint_hint);
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

    private AppCompatEditText createInput(Context context) {
        AppCompatEditText input = new AppCompatEditText(context);
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
        return input;
    }

    private static int profilePromptTitle(AiProfilePrompt.Style style) {
        switch (style) {
            case FORUM_ROAST:
                return R.string.ai_settings_profile_prompt_roast;
            case DETAILED:
                return R.string.ai_settings_profile_prompt_detailed;
            case CUSTOM:
                return R.string.ai_settings_profile_prompt_custom;
            default:
                throw new IllegalStateException("Invalid profile prompt style");
        }
    }

    private void showProfilePromptEditor() {
        final long generation = mProfilePromptEditorState.open();
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        Context context = builder.getContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setSaveEnabled(false);
        scroll.setSaveFromParentEnabled(false);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(8));
        container.setSaveEnabled(false);
        container.setSaveFromParentEnabled(false);
        scroll.addView(container);

        RadioGroup choices = new RadioGroup(context);
        choices.setSaveEnabled(false);
        container.addView(choices, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AppCompatEditText input = createInput(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setVerticalScrollBarEnabled(true);
        input.setHint(R.string.ai_settings_profile_prompt_hint);
        input.setText(mProfilePromptEditorState.getCustomText());
        input.setSelection(input.length());
        input.setVisibility(mProfilePromptEditorState.getSelectedStyle() == AiProfilePrompt.Style.CUSTOM
                ? View.VISIBLE : View.GONE);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (AiProfilePrompt.Style style : AiProfilePrompt.Style.values()) {
            AppCompatRadioButton choice = new AppCompatRadioButton(context);
            choice.setId(View.generateViewId());
            choice.setText(profilePromptTitle(style));
            choice.setMinHeight(dp(48));
            choice.setSaveEnabled(false);
            choices.addView(choice);
            choice.setChecked(style == mProfilePromptEditorState.getSelectedStyle());
            choice.setOnCheckedChangeListener((button, checked) -> {
                if (!checked || !mProfilePromptEditorState.isActive(generation)) {
                    return;
                }
                mProfilePromptEditorState.selectStyle(style);
                boolean custom = style == AiProfilePrompt.Style.CUSTOM;
                input.setVisibility(custom ? View.VISIBLE : View.GONE);
                InputMethodManager keyboard = (InputMethodManager) context.getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (custom) {
                    input.requestFocus();
                    if (keyboard != null) {
                        keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                } else if (keyboard != null) {
                    keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                }
            });
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable text) {
                if (mProfilePromptEditorState.isActive(generation)) {
                    mProfilePromptEditorState.setCustomText(text.toString());
                }
            }
        });

        AlertDialog dialog = builder.setTitle(R.string.ai_settings_profile_prompt_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (mDialog == dialog) {
                mProfilePromptEditorState.close();
                mDialog = null;
            }
            input.setText("");
        });
        mDialog = dialog;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if (!mProfilePromptEditorState.isActive(generation)) {
                return;
            }
            try {
                AiProfilePrompt previous = mProfilePromptEditorState.getPrompt();
                AiProfilePrompt updated = mProfilePromptEditorState.confirm();
                mHasChanges |= !previous.equals(updated);
                renderConfiguration();
                dialog.dismiss();
            } catch (IllegalArgumentException error) {
                input.setError(error.getMessage());
                ToastUtils.error(error.getMessage());
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private void showModelEditor() {
        final long generation = mModelEditorState.open(mModel);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        Context context = builder.getContext();
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(8));
        container.setSaveEnabled(false);
        container.setSaveFromParentEnabled(false);

        TextView status = new AppCompatTextView(context);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, 0, 0, dp(8));
        container.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView choices = new ListView(context);
        choices.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        choices.setSaveEnabled(false);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_single_choice);
        choices.setAdapter(adapter);
        // The list scrolls within a bounded viewport and yields space to the input/keyboard.
        container.addView(choices, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 1f));

        AppCompatEditText input = createInput(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.ai_settings_model_hint);
        input.setText(mModelEditorState.getCustomModel());
        input.setSelection(input.length());
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = builder.setTitle(R.string.ai_settings_model_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (ignored, which) ->
                        updateField(KEY_MODEL, mModelEditorState.getModel()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Runnable render = () -> renderModelEditor(choices, adapter, input, status);
        choices.setOnItemClickListener((parent, view, position, id) -> {
            if (!mModelEditorState.isActive(generation)) {
                return;
            }
            boolean custom = position == adapter.getCount() - 1;
            if (custom) {
                mModelEditorState.selectCustom();
            } else {
                mModelEditorState.selectModel(adapter.getItem(position));
            }
            input.setVisibility(custom ? View.VISIBLE : View.GONE);
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (custom) {
                input.requestFocus();
                if (keyboard != null) {
                    keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            } else if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable text) {
                if (mModelEditorState.isActive(generation)) {
                    mModelEditorState.setCustomModel(text.toString());
                }
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (mDialog == dialog) {
                invalidateModelDiscovery();
                mDialog = null;
            }
            input.setText("");
        });
        mDialog = dialog;
        render.run();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        try {
            mModelsCall = mClient.listModels(mEndpoint, currentApiKey(), new AiSummaryClient.ModelsCallback() {
                @Override
                public void onSuccess(List<String> models) {
                    mMainHandler.post(() -> finishModelDiscovery(generation, dialog, models, render));
                }

                @Override
                public void onError(AiError error) {
                    mMainHandler.post(() -> finishModelDiscovery(generation, dialog, null, render));
                }
            });
        } catch (AiConfigStore.StorageException | RuntimeException ignored) {
            finishModelDiscovery(generation, dialog, null, render);
        }
    }

    private void renderModelEditor(ListView choices, ArrayAdapter<String> adapter,
                                   AppCompatEditText input, TextView status) {
        List<String> models = mModelEditorState.getModels();
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(models);
        adapter.add(getString(R.string.ai_settings_model_custom));
        adapter.notifyDataSetChanged();
        choices.clearChoices();
        choices.setItemChecked(mModelEditorState.isCustom()
                ? models.size() : models.indexOf(mModelEditorState.getModel()), true);
        ViewGroup.LayoutParams params = choices.getLayoutParams();
        params.height = Math.min(dp(48) * adapter.getCount(), Math.min(dp(240),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.3f)));
        choices.setLayoutParams(params);
        // Never setText here: an arriving response must preserve both text and cursor selection.
        input.setVisibility(mModelEditorState.isCustom() ? View.VISIBLE : View.GONE);
        status.setVisibility(View.VISIBLE);
        switch (mModelEditorState.getLoadStatus()) {
            case LOADING:
                status.setText(R.string.ai_settings_models_loading);
                break;
            case EMPTY:
                status.setText(R.string.ai_settings_models_empty);
                break;
            case FAILED:
                status.setText(R.string.ai_settings_models_failed);
                break;
            case READY:
                status.setVisibility(View.GONE);
                break;
        }
    }

    private void finishModelDiscovery(long generation, AlertDialog dialog,
                                      @Nullable List<String> models, Runnable render) {
        if (mDialog != dialog || !isAdded() || !isResumed()) {
            return;
        }
        boolean accepted = models == null ? mModelEditorState.loadFailed(generation)
                : mModelEditorState.modelsLoaded(generation, models);
        if (accepted) {
            mModelsCall = null;
            render.run();
        }
    }

    private void invalidateModelDiscovery() {
        mModelEditorState.close();
        if (mModelsCall != null) {
            mModelsCall.cancel();
            mModelsCall = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateField(String key, String value) {
        invalidateConnectionTest();
        invalidateModelDiscovery();
        if (KEY_API_KEY.equals(key)) {
            // An empty editor retains the current Key, including an unsaved replacement.
            if (!value.trim().isEmpty()) {
                if (!mPendingApiKey.equals(value)) {
                    mModelEditorState.clearModels();
                }
                mPendingApiKey = value;
                mHasChanges = true;
            }
        } else if (KEY_ENDPOINT.equals(key)) {
            if (!mEndpoint.equals(value)) {
                mModelEditorState.clearModels();
            }
            mHasChanges |= !mEndpoint.equals(value);
            mEndpoint = value;
        } else if (KEY_MODEL.equals(key)) {
            mHasChanges |= !mModel.equals(value);
            mModel = value;
        }
        renderConfiguration();
    }

    private AiConfig currentConfiguration() throws AiConfigStore.StorageException {
        return new AiConfig(mEndpoint, currentApiKey(), mModel, mProfilePromptEditorState.getPrompt());
    }

    private String currentApiKey() throws AiConfigStore.StorageException {
        String apiKey = mPendingApiKey;
        if (apiKey.isEmpty()) {
            AiConfig savedConfig = mConfigStore.load();
            if (savedConfig != null) {
                apiKey = savedConfig.getApiKey();
            }
        }
        return apiKey;
    }

    private void saveConfiguration() {
        invalidateConnectionTest();
        dismissEditor();
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
            ToastUtils.error(error.getMessage());
        }
    }

    private void testConnection() {
        invalidateConnectionTest();
        dismissEditor();
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

    private void dismissEditor() {
        invalidateModelDiscovery();
        mProfilePromptEditorState.close();
        if (mDialog != null) {
            AlertDialog dialog = mDialog;
            mDialog = null;
            dialog.dismiss();
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
        mModelEditorState.clearModels();
        mProfilePromptEditorState.reset(AiProfilePrompt.DEFAULT);
        mMainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
