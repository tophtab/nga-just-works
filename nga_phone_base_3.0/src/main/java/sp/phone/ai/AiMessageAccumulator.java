package sp.phone.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** One transient message with answer/reasoning projections, before any UI or copy operation. */
final class AiMessageAccumulator {
    private static final String[] OPEN_TAGS = {"<think>", "<thinking>"};
    private static final String[] PROTOCOL_PREFIXES = {"data:", "event:", "id:", ":", "[DONE]"};
    private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final Pattern CHAT_OBJECT = Pattern.compile(
            "\"object\"\\s*:\\s*\"chat\\.completion(?:\\.chunk)?\"");

    private final AiResponseParser.ProgressListener listener;
    private final StringBuilder answer = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final StringBuilder leading = new StringBuilder();
    private final StringBuilder closing = new StringBuilder();
    private String closeTag;
    private int leadingStart = -1;
    private int answerStart = -1;
    private boolean answerStarted;
    private boolean plainAnswer;
    private long lastProgress;
    private String publishedAnswer = "";
    private String publishedReasoning = "";

    AiMessageAccumulator(AiResponseParser.ProgressListener listener) {
        this.listener = listener;
    }

    void append(String content, String nativeReasoning) throws AiResponseParser.InvalidResponseException {
        appendBounded(reasoning, nativeReasoning);
        for (int i = 0; i < content.length(); i++) {
            if (answerStarted) {
                appendAnswer(content.substring(i));
                break;
            }
            char character = content.charAt(i);
            if (closeTag != null) {
                closing.append(character);
                // Keep only a possible closing-tag suffix between chunks. Everything else is
                // reasoning, including '<' that turns out not to begin the matching close tag.
                while (!closeTag.startsWith(closing.toString())) {
                    appendBounded(reasoning, closing.substring(0, 1));
                    closing.deleteCharAt(0);
                }
                if (closeTag.contentEquals(closing)) {
                    closing.setLength(0);
                    closeTag = null;
                }
                continue;
            }
            appendBounded(leading, String.valueOf(character));
            if (leadingStart < 0) {
                if (isSpace(character)) {
                    continue;
                }
                leadingStart = leading.length() - 1;
            }
            String candidate = leading.substring(leadingStart);
            boolean possibleTag = false;
            for (String tag : OPEN_TAGS) {
                if (tag.equals(candidate)) {
                    closeTag = "</" + tag.substring(1);
                    leading.setLength(0);
                    leadingStart = -1;
                    possibleTag = true;
                    break;
                }
                possibleTag |= tag.startsWith(candidate);
            }
            if (!possibleTag) {
                answerStarted = true;
                appendAnswer(leading.toString());
                leading.setLength(0);
            }
        }
        publish(false);
    }

    String complete(String finishReason) throws AiResponseParser.InvalidResponseException {
        finishPendingText();
        boolean exhausted = "length".equals(finishReason);
        if (!plainAnswer && isSerializedProtocol(answer.toString())) {
            throw new AiResponseParser.InvalidResponseException(
                    exhausted ? AiError.OUTPUT_EXHAUSTED : AiError.INVALID_RESPONSE);
        }
        plainAnswer = true;
        publish(true);
        if (exhausted) {
            throw new AiResponseParser.InvalidResponseException(AiError.OUTPUT_EXHAUSTED);
        }
        if (finishReason != null && !"stop".equals(finishReason)
                && !"tool_calls".equals(finishReason) && !"function_call".equals(finishReason)) {
            throw new AiResponseParser.InvalidResponseException(AiError.INVALID_RESPONSE);
        }
        if (isBlank(answer)) {
            throw new AiResponseParser.InvalidResponseException(AiError.EMPTY_RESPONSE);
        }
        return answer.toString();
    }

    private void finishPendingText() throws AiResponseParser.InvalidResponseException {
        if (closeTag != null) {
            // A matched but unclosed thinking section is never promoted to an answer.
            appendBounded(reasoning, closing.toString());
            closing.setLength(0);
        } else if (leading.length() > 0) {
            appendAnswer(leading.toString());
            leading.setLength(0);
        }
    }

