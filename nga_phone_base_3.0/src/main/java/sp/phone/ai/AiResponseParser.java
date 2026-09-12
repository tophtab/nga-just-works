package sp.phone.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Typed decoding for the bounded Chat Completions and model-list response contracts. */
final class AiResponseParser {
    static final int MAX_TEXT_CHARS = 256 * 1024;
    static final int MAX_MODELS = 1024;

    private AiResponseParser() {
    }

    static String firstText(String json) throws InvalidResponseException {
        return completionText(json, (answer, reasoning) -> { });
    }

    interface ProgressListener {
        void onProgress(String answer, String reasoning);
    }

    static String completionText(String json, ProgressListener listener) throws InvalidResponseException {
        if (json != null && json.length() > AiStreamParser.MAX_EVENT_CHARS) {
            throw new InvalidResponseException(AiError.RESPONSE_TOO_LARGE);
        }
        AiMessageAccumulator message = new AiMessageAccumulator(listener);
        try {
            Chunk chunk = chatChunk(json, false);
            message.append(chunk.content, chunk.reasoning);
            // A complete JSON response remains compatible without an explicit finish_reason.
            return message.complete(chunk.finishReason);
        } finally {
            message.finishPartial();
        }
    }

    static Chunk chatChunk(String json, boolean streaming) throws InvalidResponseException {
        JSONObject root = decodeObject(json);
        if (root.get("error") != null) {
            throw new InvalidResponseException(AiError.SERVER);
        }
        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof JSONArray)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        JSONArray choices = (JSONArray) choicesValue;
        if (choices.isEmpty()) {
            if (streaming || root.get("usage") instanceof JSONObject) {
                return new Chunk("", "", null);
            }
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        JSONObject choice = firstChoice(choices);
        if (choice == null) {
            if (streaming) {
                return new Chunk("", "", null);
            }
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        String finish = optionalString(choice.get("finish_reason"));
        Object value = choice.get(streaming ? "delta" : "message");
        if (value == null && streaming && finish != null) {
            return new Chunk("", "", finish);
        }
        if (!(value instanceof JSONObject)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        JSONObject part = (JSONObject) value;
        String content = optionalString(part.get("content"));
        String reasoningContent = optionalString(part.get("reasoning_content"));
        String reasoning = optionalString(part.get("reasoning"));
        return new Chunk(content == null ? "" : content,
                reasoningContent != null && !reasoningContent.isEmpty()
                        ? reasoningContent : reasoning == null ? "" : reasoning,
                finish);
    }

    private static JSONObject firstChoice(JSONArray choices) throws InvalidResponseException {
        // Prefer index zero even if a provider reorders its choices. Without indexes, retain
        // ordinary first-choice JSON compatibility; never borrow text from another choice.
        JSONObject unindexedFirst = null;
        boolean indexed = false;
        for (int i = 0; i < choices.size(); i++) {
            Object value = choices.get(i);
            if (!(value instanceof JSONObject)) {
                if (i == 0) {
                    throw new InvalidResponseException(AiError.INVALID_RESPONSE);
                }
                continue;
            }
            JSONObject choice = (JSONObject) value;
            Object index = choice.get("index");
            if (!choice.containsKey("index")) {
                if (i == 0) {
                    unindexedFirst = choice;
                }
                continue;
            }
            if (!(index instanceof Integer) || (Integer) index < 0) {
                throw new InvalidResponseException(AiError.INVALID_RESPONSE);
            }
            indexed = true;
            if ((Integer) index == 0) {
                return choice;
            }
        }
        return indexed ? null : unindexedFirst;
    }

    private static String optionalString(Object value) throws InvalidResponseException {
        if (value != null && !(value instanceof String)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        return (String) value;
    }

    static final class Chunk {
        final String content;
        final String reasoning;
        final String finishReason;

        Chunk(String content, String reasoning, String finishReason) {
            this.content = content;
            this.reasoning = reasoning;
            this.finishReason = finishReason;
        }
    }

    static List<String> modelIds(String json) throws InvalidResponseException {
        Object data = decodeObject(json).get("data");
        if (!(data instanceof JSONArray)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        JSONArray rows = (JSONArray) data;
        if (rows.size() > MAX_MODELS) {
            throw new InvalidResponseException(AiError.RESPONSE_TOO_LARGE);
        }
        Set<String> models = new LinkedHashSet<>();
        for (Object row : rows) {
            if (!(row instanceof JSONObject)) {
                throw new InvalidResponseException(AiError.INVALID_RESPONSE);
            }
            Object id = ((JSONObject) row).get("id");
            if (!(id instanceof String)) {
                throw new InvalidResponseException(AiError.INVALID_RESPONSE);
            }
            try {
                models.add(AiConfig.normalizeModel((String) id));
            } catch (IllegalArgumentException ignored) {
                throw new InvalidResponseException(AiError.INVALID_RESPONSE);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(models));
    }

    private static JSONObject decodeObject(String json) throws InvalidResponseException {
        try {
            return SafeJsonParser.parseObject(json);
        } catch (RuntimeException ignored) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
    }

    static final class InvalidResponseException extends Exception {
        final AiError error;

        InvalidResponseException(AiError error) {
            super("Invalid AI response");
            this.error = error;
        }
    }
}
