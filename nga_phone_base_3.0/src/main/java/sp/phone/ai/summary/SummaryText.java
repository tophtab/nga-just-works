package sp.phone.ai.summary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sp.phone.util.StringHelper;

/** Text-only extraction. It never renders HTML, resolves a URL, or reads another floor. */
final class SummaryText {

    private static final int MAX_SOURCE_CHARS = 64000;
    private static final Pattern HIDDEN_HTML = Pattern.compile(
            "(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>|<!--.*?-->");
    private static final Pattern MEDIA = Pattern.compile(
            "(?is)\\[(img|flash|audio|video|attach)(?:[=\\s][^\\]]*)?].*?\\[/\\1]");
    private static final Pattern TAGS = Pattern.compile(
            "(?i)\\[/?(?:b|i|u|s|del|h|h1|h2|size|font|color|align|center|left|right|url|uid|pid|tid|fid|code|collapse|list|li|table|tr|td)(?:[=\\s][^\\]]*)?]");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    private SummaryText() {
    }

    static String plain(String source, int limit) {
        String text = limit(source, MAX_SOURCE_CHARS);
        text = HIDDEN_HTML.matcher(text).replaceAll("");
        text = text.replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|tr|h[1-6])\\s*>", "\n");
        text = text.replaceAll("(?s)</?[a-zA-Z][^>]*>", "");
        text = MEDIA.matcher(text).replaceAll("[媒体]");
        text = text.replaceAll("(?i)\\[quote(?:=[^\\]]*)?]", "\n引用：\n")
                .replaceAll("(?i)\\[/quote]", "\n引用结束\n")
                .replaceAll("(?i)\\[s:[^\\]]*]", "[表情]");
        text = TAGS.matcher(text).replaceAll("");
        text = StringHelper.unescapeHTML(decodeNumericEntities(text));
        text = text.replace('\u00a0', ' ').replace('\u3000', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n").trim();
        return limit(text, limit);
    }

    static String limit(String value, int maximum) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String decodeNumericEntities(String text) {
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group(1);
            String replacement = matcher.group();
            try {
                int value = digits.startsWith("x")
                        ? Integer.parseInt(digits.substring(1), 16)
                        : Integer.parseInt(digits, 10);
                if (Character.isValidCodePoint(value)
                        && !(value >= Character.MIN_SURROGATE && value <= Character.MAX_SURROGATE)) {
                    replacement = new String(Character.toChars(value));
                }
            } catch (NumberFormatException ignored) {
                // Preserve an invalid entity as text; never include its source in diagnostics.
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
