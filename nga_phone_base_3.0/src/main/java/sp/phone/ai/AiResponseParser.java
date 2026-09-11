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
    static final int MAX_TEXT_CHARS = 32 * 1024;
    static final int MAX_MODELS = 1024;

    private AiResponseParser() {
    }

    static String firstText(String json) throws InvalidResponseException {
        JSONObject decoded = decodeObject(json);
        Object choices = decoded.get("choices");
        if (!(choices instanceof JSONArray) || ((JSONArray) choices).isEmpty()) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        Object first = ((JSONArray) choices).get(0);
        if (!(first instanceof JSONObject)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        Object message = ((JSONObject) first).get("message");
        if (!(message instanceof JSONObject)) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        Object content = ((JSONObject) message).get("content");
        if (!(content instanceof String) || ((String) content).trim().isEmpty()) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        if (((String) content).length() > MAX_TEXT_CHARS) {
            throw new InvalidResponseException(AiError.RESPONSE_TOO_LARGE);
        }
        return ((String) content).trim();
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
