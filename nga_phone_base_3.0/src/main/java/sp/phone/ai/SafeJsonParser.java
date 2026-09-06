package sp.phone.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;

/** Bounded JSON-object decoding without global parser options, type loading, or reference resolution. */
public final class SafeJsonParser {
    private static final int MAX_JSON_CHARS = 512 * 1024;
    private static final int MAX_JSON_DEPTH = 48;

    private SafeJsonParser() {
    }

    public static JSONObject parseObject(String json) {
        checkNesting(json);
        final Object decoded;
        try {
            int features = Feature.AutoCloseSource.mask | Feature.DisableSpecialKeyDetect.mask
                    | Feature.UseBigDecimal.mask;
            decoded = JSON.parse(json, new ParserConfig(), features);
        } catch (RuntimeException ignored) {
            // Parser exceptions can contain the whole response. Do not retain them as causes.
            throw invalidJson();
        }
        if (!(decoded instanceof JSONObject)) {
            throw invalidJson();
        }
        return (JSONObject) decoded;
    }

    private static void checkNesting(String json) {
        if (json == null || json.isEmpty() || json.length() > MAX_JSON_CHARS) {
            throw invalidJson();
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (inString) {
                if (character < 0x20) {
                    throw invalidJson();
                }
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
            } else if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                if (++depth > MAX_JSON_DEPTH) {
                    throw invalidJson();
                }
            } else if (character == '}' || character == ']') {
                if (--depth < 0) {
                    throw invalidJson();
                }
            } else if (character == '\'' || character == '/') {
                throw invalidJson();
            }
        }
        if (inString || depth != 0) {
            throw invalidJson();
        }
    }

    private static IllegalArgumentException invalidJson() {
        return new IllegalArgumentException("响应内容无法解析");
    }
}
