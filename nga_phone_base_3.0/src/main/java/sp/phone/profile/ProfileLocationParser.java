package sp.phone.profile;

import com.alibaba.fastjson.JSONObject;

import java.util.Locale;

/** Reads only the latest public profile location; never a historical floor location. */
public final class ProfileLocationParser {

    static final int MAX_LOCATION_LENGTH = 80;

    private ProfileLocationParser() {
    }

    public static ProfileLocationResult parse(String source, int requestedUid) {
        if (source == null || source.trim().isEmpty() || requestedUid <= 0) {
            return ProfileLocationResult.failure();
        }
        // HTML/login/challenge pages are not profile envelopes, even with HTTP 200.
        if (source.trim().startsWith("<")) {
            return ProfileLocationResult.rejected();
        }
        final JSONObject envelope;
        try {
            envelope = ProfileEnvelopeParser.parse(source);
        } catch (ProfileEnvelopeParser.NonProfileResponseException ignored) {
            return ProfileLocationResult.rejected();
        } catch (RuntimeException ignored) {
            return ProfileLocationResult.failure();
        }
        if (envelope == null || envelope.containsKey("error")) {
            return ProfileLocationResult.rejected();
        }
        Object data = envelope.get("data");
        Object profile = data instanceof JSONObject ? ((JSONObject) data).get("0") : null;
        if (!(profile instanceof JSONObject)) {
            return ProfileLocationResult.rejected();
        }
        JSONObject user = (JSONObject) profile;
        if (user.containsKey("uid")) {
            Object uid = user.get("uid");
            if (!(uid instanceof String || uid instanceof Number)
                    || !String.valueOf(requestedUid).equals(String.valueOf(uid))) {
                return ProfileLocationResult.rejected();
            }
        } else if (!(user.get("username") instanceof String)
                || ((String) user.get("username")).trim().isEmpty()
                || !(user.containsKey("posts") || user.containsKey("group")
                || user.containsKey("regdate"))) {
            return ProfileLocationResult.rejected();
        }

        Object raw = user.get("ipLoc");
        if (raw == null) {
            return ProfileLocationResult.success(null);
        }
        if (!(raw instanceof String)) {
            return ProfileLocationResult.failure();
        }
        String location = stripSpaces((String) raw);
        if (location.isEmpty()) {
            return ProfileLocationResult.success(null);
        }
        return isDisplayableLocation(location) ? ProfileLocationResult.success(location)
                : ProfileLocationResult.failure();
    }

    static boolean isDisplayableLocation(String location) {
        if (location == null || location.isEmpty()
                || location.codePointCount(0, location.length()) > MAX_LOCATION_LENGTH) {
            return false;
        }
        switch (location.toLowerCase(Locale.ROOT)) {
            case "null":
            case "undefined":
            case "unknown":
            case "n/a":
            case "未知":
            case "暂无":
            case "不详":
            case "-":
            case "--":
                return false;
            default:
                break;
        }
        boolean hasLetter = false;
        for (int offset = 0; offset < location.length(); ) {
            int cp = location.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                hasLetter = true;
            } else if (cp == 0x2028 || cp == 0x2029) {
                return false;
            } else if (!Character.isSpaceChar(cp)
                    && Character.getType(cp) != Character.NON_SPACING_MARK
                    && Character.getType(cp) != Character.COMBINING_SPACING_MARK
                    && cp != '-' && cp != '·' && cp != '\'' && cp != '’'
                    && cp != '(' && cp != ')' && cp != '（' && cp != '）'
                    && cp != ',' && cp != '，' && cp != '、' && cp != '.') {
                // Also excludes raw IPv4/IPv6, HTML/entities, URLs and controls.
                return false;
            }
        }
        return hasLetter;
    }

    private static String stripSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = value.codePointBefore(end);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return value.substring(start, end);
    }
}
