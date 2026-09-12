package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

public class ProfileLocationParserTest {

    @Test
    public void inlineWebProfilesYieldOnlyTheLatestTopLevelLocation() {
        String user = "{\"uid\":42,\"username\":\"fixture\",\"ipLoc\":\" 广东 \"}";
        for (String source : new String[]{profile(user),
                "<!doctype html><HTML><SCRIPT type='text/javascript' data-label='>'>"
                        + "var __UCPUSER /* fixture */ =\n" + user + ";</SCRIPT></HTML>",
                script("const ignored = 1; let __UCPUSER=" + user + ";")}) {
            ProfileLocationResult result = ProfileLocationParser.parse(source, 42);
            assertEquals(ProfileLocationResult.Kind.SUCCESS, result.kind);
            assertEquals("广东", result.location);
        }
    }

    @Test
    public void nestedValuesQuotedBracesAndEscapedQuotesDoNotTruncateTheUserObject() {
        String description = JSONObject.toJSONString("quoted \" } { slash \\ and trailing \\");
        String user = "{\"uid\":42,\"description\":" + description
                + ",\"nested\":[{\"ipLoc\":\"上海\",\"values\":[1,{},[]]}],\"ipLoc\":\"广东\"}";
        assertEquals("广东", ProfileLocationParser.parse(profile(user), 42).location);
    }

    @Test
    public void markersInCommentsAttributesStringsTemplatesAndRegexesAreNotAssignments() {
        String assignment = "__UCPUSER = {\"uid\":42,\"ipLoc\":\"上海\"};";
        for (String source : new String[]{
                "<!-- " + script(assignment) + " -->",
                "<div title='" + script(assignment) + "'>fixture</div>",
                "<textarea>" + script(assignment) + "</textarea>",
                "<style>" + script(assignment) + "</style>",
                "<template>" + script(assignment) + "</template>",
                "<script-example>" + assignment + "</script-example>",
                "<script src='fixture.js'>" + assignment + "</script>",
                "<script type='application/json'>" + assignment + "</script>",
                script("// " + assignment + "\n"),
                script("/* " + assignment + " */"),
                script("const text = " + JSONObject.toJSONString(assignment) + ";"),
                script("const text = '" + assignment + "';"),
                script("const text = " + (char) 96 + assignment + (char) 96 + ";"),
                script("const template = " + (char) 96 + "outer$" + "{" + (char) 96
                        + "inner ; " + assignment + (char) 96 + "}" + (char) 96 + ";"),
                script("const pattern = /dummy; " + assignment + "/g;"),
                script("const pattern = /[/]; " + assignment + "/;"),
                script("if (ready) /dummy; " + assignment + "/.test('fixture');"),
                script("if (ready) {} /dummy; " + assignment + "/.test('fixture');"),
                script("if (ready) {} else /dummy; " + assignment + "/.test('fixture');"),
                script("not__UCPUSER = {\"uid\":42,\"ipLoc\":\"上海\"};"),
                script("__UCPUSER_extra = {\"uid\":42,\"ipLoc\":\"上海\"};"),
                script("other.__UCPUSER = {\"uid\":42,\"ipLoc\":\"上海\"};"),
                script("__UCPUSER == {\"uid\":42,\"ipLoc\":\"上海\"};")}) {
            assertEquals(source, ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationParser.parse(source, 42).kind);
            // An actual later inline assignment is still found, without consuming a decoy.
            assertEquals("广东", ProfileLocationParser.parse(source
                    + profile("{\"uid\":42,\"ipLoc\":\"广东\"}"), 42).location);
        }
    }

    @Test
    public void scriptTriviaAndUnrelatedStatementsBeforeTheAssignmentAreIgnored() {
        String source = script("<!-- fixture\nconst text = '__UCPUSER = invalid';"
                + "const pattern = /dummy; __UCPUSER = invalid/;"
                + "var half = count / 2;\n// ignored __UCPUSER = invalid\n"
                + "__UCPUSER /* data */ = /* data */ {\"uid\":42,\"ipLoc\":\"广东\"};");
        assertEquals("广东", ProfileLocationParser.parse(source, 42).location);
    }

    @Test
    public void plainLessThanTextAndNewlineSeparatedStatementsDoNotHideTheProfile() {
        String assignment = "__UCPUSER = {\"uid\":42,\"ipLoc\":\"广东\"};";
        assertEquals("广东", ProfileLocationParser.parse(
                "<body>count < 5" + script(assignment) + "</body>", 42).location);
        assertEquals("广东", ProfileLocationParser.parse(
                script("var x = 1\n" + assignment), 42).location);
        assertEquals("广东", ProfileLocationParser.parse(script("const template = " + (char) 96
                + "outer$" + "{" + (char) 96 + "inner" + (char) 96 + "}" + (char) 96
                + ";\n" + assignment), 42).location);
    }

    @Test
    public void manualProfileEnvelopesRetainEstablishedWrappers() {
        String user = "{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":\"广东\"}}}";
        for (String source : new String[]{user, "window.script_muti_get_var_store=" + user,
                "/*$js$*/" + user + "/*error fill content fixture"}) {
            JSONObject parsed = ProfileEnvelopeParser.parse(source);
            assertEquals("广东", parsed.getJSONObject("data").getJSONObject("0").getString("ipLoc"));
        }
    }

