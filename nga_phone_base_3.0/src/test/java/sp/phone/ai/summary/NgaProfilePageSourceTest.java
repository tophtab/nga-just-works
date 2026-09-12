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
import sp.phone.ai.summary.ProfileRequestQueueTest.FakeTime;

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
        assertEquals("synthetic-user-agent", replies.header("User-Agent"));
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
    public void invalidTopicIdsFailBeforeReturningActivity() {
        Object[] invalidIds = {null, "", 0, -1, "07300", "+7300", "7300&pid=1", "1e3",
                "12345678901234567890", true, new JSONObject(), Collections.singletonList(7300)};
        for (Object tid : invalidIds) {
            JSONObject row = row("42", false);
            row.put("tid", tid);
            FakeCalls calls = new FakeCalls(document(row));
            PageResult result = new PageResult();
            new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue)
                    .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, result);
            assertEquals(1, calls.calls.size());
            assertEquals(1, result.errors);
            assertNull(result.page);
        }
    }

    @Test
    public void maximumCollectionUsesOnlyTwoListReadsEvenWithSynchronousCallbacks() {
        JSONObject[] topics = new JSONObject[23];
        topics[0] = new JSONObject();
        topics[0].put("denied", "UNAVAILABLE_SENTINEL");
        JSONObject[] replies = new JSONObject[22];
        for (int i = 0; i < 22; i++) {
            topics[i + 1] = row("42", false);
            topics[i + 1].put("tid", String.valueOf(7300 + i));
            topics[i + 1].put("subject", "Topic " + i);
            topics[i + 1].put("__P", Collections.singletonMap("content", "NESTED_TOPIC_BODY_SENTINEL"));
            replies[i] = row("42", true);
            replies[i].getJSONObject("__P").put("content", "Reply body " + i);
        }
        FakeCalls calls = new FakeCalls(document(topics), document(replies));
        TextResult result = new TextResult();
        new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "FROZEN_COOKIE", "Frozen-UA", calls, calls.queue))
                .load("42", "Viewed user", result);

        assertEquals(1, calls.calls.size());
        assertEquals(0, result.successes);
        calls.time.advanceBy(499L);
        assertEquals(1, calls.calls.size());
        calls.time.advanceBy(1L);
        assertEquals(1, result.successes);
        assertNull(result.error);
        assertEquals(2, calls.calls.size());
        assertEquals(1, calls.maxActive);
        assertEquals(0, calls.active);
        assertTrue(result.prompt.contains("样本数量：主题 20 条，回复 20 条"));
        for (int i = 0; i < 20; i++) {
            assertTrue(result.prompt.contains("[主题" + (i + 1) + "] Topic " + i + " | Synthetic board |"));
            assertTrue(result.prompt.contains("回复正文：Reply body " + i + "\n"));
        }
        assertFalse(result.prompt.contains("Topic 20 |"));
        assertFalse(result.prompt.contains("Reply body 20"));
        assertFalse(result.prompt.contains("主题正文："));
        assertFalse(result.prompt.contains("SENTINEL"));
        assertNull(calls.calls.get(0).request.url().queryParameter("searchpost"));
        assertEquals("1", calls.calls.get(1).request.url().queryParameter("searchpost"));
        for (FakeCall call : calls.calls) {
            assertEquals("/thread.php", call.request.url().encodedPath());
            assertEquals("42", call.request.url().queryParameter("authorid"));
            assertEquals("1", call.request.url().queryParameter("page"));
            assertEquals("FROZEN_COOKIE", call.request.header("Cookie"));
            assertEquals("Frozen-UA", call.request.header("User-Agent"));
            assertNull(call.request.header("Authorization"));
        }
        assertFalse(result.prompt.contains("FROZEN_COOKIE"));
    }

    @Test
    public void unavailableTopicListsStillAllowRepliesButNotAnEmptySummary() {
        JSONObject unavailable = new JSONObject();
        unavailable.put("denied", "UNAVAILABLE_SENTINEL");
        for (boolean emptyReplies : new boolean[]{false, true}) {
            String empty = document(unavailable);
            FakeCalls calls = new FakeCalls(empty, empty, empty,
                    document(emptyReplies ? unavailable : row("42", true)), empty, empty);
            TextResult result = new TextResult();
            new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue))
                    .load("42", "User", result);
            calls.time.advanceBy(2_500L);
            assertEquals(emptyReplies ? 6 : 4, calls.calls.size());
            assertEquals(1, calls.maxActive);
            for (int i = 0; i < calls.calls.size(); i++) {
                assertEquals(i < 3 ? null : "1", calls.calls.get(i).request.url().queryParameter("searchpost"));
                assertEquals(i * 500L, calls.calls.get(i).startedAt);
                assertEquals("/thread.php", calls.calls.get(i).request.url().encodedPath());
                assertEquals("1", calls.calls.get(i).request.url().queryParameter("page"));
            }
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
    public void emptyRetriesForEitherKindArePacedAndCanRecover() {
        for (ProfileSummaryLoader.Kind emptyKind : ProfileSummaryLoader.Kind.values()) {
            String topics = document(row("42", false));
            String replies = document(row("42", true));
            FakeCalls calls = emptyKind == ProfileSummaryLoader.Kind.TOPICS
                    ? new FakeCalls(document(), document(), topics, replies)
                    : new FakeCalls(topics, document(), document(), replies);
            TextResult result = new TextResult();
            new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "FROZEN_COOKIE", "Frozen-UA",
                    calls, calls.queue)).load("42", "User", result);
            calls.time.advanceBy(1_499L);
            assertEquals(3, calls.calls.size());
            assertEquals(0, result.successes);
            calls.time.advanceBy(1L);
            assertEquals(4, calls.calls.size());
            assertEquals(1, result.successes);
            assertEquals(0, result.errors);
            assertEquals(1, calls.maxActive);
            assertTrue(result.prompt.contains("样本数量：主题 1 条，回复 1 条"));
            for (int i = 0; i < calls.calls.size(); i++) {
                FakeCall call = calls.calls.get(i);
                assertEquals(i * 500L, call.startedAt);
                assertEquals("/thread.php", call.request.url().encodedPath());
                assertEquals("1", call.request.url().queryParameter("page"));
                assertEquals("42", call.request.url().queryParameter("authorid"));
                assertEquals("FROZEN_COOKIE", call.request.header("Cookie"));
                assertEquals("Frozen-UA", call.request.header("User-Agent"));
            }
        }
    }

    @Test
    public void cancelingWhileWaitingForTheNextKindOrEmptyRetryPreventsThatCall() {
        for (int scenario = 0; scenario < 3; scenario++) {
            FakeCalls calls = new FakeCalls(scenario == 1 ? document() : document(row("42", false)), document());
            TextResult result = new TextResult();
            SummaryController.Cancelable load = new ProfileSummaryLoader(
                    new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue))
                    .load("42", "User", result);
            if (scenario == 2) {
                calls.time.advanceBy(500L);
            }
            int count = calls.calls.size();
            calls.time.advanceBy(499L);
            load.cancel();
            calls.time.advanceBy(10_000L);
            assertEquals(scenario == 2 ? 2 : 1, count);
            assertEquals(count, calls.calls.size());
            assertEquals(0, calls.active);
            assertEquals(0, result.successes);
            assertEquals(0, result.errors);
        }
    }

    @Test
    public void separateSourceInstancesShareOneActiveCallAndItsCompletionCooldown() throws Exception {
        FakeCalls calls = new FakeCalls();
        PageResult first = new PageResult();
        PageResult second = new PageResult();
        new NgaProfilePageSource("https://bbs.nga.cn", "FIRST_COOKIE", "UA", calls, calls.queue)
                .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, first);
        new NgaProfilePageSource("https://bbs.nga.cn", "SECOND_COOKIE", "UA", calls, calls.queue)
                .loadFirstPage("43", ProfileSummaryLoader.Kind.REPLIES, second);
        calls.time.advanceBy(10_000L);
        assertEquals(1, calls.calls.size());
        calls.calls.get(0).respond(document(row("42", false)));
        assertEquals(1, first.successes);
        assertEquals(0, calls.active);
        calls.time.advanceBy(499L);
        assertEquals(1, calls.calls.size());
        calls.time.advanceBy(1L);
        assertEquals(2, calls.calls.size());
        assertEquals(10_500L, calls.calls.get(1).startedAt);
        assertEquals("SECOND_COOKIE", calls.calls.get(1).request.header("Cookie"));
        assertEquals("43", calls.calls.get(1).request.url().queryParameter("authorid"));
        calls.calls.get(1).respond(document(row("43", true)));
        assertEquals(1, second.successes);
        assertEquals(1, calls.maxActive);
    }

    @Test
    public void cancelThenReopenWaitsForTheOldCallToFinishAndThenTheCooldown() {
        FakeCalls calls = new FakeCalls();
        PageResult canceledResult = new PageResult();
        SummaryController.Cancelable canceled = new NgaProfilePageSource("https://bbs.nga.cn", "", "UA",
                calls, calls.queue).loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, canceledResult);
        canceled.cancel();
        PageResult reopenedResult = new PageResult();
        new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue)
                .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, reopenedResult);
        calls.time.advanceBy(5_000L);
        assertEquals(1, calls.calls.size());
        assertTrue(calls.calls.get(0).canceled);
        assertEquals(1, calls.active);
        calls.calls.get(0).fail(new IOException("Synthetic cancellation"));
        calls.time.advanceBy(499L);
        assertEquals(1, calls.calls.size());
        calls.time.advanceBy(1L);
        assertEquals(2, calls.calls.size());
        assertEquals(1, calls.maxActive);
        assertEquals(0, canceledResult.successes);
        assertEquals(0, canceledResult.errors);
    }

    @Test
    public void cancellationAtEitherListDiscardsLateCallbacksAndStopsLaterReads() throws Exception {
        for (int stage = 0; stage < 2; stage++) {
            FakeCalls calls = new FakeCalls();
            TextResult result = new TextResult();
            SummaryController.Cancelable load = new ProfileSummaryLoader(
                    new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue))
                    .load("42", "User", result);
            if (stage == 1) {
                calls.calls.get(0).respond(document(row("42", false)));
                calls.time.advanceBy(500L);
            }
            FakeCall active = calls.calls.get(stage);
            load.cancel();
            active.respond(document(row("42", stage == 1)));
            active.fail(new IOException("LATE_FAILURE_SENTINEL"));
            assertTrue(active.canceled);
            assertEquals(stage + 1, calls.calls.size());
            assertEquals(0, calls.active);
            assertEquals(0, result.successes);
            assertEquals(0, result.errors);
        }
    }

    @Test
    public void completedListCallbacksCannotChangeOrFailTheRemainingCollection() throws Exception {
        FakeCalls calls = new FakeCalls(document(row("42", false)));
        TextResult result = new TextResult();
        new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue))
                .load("42", "User", result);
        calls.time.advanceBy(500L);
        assertEquals(2, calls.calls.size());
        calls.calls.get(0).respond(document(row("99", false)));
        calls.calls.get(0).fail(new IOException("STALE_LIST_SENTINEL"));
        assertEquals(2, calls.calls.size());
        assertEquals(0, result.errors);
        assertEquals(0, result.successes);
        calls.calls.get(1).respond(document(row("42", true)));
        calls.calls.get(1).respond(document(row("99", true)));
        calls.calls.get(1).fail(new IOException("STALE_REPLY_SENTINEL"));
        assertEquals(1, result.successes);
        assertEquals(0, result.errors);
        assertEquals(2, calls.calls.size());
        assertEquals(1, calls.maxActive);
        assertFalse(result.prompt.contains("SENTINEL"));
        assertTrue(result.prompt.contains("[主题1] Synthetic topic | Synthetic board |"));
        assertTrue(result.prompt.contains("回复正文：示例回复正文\n"));
    }

    @Test
    public void factoryAndEnqueueExceptionsAtEitherListTerminateOnceWithSafeErrors() {
        for (boolean factoryFailure : new boolean[]{false, true}) {
            for (int stage = 0; stage < 2; stage++) {
                FakeCalls calls = new FakeCalls(document(row("42", false)), document(row("42", true)));
                calls.throwAtFactory = factoryFailure ? stage : -1;
                calls.throwAtEnqueue = factoryFailure ? -1 : stage;
                TextResult result = new TextResult();
                new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue))
                        .load("42", "User", result);
                calls.time.advanceBy(500L);
                assertEquals(1, result.errors);
                assertEquals(0, result.successes);
                assertFalse(result.error.contains("SENTINEL"));
                assertEquals(0, calls.active);
                assertEquals(factoryFailure ? stage : stage + 1, calls.calls.size());
            }
        }
    }

    @Test
    public void cancellationDoesNotWaitForABlockedResponseRead() throws Exception {
        FakeCalls calls = new FakeCalls();
        PageResult result = new PageResult();
        SummaryController.Cancelable load = new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue)
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
                calls.calls.get(0).respond(ResponseBody.create(MediaType.get("application/json; charset=UTF-8"),
                        -1, blocked));
                return null;
            });
            assertTrue(reading.await(2, TimeUnit.SECONDS));
            executor.submit(load::cancel).get(1, TimeUnit.SECONDS);
            assertTrue(calls.calls.get(0).canceled);
            PageResult next = new PageResult();
            new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", calls, calls.queue)
                    .loadFirstPage("43", ProfileSummaryLoader.Kind.TOPICS, next);
            calls.time.advanceBy(5_000L);
            assertEquals(1, calls.calls.size());
            assertEquals(1, calls.active);
            release.countDown();
            response.get(2, TimeUnit.SECONDS);
            assertEquals(0, result.errors);
            assertEquals(0, result.successes);
            assertEquals(1, calls.calls.size());
            assertEquals(0, calls.active);
            calls.time.advanceBy(499L);
            assertEquals(1, calls.calls.size());
            calls.time.advanceBy(1L);
            assertEquals(2, calls.calls.size());
            assertEquals(1, calls.maxActive);
            calls.calls.get(1).respond(document(row("43", false)));
            assertEquals(1, next.successes);
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
    public void replyTransportAndParserFailuresStopWithoutAPartialTopicSample() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            JSONObject second = row("42", false);
            second.put("tid", "7301");
            MockResponse[] failures = {
                    new MockResponse().setResponseCode(302).setHeader("Location", "https://untrusted.example/")
                            .setBody("RAW_ERROR_SENTINEL"),
                    new MockResponse().setResponseCode(403).setBody("RAW_ERROR_SENTINEL"),
                    gbkResponse("{\"data\":{\"__MESSAGE\":\"DENIAL_SENTINEL\"}}"),
                    gbkResponse(document(row("99", true))),
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
    public void replyDeadlineTerminatesCollectionWithoutProducingAPartialPrompt() throws Exception {
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
    public void immediate503RetryHintCannotBypassTheQueueOrReplaceTheFirstError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
                    .setBody("FIRST_503_BODY_SENTINEL"));
            server.enqueue(gbkResponse(document(row("42", false))));
            OkHttpClient client = localClient(server, 5_000L);
            FakeTime time = new FakeTime();
            ProfileRequestQueue queue = time.queue();
            try {
                TextResult first = new TextResult();
                new ProfileSummaryLoader(new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", client, queue))
                        .load("42", "User", first);
                assertTrue(first.finished.await(5, TimeUnit.SECONDS));
                assertEquals(1, first.errors);
                assertEquals(0, first.successes);
                assertTrue(first.error.contains("服务暂时不可用"));
                assertFalse(first.error.contains("SENTINEL"));
                assertEquals(1, server.getRequestCount());
                RecordedRequest original = server.takeRequest(1, TimeUnit.SECONDS);
                assertNotNull(original);
                assertEquals("GET", original.getMethod());
                assertEquals(0L, original.getBodySize());

                PageResult next = new PageResult();
                new NgaProfilePageSource("https://bbs.nga.cn", "", "UA", client, queue)
                        .loadFirstPage("42", ProfileSummaryLoader.Kind.TOPICS, next);
                time.advanceBy(499L);
                assertEquals(1, server.getRequestCount());
                assertEquals(0, next.successes);
                time.advanceBy(1L);
                assertTrue(next.finished.await(5, TimeUnit.SECONDS));
                assertEquals(1, next.successes);
                assertEquals(0, next.errors);
                assertEquals(2, server.getRequestCount());
            } finally {
                close(client);
            }
        }
    }

    @Test
    public void profileTransportPreservesFailureStatusBodiesAnd429RetryMetadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
                    .setBody("FIRST_503_BODY"));
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "7")
                    .setBody("FIRST_429_BODY"));
            OkHttpClient client = localClient(server, 5_000L);
            Request request = NgaProfilePageSource.buildRequest("https://bbs.nga.cn", "", "UA", "42",
                    ProfileSummaryLoader.Kind.TOPICS);
            try {
                try (Response response = client.newCall(request).execute()) {
                    assertEquals(503, response.code());
                    assertEquals("FIRST_503_BODY", response.body().string());
                }
                assertEquals(1, server.getRequestCount());
                try (Response response = client.newCall(request).execute()) {
                    assertEquals(429, response.code());
                    assertEquals("7", response.header("Retry-After"));
                    assertEquals("FIRST_429_BODY", response.body().string());
                }
                assertEquals(2, server.getRequestCount());
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
        return NgaProfilePageSource.newClient().newBuilder()
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
        final FakeTime time = new FakeTime();
        final ProfileRequestQueue queue = time.queue();
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
        long startedAt;
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
            startedAt = owner.time.getAsLong();
            owner.active++;
            owner.maxActive = Math.max(owner.maxActive, owner.active);
            if (canceled) {
                fail(new IOException("Synthetic canceled call"));
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
            respond(ResponseBody.create(MediaType.get("application/json; charset=UTF-8"), bytes.size(), bytes));
        }

        void respond(ResponseBody body) throws IOException {
            BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        complete();
                    }
                }
            });
            ResponseBody tracked = ResponseBody.create(body.contentType(), body.contentLength(), source);
            callback.onResponse(this, new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").header("Content-Type", "application/json; charset=UTF-8")
                    .body(tracked).build());
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
