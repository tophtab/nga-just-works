package sp.phone.param;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Converts supported external links to THREAD.PAGE identities, never to a remote URL. */
public final class ArticleLinkParser {
    private ArticleLinkParser() { }

    public static ArticleListParam parse(String link) {
        if (link == null) return null;
        try {
            URI uri = new URI(link);
            boolean custom = "nga".equals(uri.getScheme());
            String type = uri.getRawAuthority();
            if (custom) {
                if (!("openType=2".equals(type) || "openType=5".equals(type))
                        || !"".equals(uri.getRawPath()) || uri.getRawFragment() != null) return null;
            } else {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (!("https".equals(scheme) || "http".equals(scheme))
                        || !("nga.178.com".equals(host) || "bbs.ngacn.cc".equals(host)
                        || "bbs.nga.cn".equals(host) || "ngabbs.com".equals(host))
                        || uri.getRawUserInfo() != null
                        || (uri.getPort() != -1 && uri.getPort() != ("https".equals(scheme) ? 443 : 80))
                        || !"/read.php".equals(uri.getRawPath())) return null;
            }
            Map<String, String> fields = new HashMap<>();
            String query = uri.getRawQuery();
            if (query == null) return null;
            for (String part : query.split("&")) {
                if (part.isEmpty()) continue;
                int equals = part.indexOf('=');
                String key = decode(equals < 0 ? part : part.substring(0, equals));
                if (!("tid".equals(key) || "pid".equals(key) || "page".equals(key)
                        || (!custom && ("authorid".equals(key) || "searchpost".equals(key))))) continue;
                if (equals < 0) return null;
                String value = decode(part.substring(equals + 1));
                String previous = fields.put(key, value);
                if (previous != null && !previous.equals(value)) return null;
            }
            ArticleListParam param = new ArticleListParam();
            param.tid = number(fields, "tid", 0, false);
            param.pid = number(fields, "pid", 0, false);
            param.page = number(fields, "page", 1, true);
            if (custom) {
                if (("openType=2".equals(type) && param.tid <= 0)
                        || ("openType=5".equals(type) && param.pid <= 0)) return null;
            } else {
                param.authorId = number(fields, "authorid", 0, false);
                param.searchPost = number(fields, "searchpost", 0, false);
                if (param.tid <= 0 && param.pid <= 0) return null;
            }
            return param;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int number(Map<String, String> fields, String key, int fallback, boolean positive) {
        String value = fields.get(key);
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
        int number = Integer.parseInt(value);
        if (positive && number == 0) throw new IllegalArgumentException();
        return number;
    }
}
