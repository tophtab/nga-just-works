package sp.phone.profile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** An immutable request identity. It is never serialized or included in diagnostics. */
public final class ProfileSession {

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "bbs.nga.cn", "bbs.ngacn.cc", "nga.178.com", "ngabbs.com"));

    public final String origin;
    public final String accountUid;
    final String cookie;
    final String userAgent;

    private ProfileSession(String origin, String accountUid, String cookie, String userAgent) {
        this.origin = origin;
        this.accountUid = accountUid;
        this.cookie = cookie;
        this.userAgent = userAgent;
    }

    public static ProfileSession create(String origin, String uid, String cid, String userAgent) {
        String normalized = normalizeOrigin(origin);
        if (normalized == null || !isHeaderText(userAgent, 1024)) {
            return null;
        }
        if (uid == null && cid == null) {
            return new ProfileSession(normalized, "0", "", userAgent);
        }
        if (!isUid(uid, false) || cid == null || cid.isEmpty() || cid.length() > 512) {
            return null;
        }
        for (int i = 0; i < cid.length(); i++) {
            char c = cid.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == ';' || c == ',' || c == '"' || c == '\\') {
                return null;
            }
        }
        return new ProfileSession(normalized, uid,
                "ngaPassportUid=" + uid + "; ngaPassportCid=" + cid, userAgent);
    }

    static String normalizeOrigin(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))) {
                return null;
            }
            return "https://" + host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    static boolean isUid(String uid, boolean guestAllowed) {
        if (guestAllowed && "0".equals(uid)) {
            return true;
        }
        if (uid == null || uid.isEmpty() || uid.length() > 10 || uid.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < uid.length(); i++) {
            if (uid.charAt(i) < '0' || uid.charAt(i) > '9') {
                return false;
            }
        }
        try {
            return Long.parseLong(uid) <= Integer.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isHeaderText(String value, int bound) {
        if (value == null || value.isEmpty() || value.length() > bound) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 0x20 || value.charAt(i) >= 0x7f) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProfileSession)) {
            return false;
        }
        ProfileSession that = (ProfileSession) other;
        return origin.equals(that.origin) && accountUid.equals(that.accountUid)
                && cookie.equals(that.cookie) && userAgent.equals(that.userAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, accountUid, cookie, userAgent);
    }
}
