package sp.phone.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/** The only decoder for the small Chat Completions response contract used by this feature. */
final class AiResponseParser {
    static final int MAX_TEXT_CHARS = 32 * 1024;

    private AiResponseParser() {
    }

    static String firstText(String json) throws InvalidResponseException {
        final JSONObject decoded;
        try {
            decoded = SafeJsonParser.parseObject(json);
        } catch (RuntimeException ignored) {
            throw new InvalidResponseException(AiError.INVALID_RESPONSE);
        }
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

    static final class InvalidResponseException extends Exception {
        final AiError error;

        InvalidResponseException(AiError error) {
            super("Invalid AI response");
            this.error = error;
        }
    }
}
