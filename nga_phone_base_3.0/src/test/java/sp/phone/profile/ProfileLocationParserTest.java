package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

public class ProfileLocationParserTest {

    @Test
    public void plainAndEstablishedWrappedProfilesYieldOnlyLatestLocation() {
        String profile = "{\"data\":{\"0\":{\"uid\":42,\"username\":\"fixture\",\"ipLoc\":\" 广东 \"}}}";
        for (String source : new String[]{profile, "window.script_muti_get_var_store=" + profile,
                "/*$js$*/" + profile + "/*error fill content fixture"}) {
            ProfileLocationResult result = ProfileLocationParser.parse(source, 42);
            assertEquals(ProfileLocationResult.Kind.SUCCESS, result.kind);
            assertEquals("广东", result.location);
        }
    }

    @Test
    public void legacyNumericRepairsAreSharedWithoutChangingTheEnvelope() {
        JSONObject root = ProfileEnvelopeParser.parse("window.script_muti_get_var_store="
                + "{\"data\":{\"0\":{\"uid\":42,\"content\":+123,\"subject\":+456,"
                + "\"ipLoc\":\"上海\"}}}/*error fill content fixture");
        JSONObject profile = root.getJSONObject("data").getJSONObject("0");
        assertEquals("+123", profile.getString("content"));
        assertEquals("+456", profile.getString("subject"));
        assertEquals("上海", profile.getString("ipLoc"));
    }

    @Test
    public void missingNullAndUnicodeBlankLocationAreValidEmptyObservations() {
        for (String field : new String[]{"", ",\"ipLoc\":null", ",\"ipLoc\":\"\"",
                ",\"ipLoc\":\" \\u3000\\u00a0 \""}) {
            ProfileLocationResult result = ProfileLocationParser.parse(
                    "{\"data\":{\"0\":{\"uid\":\"42\"" + field + "}}}", 42);
            assertEquals(ProfileLocationResult.Kind.SUCCESS, result.kind);
            assertNull(result.location);
        }
    }

    @Test
    public void returnedIdentityMustMatchAndMissingUidStillNeedsAProfileShape() {
        for (String uid : new String[]{"43", "null", "-1", "42.5", "\"42; Cookie=other\""}) {
            assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationParser.parse("{\"data\":{\"0\":{\"uid\":" + uid
                            + ",\"ipLoc\":\"广东\"}}}", 42).kind);
        }
        assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                ProfileLocationParser.parse("{\"data\":{\"0\":{\"ipLoc\":\"广东\"}}}", 42).kind);
        assertEquals(ProfileLocationResult.Kind.SUCCESS,
                ProfileLocationParser.parse("{\"data\":{\"0\":{\"username\":\"fixture\","
                        + "\"posts\":2,\"ipLoc\":\"广东\"}}}", 42).kind);
    }

    @Test
    public void nonProfileSiteMessagesAndHtmlStopTheSession() {
        for (String source : new String[]{"<html><title>验证</title></html>",
                "{\"error\":{\"0\":\"请登录\"}}", "{\"data\":{\"0\":\"站点提示\"}}",
                "{\"data\":{\"0\":{}}}", "{}", "null", "请稍后再试", "[]"}) {
            assertEquals(source, ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationParser.parse(source, 42).kind);
        }
    }

    @Test
    public void malformedResponsesDoNotBecomeNegativeSuccessCacheEntries() {
        for (String source : new String[]{null, "", " ", "{\"data\":"}) {
            assertEquals(ProfileLocationResult.Kind.FAILURE, ProfileLocationParser.parse(source, 42).kind);
        }
        assertEquals(ProfileLocationResult.Kind.FAILURE,
                ProfileLocationParser.parse("{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":[]}}}", 42).kind);
    }

    @Test
    public void locationIsBoundedPlainTextWithoutRawIpsMarkupOrPlaceholders() {
        for (String value : new String[]{"null", "undefined", "N/A", "未知", "-", "192.0.2.1",
                "2001:db8::1", "<b>广东</b>", "广东&amp;上海", "https://example.invalid",
                "广东\n上海", "广东\u2028上海", "广东\u2029上海", "广东\u202e上海", "广".repeat(81)}) {
            String source = "{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":"
                    + JSONObject.toJSONString(value) + "}}}";
            ProfileLocationResult result = ProfileLocationParser.parse(source, 42);
            assertEquals(value, ProfileLocationResult.Kind.FAILURE, result.kind);
            assertNull(result.location);
        }
        assertEquals("中国香港", ProfileLocationParser.parse(
                "{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":\"中国香港\"}}}", 42).location);
    }
}
