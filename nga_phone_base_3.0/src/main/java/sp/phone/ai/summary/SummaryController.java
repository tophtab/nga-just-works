package sp.phone.ai.summary;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import sp.phone.ai.AiConfig;

/**
 * Owns one summary request. Public actions run on the UI executor; all callbacks are marshalled
 * onto that same executor. Generation and owner checks also cover callbacks after cancellation.
 */
public final class SummaryController {

    public interface Cancelable {
        Cancelable NONE = () -> { };
        void cancel();
    }

    public interface ConfigSource {
        AiConfig load() throws Exception;
    }

    public interface Callback {
        /** Cumulative projections of one model message; input sources do not publish progress. */
        default void onProgress(String answer, String reasoning) { }
        void onSuccess(String text);
        void onError(String safeMessage);
    }

    public interface InputSource {
        Cancelable load(Callback callback);
    }

    public interface Model {
        Cancelable summarize(AiConfig config, String prompt, Callback callback);
    }

    public enum Status { IDLE, LOADING, SUCCESS, ERROR }

    public static final class State {
        private final Status status;
        private final String answer;
        private final String reasoning;
        private final String errorMessage;

        State(Status status, String answer, String reasoning, String errorMessage) {
            this.status = status;
            this.answer = answer == null ? "" : answer;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public Status getStatus() {
            return status;
        }

        public String getText() {
            return status == Status.ERROR ? errorMessage : answer;
        }

        public String getAnswer() {
            return answer;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getCopyText() {
            return status == Status.SUCCESS || status == Status.ERROR ? answer : "";
        }
    }

    public interface Listener {
        void onState(State state);
        void onConfigurationRequired();
    }

    private final ConfigSource configs;
    private final Model model;
    private final Executor executor;
    private final Supplier<String> currentTarget;
    private final Listener listener;
    private final Object updatesLock = new Object();
    // Guarded by updatesLock. At most one model-update runnable is queued across generations.
    private Pending queuedUpdate;
    private boolean updateScheduled;
    private long generation;
    private Pending active;
    private State state = new State(Status.IDLE, "", "", "");

    private static final class Pending {
        final long generation;
        final String target;
        Cancelable input = Cancelable.NONE;
        Cancelable model = Cancelable.NONE;
        boolean inputDelivered;
        boolean complete;
        // Guarded by the controller's updatesLock; transport callbacks never read UI-owned fields.
        State snapshot = new State(Status.LOADING, "", "", "");
        boolean updatesClosed;

        Pending(long generation, String target) {
            this.generation = generation;
            this.target = target;
        }
    }

    public SummaryController(ConfigSource configs, Model model, Executor executor,
                             Supplier<String> currentTarget, Listener listener) {
        this.configs = configs;
        this.model = model;
        this.executor = executor;
        this.currentTarget = currentTarget;
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public void start(String target, InputSource source) {
        cancel();
        Pending request = new Pending(generation, target);
        active = request;
        if (!isCurrent(request)) {
            return;
        }
        AiConfig config = loadConfig(request);
        if (config == null || !isCurrent(request)) {
            return;
        }
        publish(new State(Status.LOADING, "", "", ""));
        if (!isCurrent(request)) {
            return;
        }
        try {
            Cancelable input = source.load(new Callback() {
                @Override
                public void onSuccess(String prompt) {
                    executor.execute(() -> acceptInput(request, config, prompt));
                }

                @Override
                public void onError(String message) {
                    executor.execute(() -> {
                        if (isCurrent(request) && !request.inputDelivered) {
                            request.inputDelivered = true;
                            fail(request, message);
                        }
                    });
                }
            });
            if (isCurrent(request) && !request.inputDelivered) {
                request.input = input;
            } else {
                input.cancel();
            }
        } catch (RuntimeException ignored) {
            fail(request, "无法读取总结内容，请重试");
        }
    }

    private void acceptInput(Pending request, AiConfig initialConfig, String prompt) {
        if (!isCurrent(request) || request.inputDelivered) {
            return;
        }
        request.inputDelivered = true;
        AiConfig config = loadConfig(request);
        if (config == null || !isCurrent(request)) {
            return;
        }
        if (!sameConfig(initialConfig, config)) {
            fail(request, "AI 配置已变化，请重新发起总结");
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            fail(request, "没有可用于总结的内容");
            return;
        }
        try {
            Cancelable call = model.summarize(config, prompt, new Callback() {
                @Override
                public void onProgress(String answer, String reasoning) {
                    enqueueModelUpdate(request, Status.LOADING, answer, reasoning);
                }

                @Override
                public void onSuccess(String text) {
                    enqueueModelUpdate(request, Status.SUCCESS, text, "");
                }

                @Override
                public void onError(String message) {
                    enqueueModelUpdate(request, Status.ERROR, message, "");
                }
            });
            if (isCurrent(request)) {
                request.model = call;
            } else {
                call.cancel();
            }
        } catch (RuntimeException ignored) {
            fail(request, "无法发起 AI 总结，请检查配置后重试");
        }
    }

    private void enqueueModelUpdate(Pending request, Status status, String text, String reasoning) {
        synchronized (updatesLock) {
            if (request.updatesClosed) {
                return;
            }
            State previous = request.snapshot;
            switch (status) {
                case LOADING:
                    request.snapshot = new State(status, text, reasoning, "");
                    break;
                case SUCCESS:
                    request.snapshot = new State(status, text, previous.getReasoning(), "");
                    break;
                case ERROR:
                    request.snapshot = new State(status, previous.getAnswer(),
                            previous.getReasoning(), text);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid model update status");
            }
            request.updatesClosed = status != Status.LOADING;
            queuedUpdate = request;
            if (updateScheduled) {
                return;
            }
            updateScheduled = true;
        }
        executor.execute(this::deliverModelUpdate);
    }

    private void deliverModelUpdate() {
        Pending request;
        State snapshot;
        synchronized (updatesLock) {
            request = queuedUpdate;
            snapshot = request == null ? null : request.snapshot;
            queuedUpdate = null;
            updateScheduled = false;
        }
        if (request == null || !isCurrent(request)) {
            return;
        }
        if (snapshot.getStatus() == Status.LOADING) {
            publish(snapshot);
        } else {
            finish(request, snapshot);
        }
    }

    private static boolean sameConfig(AiConfig first, AiConfig second) {
        return first.getEndpoint().equals(second.getEndpoint())
                && first.getModel().equals(second.getModel())
                && first.getApiKey().equals(second.getApiKey());
    }

    private AiConfig loadConfig(Pending request) {
        try {
            AiConfig config = configs.load();
            if (config == null) {
                if (isCurrent(request)) {
                    cancel();
                    listener.onConfigurationRequired();
                }
                return null;
            }
            return config;
        } catch (Exception ignored) {
            fail(request, "无法读取 AI 配置，请在 AI 设置中重新保存");
            return null;
        }
    }

    private boolean isCurrent(Pending request) {
        if (active != request || generation != request.generation || request.complete) {
            return false;
        }
        if (!request.target.equals(currentTarget.get())) {
            cancel();
            return false;
        }
        return true;
    }

    private void fail(Pending request, String message) {
        if (!isCurrent(request)) {
            return;
        }
        State failure;
        synchronized (updatesLock) {
            if (request.updatesClosed) {
                return;
            }
            request.updatesClosed = true;
            failure = new State(Status.ERROR, request.snapshot.getAnswer(),
                    request.snapshot.getReasoning(), message);
            request.snapshot = failure;
            if (queuedUpdate == request) {
                queuedUpdate = null;
            }
        }
        finish(request, failure);
    }

    private void finish(Pending request, State result) {
        if (!isCurrent(request)) {
            return;
        }
        request.complete = true;
        request.input.cancel();
        request.model.cancel();
        publish(result);
    }

    private void publish(State next) {
        state = next;
        listener.onState(state);
    }

    public void cancel() {
        generation++;
        Pending previous = active;
        active = null;
        synchronized (updatesLock) {
            if (previous != null) {
                previous.updatesClosed = true;
                previous.snapshot = new State(Status.IDLE, "", "", "");
            }
            queuedUpdate = null;
            // Keep updateScheduled until its runnable drains, even after retry or dismissal.
        }
        if (previous != null) {
            previous.input.cancel();
            previous.model.cancel();
        }
        state = new State(Status.IDLE, "", "", "");
    }
}
