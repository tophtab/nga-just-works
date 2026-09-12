package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

public class NgaProfilePageSourceTest {

    @Test
    public void requestsUseTheViewedUidPageOneAndAFrozenSession() {
        Request topics = NgaProfilePageSource.buildRequest("https://bbs.nga.cn", "synthetic-session",
                "synthetic-user-agent", "4200", ProfileSummaryLoader.Kind.TOPICS);
        Request replies = NgaProfilePageSource.buildRequest("https://bbs.nga.cn", "synthetic-session",
                "synthetic-user-agent", "4200", ProfileSummaryLoader.Kind.REPLIES);
        Request detail = NgaProfilePageSource.buildTopicRequest("https://bbs.nga.cn", "synthetic-session",
                "synthetic-user-agent", "7300");
        assertEquals("/thread.php", topics.url().encodedPath());
        assertEquals("4200", topics.url().queryParameter("authorid"));
        assertEquals("4200", replies.url().queryParameter("authorid"));
        assertEquals("1", topics.url().queryParameter("page"));
        assertEquals("1", replies.url().queryParameter("page"));
        assertNull(topics.url().queryParameter("searchpost"));
        assertEquals("1", replies.url().queryParameter("searchpost"));
        assertEquals("js", replies.url().queryParameter("lite"));
        assertEquals("synthetic-session", replies.header("Cookie"));
        assertEquals("Nga_Official", replies.header("X-User-Agent"));
        assertNull(replies.header("Authorization"));
        assertEquals("/read.php?page=1&__output=8&noprefix&v2&tid=7300", detail.url().encodedPath()
                + "?" + detail.url().encodedQuery());
        assertNull(detail.url().queryParameter("pid"));
        assertNull(detail.url().queryParameter("authorid"));
        assertEquals("synthetic-session", detail.header("Cookie"));
        assertEquals("synthetic-user-agent", detail.header("User-Agent"));
        assertEquals("Nga_Official", detail.header("X-User-Agent"));
        assertNull(detail.header("Authorization"));
    }

    @Test
    public void untrustedOriginsAndInvalidTargetsNeverCreateATransportCall() {
        for (String domain : new String[]{"http://bbs.nga.cn", "https://bbs.nga.cn.evil.example",
                "https://user@bbs.nga.cn", "https://bbs.nga.cn:8443", "https://127.0.0.1",
                "https://bbs.nga.cn/unexpected", "https://bbs.nga.cn?token=synthetic"}) {
            PageResult result = new PageResult();
            NgaProfilePageSource source = new NgaProfilePageSource(domain, "synthetic-session", "UA",
                    request -> { throw new AssertionError("Invalid origin created a call"); });
            source.loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
            assertNotNull(result.error);
            assertThrows(IllegalArgumentException.class,
                    () -> NgaProfilePageSource.buildTopicRequest(domain, "", "UA", "7300"));
        }
        assertThrows(IllegalArgumentException.class, () -> NgaProfilePageSource.buildRequest(
                "https://bbs.nga.cn", "", "UA", "42&authorid=99", ProfileSummaryLoader.Kind.REPLIES));
    }

    @Test
    public void replyContentComesFromTheMatchingPObjectAndNeverFromTopicOwnerOrSessionData()
            throws Exception {
        JSONObject row = row("999", false);
        JSONObject reply = new JSONObject();
        reply.put("authorid", "42");
        reply.put("postdate", 1767225600);
        reply.put("content", "[b]Viewed user's reply[/b]<br/>Second line");
        row.put("__P", reply);
        String raw = document(row);
        ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(raw, "42",
                ProfileSummaryLoader.Kind.REPLIES);
        ProfileSummaryInput input = new ProfileSummaryInput("42", "Viewed user",
                Collections.emptyList(), page.entries);
        String prompt = input.toPrompt();
        assertTrue(prompt.contains("Viewed user's reply\nSecond line"));
        assertTrue(prompt.contains("Synthetic board"));
        assertTrue(prompt.contains(Instant.ofEpochSecond(1767225600).atZone(ZoneId.systemDefault())
                .toLocalDate().toString()));
        assertFalse(prompt.contains("COOKIE_SENTINEL"));
        assertFalse(prompt.contains("TOPIC_BODY_SENTINEL"));
        assertFalse(prompt.contains("PROFILE_EMAIL_SENTINEL"));
        assertEquals("42", page.uid);
    }

