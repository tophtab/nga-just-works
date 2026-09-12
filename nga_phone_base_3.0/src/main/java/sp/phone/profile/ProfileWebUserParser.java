package sp.phone.profile;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;

import java.util.Locale;

/** Extracts the web UCP page's literal data without executing any JavaScript. */
final class ProfileWebUserParser {

    static final int MAX_NESTING = 48;
    private static final String USER_VARIABLE = "__UCPUSER";

    private ProfileWebUserParser() {
    }

    /** Null means no supported inline assignment; malformed identified data throws. */
    static JSONObject parse(String html) {
        int cursor = 0;
        int templateDepth = 0;
        while ((cursor = html.indexOf('<', cursor)) >= 0) {
            if (html.startsWith("<!--", cursor)) {
                int end = html.indexOf("-->", cursor + 4);
                if (end < 0) return null;
                cursor = end + 3;
                continue;
            }
            boolean closing = html.startsWith("</", cursor);
            int nameStart = cursor + (closing ? 2 : 1);
            if (nameStart >= html.length()) return null;
            char first = html.charAt(nameStart);
            if (!Character.isLetter(first) && first != '!' && first != '?') {
                // A less-than sign in page text does not consume the following real tag.
                cursor++;
                continue;
            }
            int nameEnd = nameStart;
            while (nameEnd < html.length() && !isTagBoundary(html.charAt(nameEnd))) {
                nameEnd++;
            }
            int end = tagEnd(html, nameEnd);
            if (end < 0) return null;
            String name = html.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);
            cursor = end + 1;
            if (name.equals("template")) {
                templateDepth = closing ? Math.max(0, templateDepth - 1) : templateDepth + 1;
            }
            if (closing) continue;
            if (name.equals("plaintext")) return null;
            switch (name) {
                case "script":
                case "style":
                case "textarea":
                case "title":
                case "xmp":
                case "iframe":
                case "noembed":
                case "noframes":
                case "noscript":
                    int close = closingTag(html, name, cursor);
                    int scriptEnd = close < 0 ? html.length() : close;
                    if (name.equals("script") && templateDepth == 0
                            && isInlineJavaScript(html, nameEnd, end)) {
                        String json = userObject(html, cursor, scriptEnd);
                        if (json != null) {
                            if (close < 0) throw malformedData();
                            int features = Feature.AutoCloseSource.mask | Feature.DisableSpecialKeyDetect.mask
                                    | Feature.UseBigDecimal.mask;
                            Object decoded = JSON.parse(json, new ParserConfig(), features);
                            if (!(decoded instanceof JSONObject)) throw malformedData();
                            return (JSONObject) decoded;
                        }
                    }
                    cursor = scriptEnd;
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    private static int tagEnd(String source, int start) {
        char quote = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int closingTag(String source, String name, int start) {
        int cursor = start;
        while ((cursor = source.indexOf("</", cursor)) >= 0) {
            int afterName = cursor + 2 + name.length();
            if (source.regionMatches(true, cursor + 2, name, 0, name.length())
                    && afterName < source.length() && isTagBoundary(source.charAt(afterName))) {
                return cursor;
            }
            cursor += 2;
        }
        return -1;
    }

    private static boolean isTagBoundary(char c) {
        return Character.isWhitespace(c) || c == '>' || c == '/';
    }

    private static boolean isInlineJavaScript(String html, int start, int end) {
        for (int i = start; i < end; ) {
            if (isTagBoundary(html.charAt(i))) {
                i++;
                continue;
            }
            int nameStart = i;
            while (i < end && !isTagBoundary(html.charAt(i)) && html.charAt(i) != '=') i++;
            String name = html.substring(nameStart, i);
            while (i < end && Character.isWhitespace(html.charAt(i))) i++;
            String value = "";
            if (i < end && html.charAt(i) == '=') {
                i++;
                while (i < end && Character.isWhitespace(html.charAt(i))) i++;
                if (i < end && (html.charAt(i) == '"' || html.charAt(i) == '\'')) {
                    char quote = html.charAt(i++);
                    int valueStart = i;
                    while (i < end && html.charAt(i) != quote) i++;
                    value = html.substring(valueStart, i);
                    i++;
                } else {
                    int valueStart = i;
                    while (i < end && !Character.isWhitespace(html.charAt(i))) i++;
                    value = html.substring(valueStart, i);
                }
            }
            if (name.equalsIgnoreCase("src")) return false;
            if (name.equalsIgnoreCase("type") && !value.isEmpty()
                    && !value.equalsIgnoreCase("text/javascript")
                    && !value.equalsIgnoreCase("application/javascript")
                    && !value.equalsIgnoreCase("text/ecmascript")
                    && !value.equalsIgnoreCase("application/ecmascript")
                    && !value.equalsIgnoreCase("module")) {
                return false;
            }
        }
        return true;
    }

    private static String userObject(String source, int start, int end) {
        boolean statementStart = true;
        boolean expressionStart = true;
        boolean canEndStatement = false;
        for (int i = start; i < end; ) {
            int next = skipTrivia(source, i, end);
            if (canEndStatement && hasLineBreak(source, i, next)) statementStart = true;
            i = next;
            if (i >= end) break;
            char c = source.charAt(i);
            if (c == '"' || c == '\'' || c == 0x60) {
                i = c == 0x60 ? templateEnd(source, i, end, 1) : quotedEnd(source, i, end);
                statementStart = false;
                expressionStart = false;
                canEndStatement = true;
            } else if (c == '/' && (expressionStart || statementStart)) {
                i = regexEnd(source, i, end);
                statementStart = false;
                expressionStart = false;
                canEndStatement = true;
            } else if (Character.isJavaIdentifierPart(c)) {
                int tokenStart = i++;
                while (i < end && Character.isJavaIdentifierPart(source.charAt(i))) i++;
                String token = source.substring(tokenStart, i);
                if (statementStart && USER_VARIABLE.equals(token)) {
                    int equals = skipTrivia(source, i, end);
                    if (equals < end && source.charAt(equals) == '='
                            && (equals + 1 >= end || (source.charAt(equals + 1) != '='
                            && source.charAt(equals + 1) != '>'))) {
                        int objectStart = skipTrivia(source, equals + 1, end);
                        int objectEnd = objectEnd(source, objectStart, end);
                        int after = skipTrivia(source, objectEnd, end);
                        if (after < end && source.charAt(after) != ';' && source.charAt(after) != ',') {
                            throw malformedData();
                        }
                        return source.substring(objectStart, objectEnd);
                    }
                }
                statementStart = token.equals("var") || token.equals("let") || token.equals("const");
                expressionStart = statementStart || expressionKeyword(token);
                canEndStatement = !expressionStart;
            } else {
                i++;
                statementStart = c == ';';
                expressionStart = c != ']' && c != '.';
                canEndStatement = c == ')' || c == ']' || c == '}';
            }
        }
        return null;
    }

    private static boolean expressionKeyword(String token) {
        return token.equals("return") || token.equals("throw") || token.equals("case")
                || token.equals("typeof") || token.equals("void") || token.equals("delete")
                || token.equals("new") || token.equals("yield") || token.equals("await")
                || token.equals("in") || token.equals("instanceof")
                || token.equals("else") || token.equals("do");
    }

    private static boolean hasLineBreak(String source, int start, int end) {
        for (int i = start; i < end; i++) {
            if (isLineBreak(source.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r' || c == 0x2028 || c == 0x2029;
    }

    private static int skipTrivia(String source, int start, int end) {
        int i = start;
        while (i < end) {
            if (Character.isWhitespace(source.charAt(i))) {
                i++;
            } else if (source.startsWith("/*", i)) {
                int close = source.indexOf("*/", i + 2);
                i = close < 0 || close >= end ? end : close + 2;
            } else if (source.startsWith("//", i) || source.startsWith("<!--", i)
                    || source.startsWith("-->", i)) {
                while (i < end && !isLineBreak(source.charAt(i))) i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static int quotedEnd(String source, int start, int end) {
        char quote = source.charAt(start);
        for (int i = start + 1; i < end; i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            }
        }
        return end;
    }

    /** Interpolations can contain nested templates; none of their text is profile data. */
    private static int templateEnd(String source, int start, int end, int nesting) {
        int braces = 0;
        boolean expressionStart = true;
        for (int i = start + 1; i < end; i++) {
            if (braces != 0) {
                i = skipTrivia(source, i, end);
                if (i >= end) break;
            }
            char c = source.charAt(i);
            if (braces == 0) {
                if (c == '\\') {
                    i++;
                } else if (c == 0x60) {
                    return i + 1;
                } else if (c == '$' && i + 1 < end && source.charAt(i + 1) == '{') {
                    braces = 1;
                    expressionStart = true;
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                i = quotedEnd(source, i, end) - 1;
                expressionStart = false;
            } else if (c == 0x60) {
                if (nesting + braces >= MAX_NESTING) return end;
                i = templateEnd(source, i, end, nesting + braces) - 1;
                expressionStart = false;
            } else if (c == '/' && expressionStart) {
                i = regexEnd(source, i, end) - 1;
                expressionStart = false;
            } else if (Character.isJavaIdentifierPart(c)) {
                int tokenStart = i++;
                while (i < end && Character.isJavaIdentifierPart(source.charAt(i))) i++;
                expressionStart = expressionKeyword(source.substring(tokenStart, i));
                i--;
            } else {
                if (c == '{') braces++;
                if (c == '}') braces--;
                if (nesting + braces > MAX_NESTING) return end;
                expressionStart = c != ']' && c != '.' && c != '}';
            }
        }
        return end;
    }

    private static int regexEnd(String source, int start, int end) {
        boolean characterClass = false;
        for (int i = start + 1; i < end; i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (isLineBreak(c)) {
                return i;
            } else if (c == '[') {
                characterClass = true;
            } else if (c == ']') {
                characterClass = false;
            } else if (c == '/' && !characterClass) {
                return i + 1;
            }
        }
        return end;
    }

    private static int objectEnd(String source, int start, int end) {
        if (start >= end || source.charAt(start) != '{') throw malformedData();
        char[] expected = new char[MAX_NESTING];
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c < 0x20) throw malformedData();
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (depth == MAX_NESTING) throw malformedData();
                expected[depth++] = c == '{' ? '}' : ']';
            } else if (c == '}' || c == ']') {
                if (depth == 0 || expected[--depth] != c) throw malformedData();
                if (depth == 0) return i + 1;
            } else if (c == '\'' || c == '/' || c == 0x60) {
                throw malformedData();
            }
        }
        throw malformedData();
    }

    private static IllegalArgumentException malformedData() {
        return new IllegalArgumentException("Malformed web profile data");
    }
}
