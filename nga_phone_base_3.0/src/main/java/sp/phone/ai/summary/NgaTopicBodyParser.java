package sp.phone.ai.summary;

import static sp.phone.ai.summary.NgaProfilePageSource.IDENTITY_ERROR;
import static sp.phone.ai.summary.NgaProfilePageSource.RESPONSE_ERROR;
import static sp.phone.ai.summary.NgaProfilePageSource.hasUnavailableMarker;
import static sp.phone.ai.summary.NgaProfilePageSource.object;
import static sp.phone.ai.summary.NgaProfilePageSource.positiveId;
import static sp.phone.ai.summary.NgaProfilePageSource.scalar;

import com.alibaba.fastjson.JSONObject;

import java.util.regex.Pattern;

/** Projects only a verified original post; it never invokes the legacy renderer or body logger. */
final class NgaTopicBodyParser {
    private static final String JS_MARKER = "/*$js$*/";
    private static final String ERROR_FILL = "/*error fill content";
    // Ordinary JSON numbers plus the +integer / leading-zero text repaired by the pinned reader.
    private static final Pattern NUMERIC_TEXT = Pattern.compile(
            "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|\\+[0-9]+|0[0-9]+");

    private NgaTopicBodyParser() {
    }

    /** Null means explicitly unavailable; an unmarked missing/malformed original is an error. */
    static String parse(String raw, String uid, String tid) throws NgaProfilePageSource.PageException {
        try {
            JSONObject data = NgaProfilePageSource.parseData(normalize(raw));
            JSONObject topic = object(data.get("__T"));
            JSONObject rows = object(data.get("__R"));
            if (topic == null || rows == null) {
                throw invalid();
            }
            requireIdentity(topic.get("tid"), tid);
            if (data.containsKey("tid")) {
                requireIdentity(data.get("tid"), tid);
            }
            if (topic.containsKey("authorid")) {
                requireIdentity(topic.get("authorid"), uid);
            }

            String body = null;
            int originalCount = 0;
            boolean unavailableOriginal = false;
            for (String key : rows.keySet()) {
                if (!key.matches("[0-9]{1,6}")) {
                    continue;
                }
                JSONObject row = object(rows.get(key));
                if (row == null) {
                    throw invalid();
                }
                String floor = scalar(row.get("lou"));
                if (hasUnavailableMarker(row)) {
                    // A denied placeholder may omit identity. An explicitly later floor cannot
                    // establish that the original is unavailable.
                    if (!row.containsKey("lou") || "0".equals(floor)) {
                        unavailableOriginal = true;
                    }
                    if ("0".equals(floor) && ++originalCount > 1) {
                        throw invalid();
                    }
                    continue;
                }
                if (!floor.matches("0|[1-9][0-9]{0,18}")) {
                    throw invalid();
                }
                if (row.containsKey("tid")) {
                    requireIdentity(row.get("tid"), tid);
                }
                if (!"0".equals(floor)) {
                    continue;
                }
                if (++originalCount > 1) {
                    throw invalid();
                }
                requireIdentity(row.get("tid"), tid);
                requireIdentity(row.get("authorid"), uid);
                Object content = row.get("content");
                if (!(content instanceof String) && !(content instanceof Number)) {
                    throw invalid();
                }
                body = scalar(content);
            }
            if (body != null) {
                return body;
            }
            if (unavailableOriginal) {
                return null;
            }
            throw invalid();
        } catch (NgaProfilePageSource.PageException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            // Raw parser exceptions can contain response text; never retain them as causes.
            throw invalid();
        }
    }

    private static void requireIdentity(Object value, String expected)
            throws NgaProfilePageSource.PageException {
        if (!positiveId(value).equals(expected)) {
            throw new NgaProfilePageSource.PageException(IDENTITY_ERROR);
        }
    }

    /** Source-backed ArticleConvertFactory repairs, applied only outside quoted text. */
    private static String normalize(String raw) throws NgaProfilePageSource.PageException {
        if (raw == null || raw.length() > NgaProfilePageSource.MAX_RESPONSE_BYTES) {
            throw invalid();
        }
        StringBuilder output = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            if (raw.startsWith(JS_MARKER, index)) {
                index += JS_MARKER.length();
            } else if (raw.startsWith(ERROR_FILL, index)) {
                break;
            } else if (raw.charAt(index) == '"') {
                int end = quotedEnd(raw, index);
                String field = raw.substring(index, end + 1);
                output.append(field);
                index = end + 1;
                if ("\"content\"".equals(field) || "\"subject\"".equals(field)) {
                    int colon = skipWhitespace(raw, index);
                    if (colon < raw.length() && raw.charAt(colon) == ':') {
                        int start = skipWhitespace(raw, colon + 1);
                        if (start < raw.length() && isNumberStart(raw.charAt(start))) {
                            int numberEnd = start;
                            while (numberEnd < raw.length() && !isValueEnd(raw.charAt(numberEnd))) {
                                numberEnd++;
                            }
                            String number = raw.substring(start, numberEnd);
                            if (!NUMERIC_TEXT.matcher(number).matches()) {
                                throw invalid();
                            }
                            // Preserve the numeric text verbatim, including +, zeros, and exponent.
                            output.append(raw, index, start).append('"').append(number).append('"');
                            index = numberEnd;
                        }
                    }
                }
            } else {
                output.append(raw.charAt(index++));
            }
            if (output.length() > NgaProfilePageSource.MAX_RESPONSE_BYTES) {
                throw invalid();
            }
        }
        return output.toString();
    }

    private static int quotedEnd(String text, int start) throws NgaProfilePageSource.PageException {
        boolean escaped = false;
        for (int index = start + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return index;
            }
        }
        throw invalid();
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isNumberStart(char character) {
        return character == '+' || character == '-' || character >= '0' && character <= '9';
    }

    private static boolean isValueEnd(char character) {
        return character == ',' || character == '}' || character == ']' || Character.isWhitespace(character);
    }

    private static NgaProfilePageSource.PageException invalid() {
        return new NgaProfilePageSource.PageException(RESPONSE_ERROR);
    }
}