    @Test
    public void matchingTopicsAndWrappedResponsesAreAcceptedButOtherAuthorsAreRejected() throws Exception {
        ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(
                "window.script_muti_get_var_store=" + document(row("42", false)) + ";", "42",
                ProfileSummaryLoader.Kind.TOPICS);
        assertEquals(1, page.entries.size());
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                document(row("99", false)), "42", ProfileSummaryLoader.Kind.TOPICS));
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                document(row("99", true)), "42", ProfileSummaryLoader.Kind.REPLIES));
    }

    @Test
    public void unavailableTopicsDoNotHideVisibleTopicsBeforeOrAfterThem() throws Exception {
        JSONObject first = row("42", false);
        first.put("subject", "First visible topic");
        JSONObject denied = row("99", false);
        denied.put("denied", "DENIAL_MESSAGE_SENTINEL");
        denied.put("subject", "UNAVAILABLE_TOPIC_SENTINEL");
        JSONObject errored = row("99", false);
        errored.put("error", "DENIAL_MESSAGE_SENTINEL");
        errored.put("subject", "UNAVAILABLE_TOPIC_SENTINEL");
        JSONObject last = row("42", false);
        last.put("subject", "Last visible topic");

        ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(
                document(first, denied, errored, last), "42", ProfileSummaryLoader.Kind.TOPICS);
        assertEquals(2, page.entries.size());
        String prompt = prompt(page);
        assertTrue(prompt.contains("First visible topic"));
        assertTrue(prompt.contains("Last visible topic"));
        assertFalse(prompt.contains("UNAVAILABLE_TOPIC_SENTINEL"));
        assertFalse(prompt.contains("DENIAL_MESSAGE_SENTINEL"));
    }

    @Test
    public void unavailableReplyRowsAndNestedBodiesCannotEnterThePrompt() throws Exception {
        for (String marker : new String[]{"denied", "error"}) {
            JSONObject outer = row("99", true);
            outer.put(marker, "DENIAL_MESSAGE_SENTINEL");
            outer.getJSONObject("__P").put("content", "UNAVAILABLE_OUTER_REPLY_SENTINEL");
            JSONObject nested = row("42", true);
            nested.getJSONObject("__P").put(marker, "DENIAL_MESSAGE_SENTINEL");
            nested.getJSONObject("__P").put("content", "UNAVAILABLE_NESTED_REPLY_SENTINEL");
            JSONObject visible = row("42", true);
            visible.getJSONObject("__P").put("content", "Visible reply after unavailable entries");

            ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(
                    document(outer, nested, visible), "42", ProfileSummaryLoader.Kind.REPLIES);
            assertEquals(1, page.entries.size());
            assertEquals("Visible reply after unavailable entries", page.entries.get(0).getReply());
            String prompt = prompt(page);
            assertFalse(prompt.contains("UNAVAILABLE_OUTER_REPLY_SENTINEL"));
            assertFalse(prompt.contains("UNAVAILABLE_NESTED_REPLY_SENTINEL"));
            assertFalse(prompt.contains("DENIAL_MESSAGE_SENTINEL"));
        }
    }

    @Test
    public void unmarkedForeignOrMalformedRecordsStillFailAfterUnavailableRows() {
        JSONObject unavailable = new JSONObject();
        unavailable.put("denied", "Unavailable synthetic record");
        for (ProfileSummaryLoader.Kind kind : ProfileSummaryLoader.Kind.values()) {
            boolean reply = kind == ProfileSummaryLoader.Kind.REPLIES;
            assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                    document(unavailable, row("99", reply)), "42", kind));
            JSONObject malformed = row("42", reply);
            malformed.remove(reply ? "__P" : "authorid");
            assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                    document(unavailable, malformed), "42", kind));
        }
    }

    @Test
    public void blankAndNonStringMarkersRetainNormalAuthorAndContentValidation() throws Exception {
        Object[] values = {null, "", " \t\r\n", true, false, 0, 1,
                new JSONObject(), Collections.singletonList("Unavailable synthetic record")};
        for (String marker : new String[]{"denied", "error"}) {
            for (Object value : values) {
                for (ProfileSummaryLoader.Kind kind : ProfileSummaryLoader.Kind.values()) {
                    boolean reply = kind == ProfileSummaryLoader.Kind.REPLIES;
                    JSONObject row = row("42", reply);
                    row.put(marker, value);
                    JSONObject authored = reply ? row.getJSONObject("__P") : row;
                    authored.put(marker, value);
                    assertEquals(1, NgaProfilePageSource.parsePage(document(row), "42", kind)
                            .entries.size());

                    authored.put("authorid", "99");
                    assertThrows(NgaProfilePageSource.PageException.class,
                            () -> NgaProfilePageSource.parsePage(document(row), "42", kind));
                    authored.put("authorid", "42");
                    authored.remove(reply ? "content" : "subject");
                    assertThrows(NgaProfilePageSource.PageException.class,
                            () -> NgaProfilePageSource.parsePage(document(row), "42", kind));
                }
            }
        }
    }

    @Test
    public void allUnavailablePagesContributeNoActivityEvenWithoutUsableAuthorOrBody() throws Exception {
        JSONObject denied = new JSONObject();
        denied.put("denied", "Unavailable synthetic record");
        JSONObject errored = new JSONObject();
        errored.put("error", "Unavailable synthetic record");
        JSONObject nested = new JSONObject();
        nested.put("__P", errored);
        ProfileSummaryLoader.Page topics = NgaProfilePageSource.parsePage(
                document(denied, errored), "42", ProfileSummaryLoader.Kind.TOPICS);
        ProfileSummaryLoader.Page replies = NgaProfilePageSource.parsePage(
                document(denied, nested), "42", ProfileSummaryLoader.Kind.REPLIES);
        assertTrue(topics.entries.isEmpty());
        assertTrue(replies.entries.isEmpty());
    }

    @Test
    public void unavailableRecordsDoNotConsumeTheTwentyVisibleEntryLimit() throws Exception {
        int unavailableCount = 25;
        int visibleCount = 22;
        for (ProfileSummaryLoader.Kind kind : ProfileSummaryLoader.Kind.values()) {
            boolean reply = kind == ProfileSummaryLoader.Kind.REPLIES;
            JSONObject[] rows = new JSONObject[unavailableCount + visibleCount];
            for (int i = 0; i < unavailableCount; i++) {
                rows[i] = row("99", reply);
                JSONObject markerOwner = reply && i % 2 == 0 ? rows[i].getJSONObject("__P") : rows[i];
                markerOwner.put("denied", "Unavailable synthetic record");
            }
            for (int i = 0; i < visibleCount; i++) {
                rows[unavailableCount + i] = row("42", reply);
                rows[unavailableCount + i].put("subject", "Visible topic " + i);
            }
            ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(
                    document(rows), "42", kind);
            assertEquals(20, page.entries.size());
            String prompt = prompt(page);
            assertTrue(prompt.contains("Visible topic 0 |"));
            assertTrue(prompt.contains("Visible topic 19 |"));
            assertFalse(prompt.contains("Visible topic 20 |"));
            assertFalse(prompt.contains("Visible topic 21 |"));
        }
    }

    @Test
    public void wholePageRejectionsStillFailWhenEveryRecordIsUnavailable() {
        JSONObject unavailable = new JSONObject();
        unavailable.put("denied", "Unavailable synthetic record");
        for (String marker : new String[]{"error", "__MESSAGE"}) {
            JSONObject root = JSON.parseObject(document(unavailable));
            JSONObject markerOwner = "error".equals(marker) ? root : root.getJSONObject("data");
            markerOwner.put(marker, "WHOLE_PAGE_DENIAL_SENTINEL");
            for (ProfileSummaryLoader.Kind kind : ProfileSummaryLoader.Kind.values()) {
                NgaProfilePageSource.PageException error = assertThrows(NgaProfilePageSource.PageException.class,
                        () -> NgaProfilePageSource.parsePage(JSON.toJSONString(root), "42", kind));
                assertFalse(error.getMessage().contains("WHOLE_PAGE_DENIAL_SENTINEL"));
                assertNull(error.getCause());
            }
        }
    }

    @Test
    public void missingReplyBodyCannotSilentlyTurnIntoATitleOnlySummary() {
        JSONObject row = row("42", true);
        row.getJSONObject("__P").remove("content");
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                document(row), "42", ProfileSummaryLoader.Kind.REPLIES));
    }

    @Test
    public void malformedChallengeAndOversizedDocumentsHaveOnlySafeErrors() {
        String[] invalid = {"[]", "not-json-RAW_SENTINEL", "<html>RAW_SENTINEL</html>",
                "{\"error\":[\"RAW_SENTINEL\"]}", "{\"data\":{}}",
                SummaryInputTest.repeat(' ', NgaProfilePageSource.MAX_RESPONSE_BYTES + 1)};
        for (String raw : invalid) {
            NgaProfilePageSource.PageException error = assertThrows(NgaProfilePageSource.PageException.class,
                    () -> NgaProfilePageSource.parsePage(raw, "42", ProfileSummaryLoader.Kind.TOPICS));
            assertFalse(error.getMessage().contains("RAW_SENTINEL"));
            assertNull(error.getCause());
        }
    }

    @Test
    public void excessiveNestingInEitherTheDocumentOrStringParentIsRejected() {
        String deep = SummaryInputTest.repeat('[', 70) + "0" + SummaryInputTest.repeat(']', 70);
        String raw = "{\"data\":" + deep + "}";
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                raw, "42", ProfileSummaryLoader.Kind.TOPICS));
        JSONObject row = row("42", false);
        row.put("parent", "{\"2\":\"Board\",\"extra\":" + deep + "}");
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                document(row), "42", ProfileSummaryLoader.Kind.TOPICS));
    }

    @Test
    public void anExplicitEmptyFirstPageIsSuccessfulData() throws Exception {
        ProfileSummaryLoader.Page page = NgaProfilePageSource.parsePage(
                "{\"data\":{\"__T\":{},\"__T__ROWS\":0}}", "42", ProfileSummaryLoader.Kind.TOPICS);
        assertTrue(page.entries.isEmpty());
    }

    @Test
    public void activityDatesRespectTheDisplayZoneAcrossMidnight() {
        long timestamp = Instant.parse("2026-01-01T16:30:00Z").getEpochSecond();
        assertEquals("2026-01-02", NgaProfilePageSource.date(timestamp, ZoneId.of("Asia/Shanghai")));
        assertEquals("2026-01-01", NgaProfilePageSource.date(timestamp, ZoneId.of("UTC")));
    }

    @Test
    public void typeKeysAreOrdinaryDataAndReferencesCannotSupplyAReplyBody() throws Exception {
        JSONObject row = row("42", false);
        row.put("@type", "java.lang.AutoCloseable");
        assertEquals(1, NgaProfilePageSource.parsePage(document(row), "42",
                ProfileSummaryLoader.Kind.TOPICS).entries.size());
        JSONObject ref = new JSONObject();
        ref.put("$ref", "$.data.__T.0");
        row.put("__P", ref);
        assertThrows(NgaProfilePageSource.PageException.class, () -> NgaProfilePageSource.parsePage(
                document(row), "42", ProfileSummaryLoader.Kind.REPLIES));
    }

    @Test
    public void invalidTopicIdsFailBeforeAnyDetailRequest() {
        Object[] invalidIds = {null, "", 0, -1, "07300", "+7300", "7300&pid=1", "1e3",
                "12345678901234567890", true, new JSONObject(), Collections.singletonList(7300)};
        for (Object tid : invalidIds) {
            JSONObject row = row("42", false);
            row.put("tid", tid);
            FakeCalls calls = new FakeCalls(document(row));
            PageResult result = new PageResult();
            new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls)
                    .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
            assertEquals(1, calls.calls.size());
            assertEquals(1, result.errors);
            assertNull(result.page);
        }
    }

    @Test
    public void originalBodyUsesExplicitFloorIdentityAndIgnoresOtherTextAndMetadata() throws Exception {
        JSONObject original = post("7300", "42", 0, "The viewed user's original text");
        original.put("signature", "SIGNATURE_SENTINEL");
        original.put("attachs", Collections.singletonList("ATTACHMENT_SENTINEL"));
        original.put("__P", post("7300", "99", 1, "NESTED_REPLY_SENTINEL"));
        String raw = detailDocument("7300", post("7300", "99", 8, "OTHER_USER_SENTINEL"),
                original, post("7300", "42", 9, "LATER_FLOOR_SENTINEL"));
        assertEquals("The viewed user's original text", NgaTopicBodyParser.parse(raw, "42", "7300"));
    }

    @Test
    public void topicAndOriginalIdentitiesMustBePresentAndMatch() {
        for (String owner : new String[]{"topic", "original"}) {
            for (String field : new String[]{"tid", "authorid"}) {
                for (Object value : new Object[]{null, "", 0, -1, "99", true, new JSONObject()}) {
                    JSONObject root = JSON.parseObject(detailDocument("7300", post("7300", "42", 0, "BODY_SENTINEL")));
                    JSONObject data = root.getJSONObject("data");
                    JSONObject target = "topic".equals(owner) ? data.getJSONObject("__T")
                            : data.getJSONObject("__R").getJSONObject("0");
                    target.put(field, value);
                    // A topic author may be omitted; explicitly supplied invalid values still fail.
                    assertBodyFails(JSON.toJSONString(root,
                            com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
                }
            }
        }
        JSONObject root = JSON.parseObject(detailDocument("7300", post("7300", "42", 0, "BODY_SENTINEL")));
        root.getJSONObject("data").put("tid", "99");
        assertBodyFails(root.toJSONString());
        assertBodyFails(detailDocument("7300", post("99", "99", 1, "OTHER_THREAD_SENTINEL"),
                post("7300", "42", 0, "BODY_SENTINEL")));
    }

    @Test
    public void numericIdentitiesAndAnOmittedOptionalTopicAuthorAreAccepted() throws Exception {
        JSONObject original = post("7300", "42", 0, "Original body");
        original.put("tid", 7300);
        original.put("authorid", 42);
        JSONObject root = JSON.parseObject(detailDocument("7300", original));
        JSONObject data = root.getJSONObject("data");
        data.getJSONObject("__T").put("tid", 7300);
        data.getJSONObject("__T").remove("authorid");
        data.put("tid", 7300);
        assertEquals("Original body", NgaTopicBodyParser.parse(root.toJSONString(), "42", "7300"));
    }

    @Test
    public void absentOrAmbiguousOriginalFloorsCannotBecomeSuccessfulBodies() {
        assertBodyFails(detailDocument("7300", post("7300", "42", 1, "LATER_FLOOR_SENTINEL")));
        assertBodyFails(detailDocument("7300", post("7300", "42", 0, "FIRST_SENTINEL"),
                post("7300", "42", 0, "SECOND_SENTINEL")));
        for (Object floor : new Object[]{null, "", -1, "00", "0.0", true, new JSONObject()}) {
            JSONObject original = post("7300", "42", 0, "BODY_SENTINEL");
            original.put("lou", floor);
            assertBodyFails(detailDocument("7300", original));
        }
        assertBodyFails(detailDocument("7300"));
    }

    @Test
    public void unavailableOriginalMarkersDiscardTheirTextBeforeAuthorshipChecks() throws Exception {
        for (String marker : new String[]{"denied", "error"}) {
            JSONObject unavailable = post("99", "99", 0, "UNAVAILABLE_BODY_SENTINEL");
            unavailable.put(marker, "DENIAL_SENTINEL");
            assertNull(NgaTopicBodyParser.parse(detailDocument("7300", unavailable,
                    post("7300", "42", 1, "LATER_FLOOR_SENTINEL")), "42", "7300"));
            assertBodyFails(detailDocument("7300", unavailable, post("7300", "42", 0, "BODY_SENTINEL")));

            JSONObject placeholder = new JSONObject();
            placeholder.put(marker, "DENIAL_SENTINEL");
            assertNull(NgaTopicBodyParser.parse(detailDocument("7300", placeholder), "42", "7300"));
            placeholder.put("lou", 2);
            assertBodyFails(detailDocument("7300", placeholder));
        }
    }

    @Test
    public void blankOrNonStringOriginalMarkersDoNotBypassValidation() throws Exception {
        for (String marker : new String[]{"denied", "error"}) {
            for (Object value : new Object[]{null, "", " \r\n\t", true, 1, new JSONObject()}) {
                JSONObject original = post("7300", "42", 0, "Visible original body");
                original.put(marker, value);
                assertEquals("Visible original body",
                        NgaTopicBodyParser.parse(detailDocument("7300", original), "42", "7300"));
                original.put("authorid", "99");
                assertBodyFails(detailDocument("7300", original));
            }
        }
    }

    @Test
    public void emptyOriginalTextIsDistinctFromUnavailableOrMalformedBodyData() throws Exception {
        assertEquals("", NgaTopicBodyParser.parse(detailDocument("7300", post("7300", "42", 0, "")),
                "42", "7300"));
        for (Object content : new Object[]{null, true, new JSONObject(), Collections.singletonList("BODY_SENTINEL")}) {
            assertBodyFails(detailDocument("7300", post("7300", "42", 0, content)));
        }
        JSONObject original = post("7300", "42", 0, "BODY_SENTINEL");
        original.remove("content");
        original.put("subject", "TITLE_CANNOT_REPLACE_BODY_SENTINEL");
        assertBodyFails(detailDocument("7300", original));
    }

    @Test
    public void knownWrappersAndNumericBodyTokensPreserveTheirText() throws Exception {
        for (String number : new String[]{"0", "123", "-42", "1.250", "1e+3", "+123", "00012"}) {
            String raw = detailDocument("7300", post("7300", "42", 0, "NUMERIC_PLACEHOLDER"))
                    .replace("\"NUMERIC_PLACEHOLDER\"", number);
            assertEquals(number, NgaTopicBodyParser.parse("/*$js$*/" + raw
                    + ";/*error fill content legacy suffix", "42", "7300"));
        }
        for (String invalid : new String[]{"0x12", "NaN", "undefined", "+1.2", "--1", "-001"}) {
            String raw = detailDocument("7300", post("7300", "42", 0, "NUMERIC_PLACEHOLDER"))
                    .replace("\"NUMERIC_PLACEHOLDER\"", invalid);
            assertBodyFails(raw);
        }
    }

    @Test
    public void envelopeNormalizationNeverRewritesQuotedMarkersOrNumericLookingText() throws Exception {
        String body = "Literal /*$js$*/ and /*error fill content plus \\\"content\\\":+0123, \\\\ text";
        String raw = detailDocument("7300", post("7300", "42", 0, body));
        assertEquals(body, NgaTopicBodyParser.parse("/*$js$*/window.script_muti_get_var_store="
                + raw + ";", "42", "7300"));
    }

    @Test
    public void malformedOrRejectedDetailEnvelopesFailSafely() {
        String deep = SummaryInputTest.repeat('[', 70) + "0" + SummaryInputTest.repeat(']', 70);
        String valid = detailDocument("7300", post("7300", "42", 0, "BODY_SENTINEL"));
        for (String raw : new String[]{"[]", "<html>CHALLENGE_SENTINEL</html>",
                "{\"error\":\"DENIAL_SENTINEL\"}", "{\"data\":{\"__MESSAGE\":\"DENIAL_SENTINEL\"}}",
                "{\"data\":{\"__T\":{\"tid\":7300}}}", "{\"data\":{\"__R\":{}}}",
                "{\"data\":" + deep + "}", "/*unknown*/" + valid, valid + ";runSomething()",
                valid.replace("\"BODY_SENTINEL\"", "{\"$ref\":\"$.data.__T\"}"),
                SummaryInputTest.repeat('x', NgaProfilePageSource.MAX_RESPONSE_BYTES + 1)}) {
            assertBodyFails(raw);
        }
    }

    @Test
    public void firstPageCollectionIncludesVerifiedTopicBodiesAndKeepsSessionDataOutOfThePrompt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(gbkResponse(document(row("42", false))));
            server.enqueue(gbkResponse(detailDocument("7300",
                    post("7300", "99", 1, "OTHER_FLOOR_SENTINEL"),
                    post("7300", "42", 0, "[b]示例主题正文[/b][quote]引用文字[/quote]自己的观点"))));
            server.enqueue(gbkResponse(document(row("42", true))));
            OkHttpClient client = localClient(server, 5000);
            try {
                NgaProfilePageSource source = new NgaProfilePageSource("https://bbs.nga.cn",
                        "COOKIE_SENTINEL", "Synthetic-UA", client);
                TextResult result = new TextResult();
                new ProfileSummaryLoader(source).load("42", "Viewed user", result);
                assertTrue(result.finished.await(5, TimeUnit.SECONDS));
                assertNull(result.error);
                assertTrue(result.prompt.contains("示例回复正文"));
                assertTrue(result.prompt.contains("主题正文：示例主题正文\n引用：\n引用文字\n引用结束\n自己的观点"));
                assertFalse(result.prompt.contains("OTHER_FLOOR_SENTINEL"));
                assertFalse(result.prompt.contains("TOPIC_BODY_SENTINEL"));
                assertFalse(result.prompt.contains("7300"));
                assertFalse(result.prompt.contains("COOKIE_SENTINEL"));
                assertFalse(result.prompt.contains("PROFILE_EMAIL_SENTINEL"));
                RecordedRequest topics = server.takeRequest(2, TimeUnit.SECONDS);
                RecordedRequest detail = server.takeRequest(2, TimeUnit.SECONDS);
                RecordedRequest replies = server.takeRequest(2, TimeUnit.SECONDS);
                assertNotNull(topics);
                assertNotNull(detail);
                assertNotNull(replies);
                assertEquals("COOKIE_SENTINEL", topics.getHeader("Cookie"));
                assertEquals("COOKIE_SENTINEL", replies.getHeader("Cookie"));
                assertEquals("COOKIE_SENTINEL", detail.getHeader("Cookie"));
                assertEquals("Synthetic-UA", detail.getHeader("User-Agent"));
                assertEquals("Nga_Official", detail.getHeader("X-User-Agent"));
                assertEquals("/read.php?page=1&__output=8&noprefix&v2&tid=7300", detail.getPath());
                assertEquals("42", topics.getRequestUrl().queryParameter("authorid"));
                assertEquals("42", replies.getRequestUrl().queryParameter("authorid"));
                assertEquals("1", topics.getRequestUrl().queryParameter("page"));
                assertEquals("1", replies.getRequestUrl().queryParameter("page"));
                assertEquals("1", replies.getRequestUrl().queryParameter("searchpost"));
                assertEquals(3, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void maximumCollectionSchedulesTwentyTwoSequentialReadsEvenWithSynchronousCallbacks() {
        JSONObject[] topics = new JSONObject[23];
        topics[0] = new JSONObject();
        topics[0].put("denied", "UNAVAILABLE_SENTINEL");
        JSONObject[] replies = new JSONObject[22];
        for (int i = 0; i < 22; i++) {
            topics[i + 1] = row("42", false);
            topics[i + 1].put("tid", String.valueOf(7300 + i));
            topics[i + 1].put("subject", "Topic " + i);
            replies[i] = row("42", true);
            replies[i].getJSONObject("__P").put("content", "Reply body " + i);
        }
        List<String> responses = new ArrayList<>();
        responses.add(document(topics));
        for (int i = 0; i < 20; i++) {
            String tid = String.valueOf(7300 + i);
            responses.add(detailDocument(tid, post(tid, "42", 0, "Topic body " + i)));
        }
        responses.add(document(replies));
        FakeCalls calls = new FakeCalls(responses.toArray(new String[0]));
        TextResult result = new TextResult();
        new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "FROZEN_COOKIE", "Frozen-UA", calls))
                .load("42", "Viewed user", result);

        assertEquals(1, result.successes);
        assertNull(result.error);
        assertEquals(22, calls.calls.size());
        assertEquals(1, calls.maxActive);
        assertEquals(0, calls.active);
        assertTrue(result.prompt.contains("样本数量：主题 20 条，回复 20 条"));
        for (int i = 0; i < 20; i++) {
            assertEquals(String.valueOf(7300 + i), calls.calls.get(i + 1).request.url().queryParameter("tid"));
            assertTrue(result.prompt.contains("主题正文：Topic body " + i + "\n"));
            assertTrue(result.prompt.contains("回复正文：Reply body " + i + "\n"));
        }
        assertFalse(result.prompt.contains("Topic body 20"));
        assertFalse(result.prompt.contains("Reply body 20"));
        assertEquals("1", calls.calls.get(21).request.url().queryParameter("searchpost"));
        for (FakeCall call : calls.calls) {
            assertEquals("1", call.request.url().queryParameter("page"));
            assertEquals("FROZEN_COOKIE", call.request.header("Cookie"));
            assertEquals("Frozen-UA", call.request.header("User-Agent"));
            assertNull(call.request.header("Authorization"));
        }
        assertFalse(result.prompt.contains("FROZEN_COOKIE"));
    }

    @Test
    public void allUnavailableTopicListsScheduleNoBodyReads() {
        JSONObject unavailable = new JSONObject();
        unavailable.put("denied", "UNAVAILABLE_SENTINEL");
        for (boolean emptyReplies : new boolean[]{false, true}) {
            FakeCalls calls = new FakeCalls(document(unavailable),
                    document(emptyReplies ? unavailable : row("42", true)));
            TextResult result = new TextResult();
            new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls))
                    .load("42", "User", result);
            assertEquals(2, calls.calls.size());
            assertEquals("1", calls.calls.get(1).request.url().queryParameter("searchpost"));
            if (emptyReplies) {
                assertEquals(1, result.errors);
                assertNull(result.prompt);
            } else {
                assertEquals(1, result.successes);
                assertTrue(result.prompt.contains("无可见主题"));
                assertTrue(result.prompt.contains("示例回复正文"));
                assertFalse(result.prompt.contains("UNAVAILABLE_SENTINEL"));
            }
        }
    }

    @Test
    public void unavailableAndEmptyBodiesUseApplicationNoticesBesideVisibleTopicMetadata() {
        for (boolean unavailable : new boolean[]{false, true}) {
            JSONObject original = post("7300", "42", 0, " \n\t");
            if (unavailable) {
                original.put("denied", "DENIAL_SENTINEL");
                original.put("content", "UNAVAILABLE_BODY_SENTINEL");
                original.put("authorid", "99");
            }
            FakeCalls calls = new FakeCalls(document(row("42", false)), detailDocument("7300", original),
                    document(row("42", true)));
            TextResult result = new TextResult();
            new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls))
                    .load("42", "User", result);
            assertEquals(1, result.successes);
            assertNull(result.error);
            assertEquals(3, calls.calls.size());
            assertTrue(result.prompt.contains("[主题1] Synthetic topic | Synthetic board |"));
            assertTrue(result.prompt.contains(unavailable ? "主题正文：[应用提示：正文不可用]"
                    : "主题正文：[应用提示：未提供可用文字]"));
            assertTrue(result.prompt.contains("示例回复正文"));
            assertFalse(result.prompt.contains("SENTINEL"));
        }
    }

    @Test
    public void cancellationAtAnyCollectionStageDiscardsLateCallbacksAndStopsLaterReads() throws Exception {
        JSONObject second = row("42", false);
        second.put("tid", "7301");
        String list = document(row("42", false), second);
        for (int stage = 0; stage < 3; stage++) {
            FakeCalls calls = new FakeCalls();
            PageResult result = new PageResult();
            SummaryController.Cancelable load = new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls)
                    .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
            if (stage >= 1) {
                calls.calls.get(0).respond(list);
            }
            if (stage >= 2) {
                calls.calls.get(1).respond(detailDocument("7300", post("7300", "42", 0, "First body")));
            }
            FakeCall active = calls.calls.get(stage);
            load.cancel();
            active.respond(stage == 0 ? list : detailDocument(String.valueOf(7299 + stage),
                    post(String.valueOf(7299 + stage), "42", 0, "LATE_BODY_SENTINEL")));
            active.fail(new IOException("LATE_FAILURE_SENTINEL"));
            assertTrue(active.canceled);
            assertEquals(stage + 1, calls.calls.size());
            assertEquals(0, calls.active);
            assertEquals(0, result.successes);
            assertEquals(0, result.errors);
        }
    }

    @Test
    public void completedRequestCallbacksCannotAdvanceOrFailTheNextBodyRead() throws Exception {
        JSONObject second = row("42", false);
        second.put("tid", "7301");
        FakeCalls calls = new FakeCalls(document(row("42", false), second));
        PageResult result = new PageResult();
        new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls)
                .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
        assertEquals(2, calls.calls.size());
        calls.calls.get(0).respond(document(row("99", false)));
        calls.calls.get(0).fail(new IOException("STALE_LIST_SENTINEL"));
        assertEquals(2, calls.calls.size());
        calls.calls.get(1).respond(detailDocument("7300", post("7300", "42", 0, "First body")));
        assertEquals(3, calls.calls.size());
        calls.calls.get(1).respond(detailDocument("7300", post("7300", "42", 0, "STALE_BODY_SENTINEL")));
        calls.calls.get(1).fail(new IOException("STALE_FAILURE_SENTINEL"));
        assertEquals(0, result.errors);
        assertEquals(0, result.successes);
        calls.calls.get(2).respond(detailDocument("7301", post("7301", "42", 0, "Second body")));
        calls.calls.get(2).respond(detailDocument("7301", post("7301", "42", 0, "DUPLICATE_BODY_SENTINEL")));
        assertEquals(1, result.successes);
        assertEquals(0, result.errors);
        assertEquals(3, calls.calls.size());
        assertEquals(1, calls.maxActive);
        assertFalse(prompt(result.page).contains("SENTINEL"));
        assertTrue(prompt(result.page).contains("主题正文：First body\n"));
        assertTrue(prompt(result.page).contains("主题正文：Second body\n"));
    }

    @Test
    public void factoryAndEnqueueExceptionsAtEveryStageTerminateOnceWithSafeErrors() {
        JSONObject second = row("42", false);
        second.put("tid", "7301");
        for (boolean factoryFailure : new boolean[]{false, true}) {
            for (int stage = 0; stage < 3; stage++) {
                FakeCalls calls = new FakeCalls(document(row("42", false), second),
                        detailDocument("7300", post("7300", "42", 0, "First body")),
                        detailDocument("7301", post("7301", "42", 0, "Second body")));
                calls.throwAtFactory = factoryFailure ? stage : -1;
                calls.throwAtEnqueue = factoryFailure ? -1 : stage;
                PageResult result = new PageResult();
                new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls)
                        .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
                assertEquals(1, result.errors);
                assertEquals(0, result.successes);
                assertFalse(result.error.contains("SENTINEL"));
                assertEquals(0, calls.active);
            }
        }
    }

    @Test
    public void cancellationDoesNotWaitForABlockedResponseRead() throws Exception {
        FakeCalls calls = new FakeCalls(document(row("42", false)));
        PageResult result = new PageResult();
        SummaryController.Cancelable load = new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls)
                .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BufferedSource blocked = Okio.buffer(new Source() {
            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                reading.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Synthetic blocked read deadline");
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                throw new InterruptedIOException("Synthetic canceled read");
            }

            @Override public Timeout timeout() { return Timeout.NONE; }
            @Override public void close() { release.countDown(); }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> response = executor.submit(() -> {
                calls.calls.get(1).respond(ResponseBody.create(MediaType.get("application/json; charset=UTF-8"),
                        -1, blocked));
                return null;
            });
            assertTrue(reading.await(2, TimeUnit.SECONDS));
            executor.submit(load::cancel).get(1, TimeUnit.SECONDS);
            assertTrue(calls.calls.get(1).canceled);
            release.countDown();
            response.get(2, TimeUnit.SECONDS);
            assertEquals(0, result.errors);
            assertEquals(0, result.successes);
            assertEquals(2, calls.calls.size());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void callDeadlineReportsAnErrorEvenThoughOkHttpMarksTheCallCanceled() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            OkHttpClient client = localClient(server, 250);
            try {
                PageResult result = new PageResult();
                new NgaProfilePageSource("https://bbs.nga.cn", "", "Synthetic-UA", client)
                        .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
                assertTrue("A deadline must leave Loading", result.finished.await(5, TimeUnit.SECONDS));
                assertNull(result.page);
                assertTrue(result.error.contains("超时"));
                assertEquals(1, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void detailTransportAndParserFailuresStopWithoutAPartialSampleOrFurtherReads() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            JSONObject second = row("42", false);
            second.put("tid", "7301");
            MockResponse[] failures = {
                    new MockResponse().setResponseCode(302).setHeader("Location", "https://untrusted.example/")
                            .setBody("RAW_ERROR_SENTINEL"),
                    new MockResponse().setResponseCode(403).setBody("RAW_ERROR_SENTINEL"),
                    gbkResponse("{\"data\":{\"__MESSAGE\":\"DENIAL_SENTINEL\"}}"),
                    gbkResponse(detailDocument("7300", post("7300", "99", 0, "FOREIGN_BODY_SENTINEL"))),
                    new MockResponse().setHeader("Content-Type", "application/json; charset=invalid-charset")
                            .setBody("RAW_ERROR_SENTINEL"),
                    new MockResponse().setHeader("Content-Type", "application/json; charset=UTF-8")
                            .setChunkedBody(SummaryInputTest.repeat('x', NgaProfilePageSource.MAX_RESPONSE_BYTES + 1), 8192)
            };
            OkHttpClient client = localClient(server, 5000);
            try {
                for (int i = 0; i < failures.length; i++) {
                    server.enqueue(gbkResponse(document(row("42", false), second)));
                    server.enqueue(failures[i]);
                    TextResult result = new TextResult();
                    new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", client))
                            .load("42", "User", result);
                    assertTrue(result.finished.await(5, TimeUnit.SECONDS));
                    assertEquals(1, result.errors);
                    assertNull(result.prompt);
                    assertFalse(result.error.contains("SENTINEL"));
                    assertEquals((i + 1) * 2, server.getRequestCount());
                }
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void detailDeadlineTerminatesCollectionWithoutStartingReplies() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(gbkResponse(document(row("42", false))));
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            OkHttpClient client = localClient(server, 250);
            try {
                TextResult result = new TextResult();
                new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", client))
                        .load("42", "User", result);
                assertTrue(result.finished.await(5, TimeUnit.SECONDS));
                assertEquals(1, result.errors);
                assertTrue(result.error.contains("超时"));
                assertNull(result.prompt);
                assertEquals(2, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void deliberateCancellationSuppressesTheTransportCallback() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            OkHttpClient client = localClient(server, 5000);
            try {
                PageResult result = new PageResult();
                SummaryController.Cancelable call = new NgaProfilePageSource("https://bbs.nga.cn", "",
                        "Synthetic-UA", client).loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                call.cancel();
                assertFalse(result.finished.await(300, TimeUnit.MILLISECONDS));
                assertNull(result.page);
                assertNull(result.error);
                assertEquals(1, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void httpRejectionsAndRedirectsStopWithoutParsingOrRetryingTheBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            OkHttpClient client = localClient(server, 5000);
            try {
                int[] codes = {401, 403, 429, 500, 302};
                String[] messages = {"登录", "拒绝访问", "频繁", "服务暂时不可用", "重定向"};
                for (int i = 0; i < codes.length; i++) {
                    server.enqueue(new MockResponse().setResponseCode(codes[i])
                            .setHeader("Location", "https://untrusted.example/")
                            .setBody("RAW_ERROR_SENTINEL"));
                    PageResult result = new PageResult();
                    new NgaProfilePageSource("https://bbs.nga.cn", "", "Synthetic-UA", client)
                            .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
                    assertTrue(result.finished.await(5, TimeUnit.SECONDS));
                    assertNull(result.page);
                    assertTrue(result.error.contains(messages[i]));
                    assertFalse(result.error.contains("RAW_ERROR_SENTINEL"));
                }
                assertEquals(codes.length, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void chunkedOversizeAndUnsupportedCharsetFailBeforeProducingActivity() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/javascript; charset=UTF-8")
                    .setChunkedBody(SummaryInputTest.repeat('x', NgaProfilePageSource.MAX_RESPONSE_BYTES + 1), 8192));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/javascript; charset=invalid-charset")
                    .setBody(document(row("42", false))));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/javascript; charset = invalid-charset")
                    .setBody(document(row("42", false))));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/javascript; charset=UTF-8")
                    .setBody(new Buffer().write(new byte[]{(byte) 0xc3, 0x28})));
            OkHttpClient client = localClient(server, 5000);
            try {
                for (int i = 0; i < 4; i++) {
                    PageResult result = new PageResult();
                    new NgaProfilePageSource("https://bbs.nga.cn", "", "Synthetic-UA", client)
                            .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
                    assertTrue(result.finished.await(5, TimeUnit.SECONDS));
                    assertNull(result.page);
                    assertNotNull(result.error);
                }
                assertEquals(4, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    private static JSONObject row(String uid, boolean reply) {
        JSONObject row = new JSONObject();
        row.put("tid", "7300");
        row.put("authorid", uid);
        row.put("subject", "Synthetic topic");
        row.put("postdate", 1767225600);
        row.put("fid", 123);
        row.put("content", "TOPIC_BODY_SENTINEL");
        JSONObject parent = new JSONObject();
        parent.put("2", "Synthetic board");
        row.put("parent", parent);
        if (reply) {
            JSONObject post = new JSONObject();
            post.put("authorid", uid);
            post.put("postdate", 1767225600);
            post.put("content", "示例回复正文");
            row.put("__P", post);
        }
        return row;
    }

    private static String document(JSONObject... items) {
        JSONObject rows = new JSONObject();
        for (int i = 0; i < items.length; i++) {
            rows.put(String.valueOf(i), items[i]);
        }
        JSONObject data = new JSONObject();
        data.put("__T", rows);
        data.put("__T__ROWS", items.length);
        JSONObject privateData = new JSONObject();
        privateData.put("cookie", "COOKIE_SENTINEL");
        privateData.put("email", "PROFILE_EMAIL_SENTINEL");
        data.put("__CU", privateData);
        JSONObject root = new JSONObject();
        root.put("data", data);
        return JSON.toJSONString(root);
    }

    private static JSONObject post(String tid, String uid, int floor, Object content) {
        JSONObject post = new JSONObject();
        post.put("tid", tid);
        post.put("authorid", uid);
        post.put("lou", floor);
        post.put("pid", floor == 0 ? 0 : 100 + floor);
        post.put("content", content);
        return post;
    }

    private static String detailDocument(String tid, JSONObject... posts) {
        JSONObject topic = new JSONObject();
        topic.put("tid", tid);
        topic.put("authorid", "42");
        topic.put("subject", "DETAIL_TITLE_SENTINEL");
        JSONObject rows = new JSONObject();
        for (int i = 0; i < posts.length; i++) {
            rows.put(String.valueOf(i), posts[i]);
        }
        JSONObject data = new JSONObject();
        data.put("__T", topic);
        data.put("__R", rows);
        data.put("__R__ROWS", posts.length);
        data.put("__U", Collections.singletonMap("42", Collections.singletonMap("email", "PROFILE_EMAIL_SENTINEL")));
        data.put("__CU", Collections.singletonMap("cookie", "COOKIE_SENTINEL"));
        JSONObject root = new JSONObject();
        root.put("data", data);
        return root.toJSONString();
    }

    private static void assertBodyFails(String raw) {
        NgaProfilePageSource.PageException error = assertThrows(NgaProfilePageSource.PageException.class,
                () -> NgaTopicBodyParser.parse(raw, "42", "7300"));
        assertFalse(error.getMessage().contains("SENTINEL"));
        assertNull(error.getCause());
    }

    private static String prompt(ProfileSummaryLoader.Page page) {
        return new ProfileSummaryInput(page.uid, "Viewed user",
                page.kind == ProfileSummaryLoader.Kind.TOPICS ? page.entries : Collections.emptyList(),
                page.kind == ProfileSummaryLoader.Kind.REPLIES ? page.entries : Collections.emptyList())
                .toPrompt();
    }

    private static MockResponse gbkResponse(String json) {
        return new MockResponse().setHeader("Content-Type", "application/javascript; charset=GBK")
                .setBody(new Buffer().write(json.getBytes(Charset.forName("GBK"))));
    }

    private static OkHttpClient localClient(MockWebServer server, long timeoutMillis) {
        return new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .addInterceptor(chain -> {
                    // Test-only destination override: every socket stays on this loopback server.
                    HttpUrl local = server.url(chain.request().url().encodedPath()).newBuilder()
                            .encodedQuery(chain.request().url().encodedQuery()).build();
                    return chain.proceed(chain.request().newBuilder().url(local).build());
                }).build();
    }

    private static void close(OkHttpClient client) {
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }

    private static final class FakeCalls implements Call.Factory {
        final List<FakeCall> calls = new ArrayList<>();
        final String[] synchronousBodies;
        int throwAtFactory = -1;
        int throwAtEnqueue = -1;
        int active;
        int maxActive;

        FakeCalls(String... synchronousBodies) {
            this.synchronousBodies = synchronousBodies;
        }

        @Override
        public Call newCall(Request request) {
            int index = calls.size();
            if (index == throwAtFactory) {
                throw new IllegalStateException("FACTORY_FAILURE_SENTINEL");
            }
            FakeCall call = new FakeCall(this, request, index);
            calls.add(call);
            return call;
        }
    }

    private static final class FakeCall implements Call {
        final FakeCalls owner;
        final Request request;
        final int index;
        Callback callback;
        boolean executed;
        boolean finished;
        volatile boolean canceled;

        FakeCall(FakeCalls owner, Request request, int index) {
            this.owner = owner;
            this.request = request;
            this.index = index;
        }

        @Override
        public void enqueue(Callback callback) {
            this.callback = callback;
            if (index == owner.throwAtEnqueue) {
                throw new IllegalStateException("ENQUEUE_FAILURE_SENTINEL");
            }
            executed = true;
            owner.active++;
            owner.maxActive = Math.max(owner.maxActive, owner.active);
            if (canceled) {
                complete();
                return;
            }
            if (index < owner.synchronousBodies.length && owner.synchronousBodies[index] != null) {
                try {
                    respond(owner.synchronousBodies[index]);
                } catch (IOException error) {
                    fail(error);
                }
            }
        }

        void respond(String raw) throws IOException {
            Buffer bytes = new Buffer().writeUtf8(raw);
            long length = bytes.size();
            BufferedSource source = Okio.buffer(new ForwardingSource(bytes) {
                @Override
                public void close() throws IOException {
                    super.close();
                    complete();
                }
            });
            respond(ResponseBody.create(MediaType.get("application/json; charset=UTF-8"), length, source));
        }

        void respond(ResponseBody body) throws IOException {
            callback.onResponse(this, new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").header("Content-Type", "application/json; charset=UTF-8")
                    .body(body).build());
        }

        void fail(IOException error) {
            complete();
            callback.onFailure(this, error);
        }

        private void complete() {
            if (executed && !finished) {
                finished = true;
                owner.active--;
            }
        }

        @Override public Request request() { return request; }
        @Override public Response execute() { throw new UnsupportedOperationException(); }
        @Override public boolean isExecuted() { return executed; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Timeout timeout() { return Timeout.NONE; }
        @Override public Call clone() { return new FakeCall(owner, request, index); }

        @Override
        public void cancel() {
            canceled = true;
            complete();
        }
    }

    private static final class PageResult implements ProfileSummaryLoader.PageCallback {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile ProfileSummaryLoader.Page page;
        volatile String error;
        volatile int successes;
        volatile int errors;

        @Override
        public void onSuccess(ProfileSummaryLoader.Page page) {
            successes++;
            this.page = page;
            finished.countDown();
        }

        @Override
        public void onError(String safeMessage) {
            errors++;
            error = safeMessage;
            finished.countDown();
        }
    }

    private static final class TextResult implements SummaryController.Callback {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String prompt;
        volatile String error;
        volatile int successes;
        volatile int errors;

        @Override
        public void onSuccess(String text) {
            successes++;
            prompt = text;
            finished.countDown();
        }

        @Override
        public void onError(String safeMessage) {
            errors++;
            error = safeMessage;
            finished.countDown();
        }
    }
}