    @Test
    public void manualProfileEnvelopesRetainLegacyNumericRepairs() {
        JSONObject root = ProfileEnvelopeParser.parse("window.script_muti_get_var_store="
                + "{\"data\":{\"0\":{\"uid\":42,\"content\":+123,\"subject\":+456,"
                + "\"ipLoc\":\"上海\"}}}/*error fill content fixture");
        JSONObject user = root.getJSONObject("data").getJSONObject("0");
        assertEquals("+123", user.getString("content"));
        assertEquals("+456", user.getString("subject"));
        assertEquals("上海", user.getString("ipLoc"));
    }

    @Test
    public void missingNullAndUnicodeBlankLocationAreValidEmptyObservations() {
        for (String field : new String[]{"", ",\"ipLoc\":null", ",\"ipLoc\":\"\"",
                ",\"ipLoc\":\" \\u3000\\u00a0 \""}) {
            ProfileLocationResult result = ProfileLocationParser.parse(profile("{\"uid\":\"42\"" + field + "}"), 42);
            assertEquals(ProfileLocationResult.Kind.SUCCESS, result.kind);
            assertNull(result.location);
        }
    }

    @Test
    public void returnedIdentityMustMatchAndMissingUidStillNeedsAProfileShape() {
        for (String uid : new String[]{"43", "null", "-1", "42.5", "\"42; Cookie=other\""}) {
            assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationParser.parse(profile("{\"uid\":" + uid + ",\"ipLoc\":\"广东\"}"), 42).kind);
        }
        assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                ProfileLocationParser.parse(profile("{\"ipLoc\":\"广东\"}"), 42).kind);
        for (String discriminator : new String[]{"posts", "group", "regdate"}) {
            assertEquals(ProfileLocationResult.Kind.SUCCESS,
                    ProfileLocationParser.parse(profile("{\"username\":\"fixture\",\""
                            + discriminator + "\":2,\"ipLoc\":\"广东\"}"), 42).kind);
        }
    }

    @Test
    public void nonProfilePagesSiteMessagesAndOldJsonEnvelopesStopTheSession() {
        for (String source : new String[]{"<html><title>验证</title></html>",
                "{\"error\":{\"0\":\"请登录\"}}", "{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":\"广东\"}}}",
                profile("{\"error\":\"请登录\"}"), profile("{}"), "{}", "null", "请稍后再试", "[]"}) {
            assertEquals(source, ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationParser.parse(source, 42).kind);
        }
    }

    @Test
    public void malformedAssignmentsAndJsonDoNotBecomeNegativeSuccessCacheEntries() {
        for (String source : new String[]{null, "", " ", script("__UCPUSER ="),
                script("__UCPUSER = {\"uid\":"), profile("{\"uid\":42,\"ipLoc\":[}"),
                profile("{\"uid\":42,\"ipLoc\":[]}"), profile("{uid:42,ipLoc:'广东'}"),
                script("__UCPUSER = null;"), script("__UCPUSER = [] ;"),
                script("__UCPUSER = {\"uid\":42,\"ipLoc\":\"广东\"}.another;"),
                "<script>__UCPUSER = {\"uid\":42,\"ipLoc\":\"广东\"};"}) {
            assertEquals(source, ProfileLocationResult.Kind.FAILURE,
                    ProfileLocationParser.parse(source, 42).kind);
        }
    }

    @Test
    public void containerNestingIsBoundedBeforeJsonParsing() {
        String atLimit = "{\"uid\":42,\"nested\":" + "[".repeat(47) + "0" + "]".repeat(47)
                + ",\"ipLoc\":\"广东\"}";
        assertEquals("广东", ProfileLocationParser.parse(profile(atLimit), 42).location);
        String tooDeep = "{\"uid\":42,\"nested\":" + "[".repeat(48) + "0" + "]".repeat(48) + "}";
        assertEquals(ProfileLocationResult.Kind.FAILURE, ProfileLocationParser.parse(profile(tooDeep), 42).kind);
    }

    @Test
    public void jsonSpecialKeysRemainDataAndCannotResolveTheLocationThroughReferences() {
        assertEquals("广东", ProfileLocationParser.parse(profile("{\"@type\":\"fixture.Type\","
                + "\"uid\":42,\"ipLoc\":\"广东\"}"), 42).location);
        assertEquals(ProfileLocationResult.Kind.FAILURE, ProfileLocationParser.parse(profile(
                "{\"uid\":42,\"value\":\"广东\",\"ipLoc\":{\"$ref\":\"$.value\"}}"), 42).kind);
    }

    @Test
    public void locationIsBoundedPlainTextWithoutRawIpsMarkupOrPlaceholders() {
        for (String value : new String[]{"null", "undefined", "N/A", "未知", "-", "192.0.2.1",
                "2001:db8::1", "<b>广东</b>", "广东&amp;上海", "https://example.invalid",
                "广东\n上海", "广东\u2028上海", "广东\u2029上海", "广东\u202e上海", "广".repeat(81)}) {
            String source = profile("{\"uid\":42,\"ipLoc\":" + JSONObject.toJSONString(value) + "}");
            ProfileLocationResult result = ProfileLocationParser.parse(source, 42);
            assertEquals(value, ProfileLocationResult.Kind.FAILURE, result.kind);
            assertNull(result.location);
        }
        assertEquals("中国香港", ProfileLocationParser.parse(profile("{\"uid\":42,\"ipLoc\":\"中国香港\"}"), 42).location);
    }

    private static String profile(String user) {
        return "<!doctype html><html><head><title>Fixture profile</title></head><body>"
                + script("__UCPUSER = " + user + ";") + "</body></html>";
    }

    private static String script(String code) {
        return "<script>" + code + "</script>";
    }
}
