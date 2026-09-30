package sp.phone.view.editor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.common.util.EmoticonUtils;
import gov.anzong.androidnga.common.util.NgaImageHost;

/** Source offsets are UTF-16 editor offsets. Parsing never rewrites the document. */
public final class InlineMediaSource {
    private static final Pattern TOKEN = Pattern.compile(
            "\\[s:([^:\\[\\]\\s]+):([^\\[\\]\\r\\n]+)]|\\[img]([^\\[\\]]+)\\[/img]",
            Pattern.CASE_INSENSITIVE);

    private InlineMediaSource() { }

    public static final class Token {
        public final int start;
        public final int end;
        public final String source;
        public final String resource;
        public final boolean image;

        Token(int start, int end, String source, String resource, boolean image) {
            this.start = start;
            this.end = end;
            this.source = source;
            this.resource = resource;
            this.image = image;
        }
    }

    public static List<Token> parse(String source, String attachmentsPrefix) {
        List<Token> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(source);
        while (matcher.find()) {
            boolean image = matcher.group(3) != null;
            String resource;
            if (image) {
                resource = imageUrl(matcher.group(3).trim(), attachmentsPrefix);
            } else {
                String asset = EmoticonUtils.resolveAssetPath(matcher.group(1), matcher.group(2));
                resource = asset == null ? null : "file:///android_asset/" + asset;
            }
            if (resource != null) {
                result.add(new Token(matcher.start(), matcher.end(), matcher.group(), resource, image));
            }
        }
        return result;
    }

    static String imageUrl(String value, String prefix) {
        if (value.startsWith("./")) value = prefix + value.substring(1);
        value = NgaImageHost.normalizeLegacyHosts(value, prefix);
        try {
            URI uri = new URI(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || value.indexOf('|') >= 0) return null;
            return value;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    /** Expand only displayed media, not unknown, loading or failed source text. */
    public static int[] expand(int start, int end, List<int[]> displayedRanges) {
        int low = Math.min(start, end);
        int high = Math.max(start, end);
        for (int[] range : displayedRanges) {
            if (low < range[1] && high > range[0]) {
                low = Math.min(low, range[0]);
                high = Math.max(high, range[1]);
            }
        }
        return start <= end ? new int[]{low, high} : new int[]{high, low};
    }

    /** An async delivery belongs to one exact source revision and view lifetime. */
    public static final class Revision {
        private long generation;
        private boolean closed;

        public long changed() { return ++generation; }
        public long current() { return generation; }
        public void close() { closed = true; generation++; }
        public boolean accepts(long expected, Token token, CharSequence source) {
            return !closed && generation == expected && token.end <= source.length()
                    && token.source.contentEquals(source.subSequence(token.start, token.end));
        }
    }
}
