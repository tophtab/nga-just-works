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
        private final String text;

        State(Status status, String text) {
            this.status = status;
            this.text = text;
        }

        public Status getStatus() {
            return status;
        }

        public String getText() {
            return text;
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
    private long generation;
    private Pending active;
    private State state = new State(Status.IDLE, "");

    private static final class Pending {
        final long generation;
        final String target;
        Cancelable input = Cancelable.NONE;
        Cancelable model = Cancelable.NONE;
        boolean inputDelivered;
        boolean complete;

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
        publish(Status.LOADING, "");
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
                            finish(request, Status.ERROR, message);
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
            finish(request, Status.ERROR, "无法读取总结内容，请重试");
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
            finish(request, Status.ERROR, "AI 配置已变化，请重新发起总结");
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            finish(request, Status.ERROR, "没有可用于总结的内容");
            return;
        }
        try {
            Cancelable call = model.summarize(config, prompt, new Callback() {
                @Override
                public void onSuccess(String text) {
                    executor.execute(() -> finish(request, Status.SUCCESS, text));
                }

                @Override
                public void onError(String message) {
                    executor.execute(() -> finish(request, Status.ERROR, message));
                }
            });
            if (isCurrent(request)) {
                request.model = call;
            } else {
                call.cancel();
            }
        } catch (RuntimeException ignored) {
            finish(request, Status.ERROR, "无法发起 AI 总结，请检查配置后重试");
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
            finish(request, Status.ERROR, "无法读取 AI 配置，请在 AI 设置中重新保存");
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

    private void finish(Pending request, Status status, String text) {
        if (!isCurrent(request)) {
            return;
        }
        request.complete = true;
        request.input.cancel();
        request.model.cancel();
        publish(status, text);
    }

    private void publish(Status status, String text) {
        state = new State(status, text);
        listener.onState(state);
    }

    public void cancel() {
        generation++;
        Pending previous = active;
        active = null;
        if (previous != null) {
            previous.input.cancel();
            previous.model.cancel();
        }
        state = new State(Status.IDLE, "");
    }
}
