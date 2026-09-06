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
        assertEquals(2, states.size());
    }

    private static final class Fixture implements SummaryController.Listener {
        AiConfig config = new AiConfig("https://api.example/v1", "test-only-key", "synthetic-model");
        boolean storageFails;
        String target = "profile:42";
        int configurationPrompts;
        int successCount;
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
            if (state.getStatus() == SummaryController.Status.SUCCESS) {
                successCount++;
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