    void publish(boolean force) {
        long now = System.nanoTime();
        if (!force && lastProgress != 0 && now - lastProgress < PROGRESS_INTERVAL_NANOS) {
            return;
        }
        // Potential envelopes are held until complete validation. Even an early snapshot must
        // never expose raw protocol as answer text. Ordinary prose streams immediately.
        String visibleAnswer = plainAnswer ? answer.toString() : "";
        String visibleReasoning = reasoning.toString();
        if (!visibleAnswer.equals(publishedAnswer) || !visibleReasoning.equals(publishedReasoning)) {
            publishedAnswer = visibleAnswer;
            publishedReasoning = visibleReasoning;
            lastProgress = now;
            listener.onProgress(visibleAnswer, visibleReasoning);
        }
    }

    void finishPartial() throws AiResponseParser.InvalidResponseException {
        try {
            finishPendingText();
            if (!plainAnswer) {
                // An interrupted ordinary JSON/code reply is still received answer text.
                // Apply the same screening as completion before releasing a held candidate.
                plainAnswer = !isSerializedProtocol(answer.toString());
            }
        } finally {
            publish(true);
        }
    }

    private void appendAnswer(String text) throws AiResponseParser.InvalidResponseException {
        int previousLength = answer.length();
        appendBounded(answer, text);
        if (answerStart < 0) {
            for (int i = previousLength; i < answer.length(); i++) {
                if (!isSpace(answer.charAt(i))) {
                    answerStart = i;
                    break;
                }
            }
        }
        if (!plainAnswer && answerStart >= 0 && answer.charAt(answerStart) != '{') {
            String prefix = answer.substring(answerStart, Math.min(answer.length(), answerStart + 8));
            boolean possibleProtocol = false;
            for (String marker : PROTOCOL_PREFIXES) {
                possibleProtocol |= marker.startsWith(prefix) || prefix.startsWith(marker);
            }
            plainAnswer = !possibleProtocol;
        }
    }

    private static void appendBounded(StringBuilder target, String text)
            throws AiResponseParser.InvalidResponseException {
        if (text.length() > AiResponseParser.MAX_TEXT_CHARS - target.length()) {
            throw new AiResponseParser.InvalidResponseException(AiError.RESPONSE_TOO_LARGE);
        }
        target.append(text);
    }

    private static boolean isSerializedProtocol(String text) {
        String value = trimWhitespace(text);
        if ("[DONE]".equals(value) || isProtocolObject(value)) {
            return true;
        }
        StringBuilder data = new StringBuilder();
        for (String line : value.split("\\r\\n|\\r|\\n")) {
            if (line.isEmpty()) {
                if (isProtocolData(data.toString())) {
                    return true;
                }
                data.setLength(0);
            } else if (line.startsWith("data:")) {
                String payload = trimWhitespace(line.substring(5));
                if (isProtocolData(payload)) {
                    return true;
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(payload);
            }
        }
        return isProtocolData(data.toString());
    }

    private static boolean isProtocolData(String data) {
        String value = trimWhitespace(data);
        return "[DONE]".equals(value) || isProtocolObject(value);
    }

    private static boolean isProtocolObject(String value) {
        if (!value.startsWith("{")) {
            return false;
        }
        final JSONObject object;
        try {
            object = SafeJsonParser.parseObject(value);
        } catch (RuntimeException ignored) {
            // Recognize even an interrupted envelope when its explicit object type survives.
            return CHAT_OBJECT.matcher(value).find();
        }
        if ("chat.completion".equals(object.get("object"))
                || "chat.completion.chunk".equals(object.get("object"))) {
            return true;
        }
        Object choices = object.get("choices");
        if (choices instanceof JSONArray) {
            JSONArray rows = (JSONArray) choices;
            if (rows.isEmpty()) {
                return true;
            }
            for (Object row : rows) {
                if (row instanceof JSONObject && (((JSONObject) row).get("delta") instanceof JSONObject
                        || ((JSONObject) row).get("message") instanceof JSONObject)) {
                    return true;
                }
            }
        }
        if ("assistant".equals(object.get("role")) && (object.containsKey("content")
                || object.containsKey("reasoning_content") || object.containsKey("reasoning"))) {
            return true;
        }
        Object usage = object.get("usage");
        return usage instanceof JSONObject && (((JSONObject) usage).containsKey("total_tokens")
                || ((JSONObject) usage).containsKey("completion_tokens")
                || ((JSONObject) usage).containsKey("prompt_tokens"));
    }

    private static boolean isBlank(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isSpace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character) || character == '\ufeff';
    }

    private static String trimWhitespace(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }
}
