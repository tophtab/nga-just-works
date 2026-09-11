package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import org.junit.Test;

import sp.phone.ai.AiConfig;

public class SummaryControllerTest {

    @Test
    public void missingConfigurationPreventsBothProfileCollectionAndModelCalls() {
        Fixture test = new Fixture();
        test.config = null;
        test.controller.start(test.target, test.input);
        assertEquals(1, test.configurationPrompts);
        assertTrue(test.input.calls.isEmpty());
        assertTrue(test.model.calls.isEmpty());
        assertEquals(SummaryController.Status.IDLE, test.controller.getState().getStatus());
    }

    @Test
    public void configurationReadFailureNeverLeaksTheExceptionOrStartsCollection() {
        Fixture test = new Fixture();
        test.storageFails = true;
        test.controller.start(test.target, test.input);
        assertTrue(test.input.calls.isEmpty());
        assertTrue(test.model.calls.isEmpty());
        assertEquals(SummaryController.Status.ERROR, test.controller.getState().getStatus());
        assertFalse(test.controller.getState().getText().contains("SECRET_SENTINEL"));
    }

    @Test
    public void callbacksAreMarshalledThroughTheOwnerExecutorAndReachSuccess() {
        Fixture test = new Fixture();
        assertEquals(SummaryController.Status.IDLE, test.controller.getState().getStatus());
        test.controller.start(test.target, test.input);
        assertEquals(SummaryController.Status.LOADING, test.controller.getState().getStatus());
        test.input.calls.get(0).callback.onSuccess("Selected floor prompt");
        assertTrue(test.model.calls.isEmpty());
        test.executor.drain();
        assertEquals("Selected floor prompt", test.model.prompts.get(0));
        test.model.calls.get(0).callback.onSuccess("Final summary");
        assertEquals(SummaryController.Status.LOADING, test.controller.getState().getStatus());
        test.executor.drain();
        assertEquals(SummaryController.Status.SUCCESS, test.controller.getState().getStatus());
        assertEquals("Final summary", test.controller.getState().getText());
        assertEquals("Final summary", test.controller.getState().getCopyText());
        assertEquals("", test.controller.getState().getReasoning());
    }

    @Test
    public void progressDisplaysCumulativeAnswerAndReasoningBeforeCompletionWithoutEnablingCopy() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Selected floor prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        callback.onProgress("First", "Synthetic reasoning");
        assertEquals("", test.controller.getState().getAnswer());
        assertEquals("", test.controller.getState().getReasoning());
        test.executor.drain();
        SummaryController.State first = test.controller.getState();
        assertEquals(SummaryController.Status.LOADING, first.getStatus());
        assertEquals("First", first.getAnswer());
        assertEquals("First", first.getText());
        assertEquals("Synthetic reasoning", first.getReasoning());
        assertEquals("", first.getErrorMessage());
        assertEquals("", first.getCopyText());

