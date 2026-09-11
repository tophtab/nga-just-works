package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

public class NgaProfilePageSourceTest {

    @Test
    public void requestsUseTheViewedUidPageOneAndAFrozenSession() {
        Request topics = NgaProfilePageSource.buildRequest("https://bbs.nga.cn", "synthetic-session",
                "synthetic-user-agent", "4200", ProfileSummaryLoader.Kind.TOPICS);
        Request replies = NgaProfilePageSource.buildRequest("https://bbs.nga.cn", "synthetic-session",
                "synthetic-user-agent", "4200", ProfileSummaryLoader.Kind.REPLIES);
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
    public void firstPageCollectionUsesTwoOfflineRequestsAndKeepsSessionDataOutOfThePrompt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(gbkResponse(document(row("42", false))));
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
                assertFalse(result.prompt.contains("COOKIE_SENTINEL"));
                assertFalse(result.prompt.contains("PROFILE_EMAIL_SENTINEL"));
                RecordedRequest topics = server.takeRequest(2, TimeUnit.SECONDS);
                RecordedRequest replies = server.takeRequest(2, TimeUnit.SECONDS);
                assertNotNull(topics);
                assertNotNull(replies);
                assertEquals("COOKIE_SENTINEL", topics.getHeader("Cookie"));
                assertEquals("COOKIE_SENTINEL", replies.getHeader("Cookie"));
                assertEquals("42", topics.getRequestUrl().queryParameter("authorid"));
                assertEquals("42", replies.getRequestUrl().queryParameter("authorid"));
                assertEquals("1", topics.getRequestUrl().queryParameter("page"));
                assertEquals("1", replies.getRequestUrl().queryParameter("page"));
                assertEquals("1", replies.getRequestUrl().queryParameter("searchpost"));
                assertEquals(2, server.getRequestCount());
            } finally {
                close(client);
            }
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

    private static final class PageResult implements ProfileSummaryLoader.PageCallback {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile ProfileSummaryLoader.Page page;
        volatile String error;

        @Override
        public void onSuccess(ProfileSummaryLoader.Page page) {
            this.page = page;
            finished.countDown();
        }

        @Override
        public void onError(String safeMessage) {
            error = safeMessage;
            finished.countDown();
        }
    }

    private static final class TextResult implements SummaryController.Callback {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String prompt;
        volatile String error;

        @Override
        public void onSuccess(String text) {
            prompt = text;
            finished.countDown();
        }

        @Override
        public void onError(String safeMessage) {
            error = safeMessage;
            finished.countDown();
        }
    }
}
