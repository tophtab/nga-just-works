package sp.phone.ui.fragment.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import java.util.function.Supplier;

import gov.anzong.androidnga.R;
import sp.phone.ai.AiConfigStore;
import sp.phone.ai.AiError;
import sp.phone.ai.AiSummaryClient;
import sp.phone.ai.summary.AiSummarySources;
import sp.phone.ai.summary.FloorSummaryInput;
import sp.phone.ai.summary.SummaryController;
import sp.phone.ui.fragment.SettingsAiFragment;

/**
 * One transient, scrollable result surface shared by floor and profile menus. Its source page
 * owns this object and dismisses it on pause; plaintext input is never saved into a Bundle.
 */
public final class AiSummaryDialog {

    private final AlertDialog dialog;
    private final SummaryController controller;
    private final SummaryController.InputSource input;
    private final String target;
    private final TextView content;
    private final TextView status;
    private final TextView error;
    private final TextView reasoning;
    private final Button reasoningToggle;
    private final ProgressBar progress;
    private boolean reasoningExpanded;

    private AiSummaryDialog(Context context, String title, String target,
                            SummaryController.InputSource input, Supplier<String> currentTarget) {
        this.target = target;
        this.input = input;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppTheme_AiSummaryDialog);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_ai_summary, null);
        content = view.findViewById(R.id.ai_summary_content);
        status = view.findViewById(R.id.ai_summary_status);
        error = view.findViewById(R.id.ai_summary_error);
        reasoning = view.findViewById(R.id.ai_summary_reasoning);
        reasoningToggle = view.findViewById(R.id.ai_summary_reasoning_toggle);
        progress = view.findViewById(R.id.ai_summary_progress);
        ScrollView scroll = view.findViewById(R.id.ai_summary_scroll);
        scroll.getLayoutParams().height = (int) (context.getResources()
                .getDisplayMetrics().heightPixels * 0.45f);
        dialog = builder
                .setTitle(title)
                .setView(view)
                .setNeutralButton(R.string.ai_summary_close, (ignored, which) -> dismiss())
                .setNegativeButton(R.string.ai_summary_retry, null)
                .setPositiveButton(R.string.ai_summary_copy, null)
                .create();
        AiConfigStore configs = new AiConfigStore(context.getApplicationContext());
        AiSummaryClient client = new AiSummaryClient();
        Handler handler = new Handler(Looper.getMainLooper());
        controller = new SummaryController(configs::load, (config, prompt, callback) -> {
            okhttp3.Call call = client.summarize(config, prompt, new AiSummaryClient.Callback() {
                @Override
                public void onProgress(String answer, String reasoning) {
                    callback.onProgress(answer, reasoning);
                }

                @Override
                public void onSuccess(String text) {
                    callback.onSuccess(text);
                }

                @Override
                public void onError(AiError error) {
                    callback.onError(error.getMessage());
                }
            });
            return call::cancel;
        }, handler::post, () -> dialog.isShowing() ? currentTarget.get() : null,
                new SummaryController.Listener() {
                    @Override
                    public void onState(SummaryController.State state) {
                        render(state);
                    }

                    @Override
                    public void onConfigurationRequired() {
                        dismiss();
                        Toast.makeText(context, R.string.ai_summary_configure_first, Toast.LENGTH_SHORT).show();
                        SettingsAiFragment.open(context);
                    }
                });
        reasoningToggle.setOnClickListener(button -> setReasoningExpanded(!reasoningExpanded));
        dialog.setOnDismissListener(ignored -> {
            controller.cancel();
            setReasoningExpanded(false);
            render(controller.getState());
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(button -> retry());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> copy(context));
            retry();
        });
    }

    public static AiSummaryDialog showFloor(Context context, FloorSummaryInput input,
                                            Supplier<String> currentTarget) {
        String title = input.hasFloor()
                ? context.getString(R.string.ai_summary_floor_title, input.getFloor())
                : context.getString(R.string.ai_summary_action);
        AiSummaryDialog result = new AiSummaryDialog(context, title,
                input.getTarget(), AiSummarySources.floor(input), currentTarget);
        result.dialog.show();
        return result;
    }

    public static AiSummaryDialog showProfile(Context context, String uid, String userName,
                                              Supplier<String> currentTarget) {
        AiSummaryDialog result = new AiSummaryDialog(context,
                context.getString(R.string.ai_summary_profile_title), "profile:" + uid,
                AiSummarySources.profile(uid, userName), currentTarget);
        result.dialog.show();
        return result;
    }

    private void retry() {
        setReasoningExpanded(false);
        controller.start(target, input);
    }

    private void render(SummaryController.State state) {
        boolean loading = state.getStatus() == SummaryController.Status.LOADING;
        boolean canCopy = !state.getCopyText().isEmpty();
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setVisibility(loading ? View.VISIBLE : View.GONE);
        content.setText(state.getAnswer());
        content.setVisibility(state.getAnswer().isEmpty() ? View.GONE : View.VISIBLE);
        content.setTextIsSelectable(canCopy);
        error.setText(state.getErrorMessage());
        error.setVisibility(state.getErrorMessage().isEmpty() ? View.GONE : View.VISIBLE);
        renderReasoning(state);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(!loading);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(canCopy);
    }

    private void renderReasoning(SummaryController.State state) {
        boolean hasReasoning = !state.getReasoning().isEmpty();
        reasoningToggle.setVisibility(hasReasoning ? View.VISIBLE : View.GONE);
        reasoning.setText(state.getReasoning());
        reasoning.setVisibility(hasReasoning && reasoningExpanded ? View.VISIBLE : View.GONE);
    }

    private void setReasoningExpanded(boolean expanded) {
        reasoningExpanded = expanded;
        reasoningToggle.setText(expanded
                ? R.string.ai_summary_hide_reasoning : R.string.ai_summary_show_reasoning);
        ViewCompat.setStateDescription(reasoningToggle, reasoningToggle.getContext().getString(expanded
                ? R.string.ai_summary_reasoning_expanded : R.string.ai_summary_reasoning_collapsed));
        renderReasoning(controller.getState());
    }

    private void copy(Context context) {
        String copyText = controller.getState().getCopyText();
        if (copyText.isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    context.getString(R.string.ai_summary_action), copyText));
            Toast.makeText(context, R.string.ai_summary_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public void dismiss() {
        controller.cancel();
        dialog.dismiss();
    }
}