        callback.onProgress("First and second", "Synthetic reasoning continued");
        test.executor.drain();
        assertEquals("First and second", test.controller.getState().getAnswer());
        assertEquals("Synthetic reasoning continued", test.controller.getState().getReasoning());
        assertEquals("", test.controller.getState().getCopyText());
        assertEquals("First", first.getAnswer());
        assertEquals("Synthetic reasoning", first.getReasoning());
    }

    @Test
    public void progressBurstKeepsOnlyOneQueuedUiDeliveryAndPublishesTheLatestSnapshot() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        for (int i = 0; i < 2000; i++) {
            callback.onProgress("Answer " + i, "Synthetic reasoning " + i);
        }
        assertEquals(1, test.executor.queue.size());
        assertEquals(1, test.states.size());
        test.executor.drain();
        assertEquals(2, test.states.size());
        assertEquals("Answer 1999", test.controller.getState().getAnswer());
        assertEquals("Synthetic reasoning 1999", test.controller.getState().getReasoning());
    }

    @Test
    public void successKeepsUndeliveredReasoningAndCopiesTheFullAnswerAboveOneThousandCharacters() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        String answer = "  " + repeat("正文", 800) + "\n";
        callback.onProgress("Draft", "Synthetic reasoning");
        callback.onProgress("Draft body", "Synthetic reasoning complete");
        callback.onSuccess(answer);
        callback.onProgress("LATE_ANSWER", "LATE_REASONING");
        callback.onError("LATE_ERROR");
        assertEquals(1, test.executor.queue.size());
        assertEquals("", test.controller.getState().getCopyText());
        test.executor.drain();
        SummaryController.State state = test.controller.getState();
        assertEquals(SummaryController.Status.SUCCESS, state.getStatus());
        assertEquals(answer, state.getAnswer());
        assertEquals(answer, state.getText());
        assertEquals(answer, state.getCopyText());
        assertEquals("Synthetic reasoning complete", state.getReasoning());
        assertEquals("", state.getErrorMessage());
        assertTrue(test.model.calls.get(0).canceled);
        assertEquals(1, test.successCount);
    }

    @Test
    public void interruptedStreamKeepsUndeliveredPartialTextAndCopiesOnlyTheReceivedAnswer() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        String answer = repeat("Partial answer. ", 100);
        callback.onProgress(answer, "Synthetic partial reasoning");
        callback.onError("Stream interrupted");
        assertEquals(1, test.executor.queue.size());
        test.executor.drain();
        SummaryController.State state = test.controller.getState();
        assertEquals(SummaryController.Status.ERROR, state.getStatus());
        assertEquals(answer, state.getAnswer());
        assertEquals("Synthetic partial reasoning", state.getReasoning());
        assertEquals("Stream interrupted", state.getErrorMessage());
        assertEquals("Stream interrupted", state.getText());
        assertEquals(answer, state.getCopyText());
        callback.onSuccess("LATE_SUCCESS");
        callback.onProgress("LATE_ANSWER", "LATE_REASONING");
        assertTrue(test.executor.queue.isEmpty());
        assertEquals(answer, test.controller.getState().getCopyText());
        assertEquals(0, test.successCount);
    }

    @Test
    public void reasoningOnlyFailureNeverProvidesCopyableText() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        callback.onProgress("", "Synthetic reasoning without an answer");
        test.executor.drain();
        assertEquals("", test.controller.getState().getCopyText());
        callback.onError("Empty answer");
        test.executor.drain();
        assertEquals(SummaryController.Status.ERROR, test.controller.getState().getStatus());
        assertEquals("", test.controller.getState().getAnswer());
        assertEquals("", test.controller.getState().getCopyText());
        assertEquals("Synthetic reasoning without an answer", test.controller.getState().getReasoning());
        assertEquals("Empty answer", test.controller.getState().getErrorMessage());
    }

    @Test
    public void terminalUpdateDuringProgressDeliveryRetainsTheLatestChannels() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        SummaryController.Callback callback = test.model.calls.get(0).callback;
        test.onNextState = () -> {
            callback.onProgress("Last partial answer", "Last synthetic reasoning");
            callback.onError("Stream interrupted");
        };
        callback.onProgress("First partial answer", "First synthetic reasoning");
        test.executor.drain();
        assertEquals(3, test.states.size());
        assertEquals(SummaryController.Status.ERROR, test.controller.getState().getStatus());
        assertEquals("Last partial answer", test.controller.getState().getAnswer());
        assertEquals("Last synthetic reasoning", test.controller.getState().getReasoning());
        assertEquals("Last partial answer", test.controller.getState().getCopyText());
    }

    @Test
    public void inputSourceProgressCannotBeRenderedAsModelOutput() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        test.input.calls.get(0).callback.onProgress("SOURCE_TEXT", "SOURCE_METADATA");
        assertTrue(test.executor.queue.isEmpty());
        assertEquals("", test.controller.getState().getAnswer());
        assertEquals("", test.controller.getState().getReasoning());
        test.input.calls.get(0).callback.onSuccess("Current input");
        test.executor.drain();
        assertEquals("Current input", test.model.prompts.get(0));
    }

    @Test
    public void dismissClearsBothChannelsAndDiscardsQueuedAndFutureModelProgress() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Profile prompt");
        Pending model = test.model.calls.get(0);
        model.callback.onProgress("Visible partial answer", "Visible synthetic reasoning");
        test.executor.drain();
        model.callback.onProgress("Queued partial answer", "Queued synthetic reasoning");
        test.controller.cancel();
        assertTrue(model.canceled);
        assertEmptyIdleState(test.controller.getState());
        int delivered = test.states.size();
        for (int i = 0; i < 1000; i++) {
            model.callback.onProgress("LATE_ANSWER", "LATE_REASONING");
        }
        model.callback.onSuccess("LATE_SUCCESS");
        model.callback.onError("LATE_ERROR");
        assertEquals(1, test.executor.queue.size());
        test.executor.drain();
        assertEmptyIdleState(test.controller.getState());
        assertEquals(delivered, test.states.size());
    }

    @Test
    public void retryClearsVisibleAndQueuedChannelsAndRejectsLateProgressForTheSameTarget() {
        Fixture test = new Fixture();
        test.startAndProvideInput("First prompt");
        Pending first = test.model.calls.get(0);
        first.callback.onProgress("Old answer", "Old synthetic reasoning");
        test.executor.drain();
        first.callback.onProgress("Queued old answer", "Queued old reasoning");
        test.controller.start(test.target, test.input);
        assertTrue(first.canceled);
        assertEquals(SummaryController.Status.LOADING, test.controller.getState().getStatus());
        assertEquals("", test.controller.getState().getAnswer());
        assertEquals("", test.controller.getState().getReasoning());
        assertEquals("", test.controller.getState().getCopyText());
        first.callback.onProgress("LATE_ANSWER", "LATE_REASONING");
        first.callback.onSuccess("LATE_SUCCESS");
        test.input.calls.get(1).callback.onSuccess("Second prompt");
        test.executor.drain();
        assertEquals("", test.controller.getState().getAnswer());
        assertEquals("", test.controller.getState().getReasoning());
        test.model.calls.get(1).callback.onProgress("New answer", "New synthetic reasoning");
        test.executor.drain();
        assertEquals("New answer", test.controller.getState().getAnswer());
        assertEquals("New synthetic reasoning", test.controller.getState().getReasoning());
    }

    @Test
    public void changedTargetDiscardsQueuedProgressAndCancelsTheOldRequest() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Viewed user prompt");
        Pending old = test.model.calls.get(0);
        old.callback.onProgress("OLD_USER_ANSWER", "OLD_USER_REASONING");
        test.target = "profile:99";
        test.executor.drain();
        assertTrue(old.canceled);
        assertEmptyIdleState(test.controller.getState());
        old.callback.onProgress("LATE_ANSWER", "LATE_REASONING");
        assertTrue(test.executor.queue.isEmpty());

        test.startAndProvideInput("New viewed user prompt");
        test.model.calls.get(1).callback.onProgress("New user answer", "New synthetic reasoning");
        test.executor.drain();
        old.callback.onProgress("WRONG_USER_ANSWER", "WRONG_USER_REASONING");
        test.model.calls.get(1).callback.onSuccess("New user answer");
        test.executor.drain();
        assertEquals("New user answer", test.controller.getState().getCopyText());
        assertEquals("New synthetic reasoning", test.controller.getState().getReasoning());
    }

    @Test
    public void retryDiscardsOldSuccessAndErrorEvenWhenTargetIsUnchanged() {
        Fixture test = new Fixture();
        test.startAndProvideInput("first");
        Pending firstModel = test.model.calls.get(0);
        test.startAndProvideInput("second");
        assertTrue(firstModel.canceled);
        assertTrue(test.input.calls.get(0).canceled);
        firstModel.callback.onSuccess("OBSOLETE_RESULT");
        firstModel.callback.onError("OBSOLETE_ERROR");
        test.executor.drain();
        assertEquals(SummaryController.Status.LOADING, test.controller.getState().getStatus());
        test.model.calls.get(1).callback.onSuccess("Current summary");
        test.executor.drain();
        assertEquals("Current summary", test.controller.getState().getText());
        assertEquals(1, test.successCount);
    }

    @Test
    public void replacedInputCannotStartAModelCallWhenItArrivesLate() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        Pending first = test.input.calls.get(0);
        test.controller.start(test.target, test.input);
        first.callback.onSuccess("OBSOLETE_INPUT");
        test.executor.drain();
        assertTrue(first.canceled);
        assertTrue(test.model.calls.isEmpty());
        test.input.calls.get(1).callback.onSuccess("Current input");
        test.executor.drain();
        assertEquals(1, test.model.calls.size());
    }

    @Test
    public void changedTargetRejectsLateResultsAndCancelsItsHandle() {
        Fixture test = new Fixture();
        test.startAndProvideInput("Viewed user input");
        test.target = "profile:99";
        test.model.calls.get(0).callback.onSuccess("WRONG_USER_RESULT");
        test.executor.drain();
        assertEquals(0, test.successCount);
        assertTrue(test.model.calls.get(0).canceled);
        assertEquals(SummaryController.Status.IDLE, test.controller.getState().getStatus());
    }

    @Test
    public void dismissOrPagePauseCancelsCollectionAndDiscardsQueuedCallbacks() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        test.input.calls.get(0).callback.onSuccess("QUEUED_BEFORE_DISMISS");
        test.controller.cancel();
        test.target = null;
        test.executor.drain();
        assertTrue(test.input.calls.get(0).canceled);
        assertTrue(test.model.calls.isEmpty());
        assertEquals(0, test.successCount);
    }

    @Test
    public void clearedConfigurationDuringCollectionPreventsTheModelRequest() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        test.config = null;
        test.input.calls.get(0).callback.onSuccess("Public first-page input");
        test.executor.drain();
        assertTrue(test.model.calls.isEmpty());
        assertEquals(1, test.configurationPrompts);
        assertTrue(test.input.calls.get(0).canceled);
    }

    @Test
    public void changedConfigurationRequiresADeliberateNewRequest() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        test.config = new AiConfig("https://other.example/v1", "test-only-key", "synthetic-model");
        test.input.calls.get(0).callback.onSuccess("Public first-page input");
        test.executor.drain();
        assertTrue(test.model.calls.isEmpty());
        assertEquals(SummaryController.Status.ERROR, test.controller.getState().getStatus());
    }

    @Test
    public void failureThenRetryCanSucceedAndDuplicateSourceCallbacksAreIgnored() {
        Fixture test = new Fixture();
        test.controller.start(test.target, test.input);
        test.input.calls.get(0).callback.onError("Safe collection failure");
        test.executor.drain();
        assertEquals(SummaryController.Status.ERROR, test.controller.getState().getStatus());
        test.input.calls.get(0).callback.onSuccess("INVALID_LATE_SUCCESS");
        test.executor.drain();
        assertTrue(test.model.calls.isEmpty());
        test.startAndProvideInput("Retry input");
        test.input.calls.get(1).callback.onSuccess("DUPLICATE_INPUT");
        test.executor.drain();
        assertEquals(1, test.model.calls.size());
        test.model.calls.get(0).callback.onSuccess("Retry summary");
        test.executor.drain();
        assertEquals("Retry summary", test.controller.getState().getText());
    }

    @Test
    public void synchronousCallbacksCannotOverwriteACompletedRequestsHandles() {
        AiConfig config = new AiConfig("https://api.example/v1", "test-only-key", "synthetic-model");
        Pending input = new Pending(null);
        Pending model = new Pending(null);
        List<SummaryController.Status> states = new ArrayList<>();
        SummaryController controller = new SummaryController(() -> config, (unused, prompt, callback) -> {
            callback.onProgress("Synchronous draft", "Synchronous synthetic reasoning");
            callback.onSuccess("Synchronous result");
            return model;
        }, Runnable::run, () -> "floor:1", new SummaryController.Listener() {
            @Override
            public void onState(SummaryController.State state) {
                states.add(state.getStatus());
            }

            @Override
            public void onConfigurationRequired() {
                throw new AssertionError("Configured");
            }
        });
        controller.start("floor:1", callback -> {
            callback.onSuccess("Synchronous input");
            return input;
        });
        assertEquals(SummaryController.Status.SUCCESS, controller.getState().getStatus());
        assertTrue(input.canceled);
        assertTrue(model.canceled);
        assertEquals("Synchronous synthetic reasoning", controller.getState().getReasoning());
        assertEquals("Synchronous result", controller.getState().getCopyText());
        assertEquals(3, states.size());
    }

    private static void assertEmptyIdleState(SummaryController.State state) {
        assertEquals(SummaryController.Status.IDLE, state.getStatus());
        assertEquals("", state.getAnswer());
        assertEquals("", state.getReasoning());
        assertEquals("", state.getErrorMessage());
        assertEquals("", state.getCopyText());
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(text);
        }
        return result.toString();
    }

    private static final class Fixture implements SummaryController.Listener {
        AiConfig config = new AiConfig("https://api.example/v1", "test-only-key", "synthetic-model");
        boolean storageFails;
        String target = "profile:42";
        int configurationPrompts;
        int successCount;
        Runnable onNextState;
        final List<SummaryController.State> states = new ArrayList<>();
        final QueuedExecutor executor = new QueuedExecutor();
        final FakeInput input = new FakeInput();
        final FakeModel model = new FakeModel();
        final SummaryController controller = new SummaryController(() -> {
            if (storageFails) {
                throw new IllegalStateException("SECRET_SENTINEL");
            }
            return config;
        }, model, executor, () -> target, this);

        void startAndProvideInput(String prompt) {
            controller.start(target, input);
            input.calls.get(input.calls.size() - 1).callback.onSuccess(prompt);
            executor.drain();
        }

        @Override
        public void onState(SummaryController.State state) {
            states.add(state);
            if (state.getStatus() == SummaryController.Status.SUCCESS) {
                successCount++;
            }
            if (onNextState != null) {
                Runnable action = onNextState;
                onNextState = null;
                action.run();
            }
        }

        @Override
        public void onConfigurationRequired() {
            configurationPrompts++;
        }
    }

    private static final class QueuedExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void drain() {
            while (!queue.isEmpty()) {
                queue.remove().run();
            }
        }
    }

    private static final class Pending implements SummaryController.Cancelable {
        final SummaryController.Callback callback;
        boolean canceled;

        Pending(SummaryController.Callback callback) {
            this.callback = callback;
        }

        @Override
        public void cancel() {
            canceled = true;
        }
    }

    private static final class FakeInput implements SummaryController.InputSource {
        final List<Pending> calls = new ArrayList<>();

        @Override
        public SummaryController.Cancelable load(SummaryController.Callback callback) {
            Pending request = new Pending(callback);
            calls.add(request);
            return request;
        }
    }

    private static final class FakeModel implements SummaryController.Model {
        final List<Pending> calls = new ArrayList<>();
        final List<String> prompts = new ArrayList<>();

        @Override
        public SummaryController.Cancelable summarize(AiConfig config, String prompt,
                                                      SummaryController.Callback callback) {
            Pending request = new Pending(callback);
            calls.add(request);
            prompts.add(prompt);
            return request;
        }
    }
}
