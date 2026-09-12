package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import okio.GzipSink;
import sp.phone.ai.summary.ProfileSummaryInput;

public class AiSummaryClientTest {
    private final AiConfig config = new AiConfig("https://unused.example.test/v1", "synthetic-test-key", "synthetic-model");
    private final List<AiSummaryClient> clients = new ArrayList<>();
    private MockWebServer server;

    @Before
    public void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void stopServer() throws Exception {
        for (AiSummaryClient client : clients) {
            client.transportForTest().dispatcher().cancelAll();
            client.transportForTest().connectionPool().evictAll();
            client.transportForTest().dispatcher().executorService().shutdownNow();
        }
        server.shutdown();
    }

    @Test
    public void requestsOneUtf8StreamWithTheSharedTokenBudgetAndWithoutNgaHeaders() throws Exception {
        server.enqueue(success("测试总结"));
        Result result = new Result();
        String prompt = "测试楼层：只有当前行\n包含\"引号\"与表情🙂";
        Call call = client(5_000).summarize(config, prompt, result);
        result.await();
        assertEquals("测试总结", result.text);
        assertNull(result.error);
        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/chat/completions", request.getPath());
        assertEquals("Bearer synthetic-test-key", request.getHeader("Authorization"));
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
        assertEquals("text/event-stream, application/json", request.getHeader("Accept"));
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("X-User-Agent"));
        assertNull(request.getHeader("Proxy-Authorization"));
        JSONObject body = SafeJsonParser.parseObject(request.getBody().readUtf8());
        assertEquals("synthetic-model", body.get("model"));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertGenerationOptions(body);
        assertEquals(prompt, body.getJSONArray("messages").getJSONObject(0).get("content"));
        assertEquals(1, body.getJSONArray("messages").size());
        assertTrue(call.request().body().isOneShot());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void productionTransportSendsHttpSummariesToTheConfiguredCustomEndpoint() throws Exception {
        server.enqueue(success("HTTP summary"));
        AiSummaryClient client = new AiSummaryClient();
        clients.add(client);
        AiConfig httpConfig = new AiConfig(server.url("/custom/v2/").toString(), "synthetic-http-key", "http-model");
        Result result = new Result();
        Call call = client.summarize(httpConfig, "synthetic floor", result);
        result.await();
        assertEquals("HTTP summary", result.text);
        assertNull(result.error);
        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/custom/v2/chat/completions", request.getPath());
        assertEquals(server.getPort(), call.request().url().port());
        assertEquals("Bearer synthetic-http-key", request.getHeader("Authorization"));
        JSONObject body = SafeJsonParser.parseObject(request.getBody().readUtf8());
        assertEquals("http-model", body.get("model"));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertGenerationOptions(body);
        assertTrue(call.request().body().isOneShot());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void connectionTestKeepsTheShortFixedInputAndSharedTokenBudget() throws Exception {
        server.enqueue(success("连接成功"));
        Result result = new Result();
        AiConfig custom = new AiConfig(config.getEndpoint(), config.getApiKey(), config.getModel(),
                new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, "CUSTOM_CONNECTION_SENTINEL\nSecond line"));
        client(5_000).testConnection(custom, result);
        result.await();
        assertEquals("连接成功", result.text);
        String json = takeRequest().getBody().readUtf8();
        JSONObject body = SafeJsonParser.parseObject(json);
        assertEquals("请只回复：连接成功", body.getJSONArray("messages").getJSONObject(0).get("content"));
        assertEquals(Boolean.FALSE, body.get("stream"));
        assertGenerationOptions(body);
        assertFalse(json.contains(config.getApiKey()));
    }

    @Test
    public void streamsCumulativeProgressBeforeCompletionAndKeepsReasoningSeparate() throws Exception {
        String first = AiStreamParserTest.event(AiStreamParserTest.chunk("first ", "synthetic thought", null));
        String last = AiStreamParserTest.event(AiStreamParserTest.chunk("answer", null, "stop"));
        server.enqueue(stream(first + last).throttleBody(first.getBytes(StandardCharsets.UTF_8).length,
                2, TimeUnit.SECONDS));
        Result result = new Result();
        client(5_000).summarize(config, "synthetic floor", result);
        result.awaitProgress();
        assertEquals("first ", result.firstAnswer);
        assertEquals("synthetic thought", result.firstReasoning);
        assertTrue(result.firstProgressBeforeCompletion);
        result.await();
        assertEquals("first answer", result.text);
        assertEquals(result.text, result.answer);
        assertEquals("synthetic thought", result.reasoning);
        assertNull(result.error);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void longStreamExceedsTheFormerTransportCapWithoutLosingTheCompleteReply() throws Exception {
        String answer = "完整回复🙂".repeat(500);
        String metadata = AiStreamParserTest.event("{\"choices\":[],\"usage\":{\"completion_tokens\":5},"
                + "\"padding\":\"" + "x".repeat(4096) + "\"}");
        String body = AiStreamParserTest.event(AiStreamParserTest.chunk(answer, null, null))
                + metadata.repeat(70) + AiStreamParserTest.event("[DONE]");
        assertTrue(body.getBytes(StandardCharsets.UTF_8).length > AiSummaryClient.MAX_RESPONSE_BYTES);
        server.enqueue(stream(body));
        Result result = new Result();
        client(5_000).summarize(config, "synthetic floor", result);
        result.await();
        assertEquals(answer, result.text);
        assertEquals(answer, result.answer);
        assertNull(result.error);
    }

    @Test
    public void jsonFallbackPublishesReasoningAndRetainsTheEntireAnswer() throws Exception {
        String answer = " " + "完整回答".repeat(500) + "\n";
        String body = "{\"choices\":[{\"message\":{\"content\":"
                + com.alibaba.fastjson.JSON.toJSONString(answer)
                + ",\"reasoning\":\"synthetic thought\"}}]}";
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(body));
        Result result = new Result();
        client(5_000).summarize(config, "synthetic floor", result);
        result.await();
        assertEquals(answer, result.text);
        assertEquals(answer, result.answer);
        assertEquals("synthetic thought", result.reasoning);
        assertNull(result.error);
    }

    @Test
    public void incompleteExhaustedAndMalformedStreamsRetainEarlierProgress() throws Exception {
        String first = AiStreamParserTest.event(AiStreamParserTest.chunk("partial answer", "synthetic thought", null));
        String[] tails = {"", AiStreamParserTest.event(AiStreamParserTest.chunk(null, null, "length")),
                AiStreamParserTest.event("{malformed synthetic-private-value")};
        AiError[] expected = {AiError.INTERRUPTED_RESPONSE, AiError.OUTPUT_EXHAUSTED, AiError.INVALID_RESPONSE};
        AiSummaryClient client = client(5_000);
        for (int i = 0; i < tails.length; i++) {
            server.enqueue(stream(first + tails[i]));
            Result result = new Result();
            client.summarize(config, "synthetic floor", result);
            result.await();
            assertEquals(expected[i], result.error);
            assertNull(result.text);
            assertEquals("partial answer", result.answer);
            assertEquals("synthetic thought", result.reasoning);
        }
        assertEquals(tails.length, server.getRequestCount());
    }

    @Test
    public void streamingTimeoutFlushesTheLastCoalescedSnapshotAndNeverBecomesCancellation() throws Exception {
        String first = AiStreamParserTest.event(AiStreamParserTest.chunk("partial answer", null, null));
        String pending = first + AiStreamParserTest.event(AiStreamParserTest.chunk("; final delta", "reasoning tail", null));
        server.enqueue(stream(pending + AiStreamParserTest.event("[DONE]"))
                .throttleBody(pending.getBytes(StandardCharsets.UTF_8).length, 2, TimeUnit.SECONDS));
        Result result = new Result();
        client(500).summarize(config, "synthetic floor", result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertEquals("partial answer; final delta", result.answer);
        assertEquals("reasoning tail", result.reasoning);
        assertNull(result.text);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void cancellingAStreamAfterProgressCannotPublishSuccess() throws Exception {
        String first = AiStreamParserTest.event(AiStreamParserTest.chunk("partial answer", null, null));
        server.enqueue(stream(first + AiStreamParserTest.event("[DONE]"))
                .throttleBody(first.getBytes(StandardCharsets.UTF_8).length, 2, TimeUnit.SECONDS));
        Result result = new Result();
        Call call = client(5_000).summarize(config, "synthetic floor", result);
        result.awaitProgress();
        call.cancel();
        result.await();
        assertEquals(AiError.CANCELLED, result.error);
        assertEquals("partial answer", result.answer);
        assertNull(result.text);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void decompressedStreamBytesHaveAnIndependentOperationalLimit() throws Exception {
        String frame = ":" + "x".repeat(1022) + "\n\n";
        String oversized = frame.repeat(AiSummaryClient.MAX_SUMMARY_RESPONSE_BYTES / frame.length() + 1);
        Buffer bytes = new Buffer().writeUtf8(oversized);
        Buffer compressed = new Buffer();
        try (GzipSink gzip = new GzipSink(compressed)) {
            gzip.write(bytes, bytes.size());
        }
        assertTrue(compressed.size() < AiSummaryClient.MAX_RESPONSE_BYTES);
        server.enqueue(new MockResponse().addHeader("Content-Type", "text/event-stream")
                .addHeader("Content-Encoding", "gzip").setBody(compressed));
        Result result = new Result();
        client(5_000).summarize(config, "synthetic floor", result);
        result.await();
        assertEquals(AiError.RESPONSE_TOO_LARGE, result.error);
        assertNull(result.text);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void systemAndResponseCookiesAreNeverSent() throws Exception {
        CookieHandler previous = CookieHandler.getDefault();
        CookieManager manager = new CookieManager();
        manager.getCookieStore().add(server.url("/").uri(), new HttpCookie("ngaPassportUid", "synthetic-user"));
        CookieHandler.setDefault(manager);
        try {
            AiSummaryClient client = client(5_000);
            server.enqueue(success("first").addHeader("Set-Cookie", "ngaPassportCid=synthetic-session; Path=/"));
            server.enqueue(success("second"));
            for (int i = 0; i < 2; i++) {
                Result result = new Result();
                client.testConnection(config, result);
                result.await();
                assertNull(takeRequest().getHeader("Cookie"));
            }
        } finally {
            CookieHandler.setDefault(previous);
        }
    }

    @Test
    public void authenticationChallengeDoesNotUseSystemCredentialsOrRetry() throws Exception {
        // The Android compile stubs omit this JVM method; reflection keeps the host test portable.
        java.net.Authenticator previous = (java.net.Authenticator) java.net.Authenticator.class
                .getMethod("getDefault").invoke(null);
        AtomicInteger authentications = new AtomicInteger();
        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                authentications.incrementAndGet();
                return new PasswordAuthentication("synthetic-user", "synthetic-password".toCharArray());
            }
        });
        try {
            server.enqueue(new MockResponse().setResponseCode(401)
                    .addHeader("WWW-Authenticate", "Basic realm=local")
                    .setBody("synthetic-private-error"));
            Result result = new Result();
            client(5_000).testConnection(config, result);
            result.await();
            assertEquals(AiError.AUTHENTICATION, result.error);
            assertEquals(0, authentications.get());
            assertEquals(1, server.getRequestCount());
        } finally {
            java.net.Authenticator.setDefault(previous);
        }
    }

    @Test
    public void classifiesHttpFailuresWithoutExposingServerBody() throws Exception {
        int[] statuses = {400, 401, 403, 404, 429, 500, 503};
        AiError[] errors = {AiError.INVALID_REQUEST, AiError.AUTHENTICATION, AiError.AUTHENTICATION,
                AiError.ADDRESS, AiError.RATE_LIMIT, AiError.SERVER, AiError.SERVER};
        AiSummaryClient client = client(5_000);
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i]).setBody("synthetic-private-error"));
            Result result = new Result();
            client.testConnection(config, result);
            result.await();
            assertEquals(errors[i], result.error);
            assertNull(result.text);
            assertFalse(result.error.getMessage().contains("synthetic-private-error"));
        }
        assertEquals(statuses.length, server.getRequestCount());
    }

    @Test
    public void serviceUnavailableWithImmediateRetryHeaderIsStillSentOnlyOnce() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).addHeader("Retry-After", "0"));
        server.enqueue(success("explicit retry"));
        AiSummaryClient client = client(5_000);
        Result first = new Result();
        client.summarize(config, "synthetic floor", first);
        first.await();
        assertEquals(AiError.SERVER, first.error);
        assertEquals(1, server.getRequestCount());

        Result second = new Result();
        client.summarize(config, "synthetic floor", second);
        second.await();
        assertEquals("explicit retry", second.text);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void redirectsDoNotForwardTheKeyOrBody() throws Exception {
        MockWebServer destination = new MockWebServer();
        destination.start();
        try {
            for (int status : new int[]{302, 307, 308}) {
                server.enqueue(new MockResponse().setResponseCode(status)
                        .addHeader("Location", destination.url("/stolen")));
                Result result = new Result();
                client(5_000).summarize(config, "synthetic floor", result);
                result.await();
                assertEquals(AiError.ADDRESS, result.error);
            }
            assertEquals(3, server.getRequestCount());
            assertEquals(0, destination.getRequestCount());
        } finally {
            destination.shutdown();
        }
    }

    @Test
    public void cancellationTerminatesAnInflightCallWithNoSuccess() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        Call call = client(5_000).testConnection(config, result);
        takeRequest();
        call.cancel();
        result.await();
        assertTrue(call.isCanceled());
        assertEquals(AiError.CANCELLED, result.error);
        assertNull(result.text);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void stalledCallIsATimeoutRatherThanUserCancellation() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        client(500).testConnection(config, result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void bodyReadTimeoutCannotBecomeSuccessOrCancellation() throws Exception {
        server.enqueue(success("delayed").setBodyDelay(2, TimeUnit.SECONDS));
        Result result = new Result();
        client(500).testConnection(config, result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertNull(result.text);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void disconnectIsANetworkErrorAndIsNotRetried() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(success("must not retry"));
        Result result = new Result();
        client(5_000).testConnection(config, result);
        result.await();
        assertEquals(AiError.NETWORK, result.error);
        assertTrue(server.getRequestCount() <= 1);
    }

    @Test
    public void malformedEmptyAndInvalidUtf8ResponsesAreErrors() throws Exception {
        MockResponse[] responses = {
                new MockResponse().setBody("{malformed synthetic-private-error"),
                new MockResponse().setResponseCode(204),
                new MockResponse().setBody(new Buffer().write(new byte[]{(byte) 0xc3, 0x28})),
                new MockResponse().setBody("{\"choices\":[]}")
        };
        AiSummaryClient client = client(5_000);
        for (MockResponse response : responses) {
            server.enqueue(response);
            Result result = new Result();
            client.testConnection(config, result);
            result.await();
            assertEquals(AiError.INVALID_RESPONSE, result.error);
            assertNull(result.text);
        }
    }

    @Test
    public void bothKnownAndChunkedOversizedBodiesAreRejectedBeforeParsing() throws Exception {
        String oversized = "x".repeat(AiSummaryClient.MAX_RESPONSE_BYTES + 1);
        MockResponse[] responses = {
                new MockResponse().setBody(oversized),
                new MockResponse().setChunkedBody(oversized, 4096)
        };
        AiSummaryClient client = client(5_000);
        for (MockResponse response : responses) {
            server.enqueue(response);
            Result result = new Result();
            client.testConnection(config, result);
            result.await();
            assertEquals(AiError.RESPONSE_TOO_LARGE, result.error);
            assertNull(result.text);
        }
    }

    @Test
    public void productionClientHasHttpAndTlsWithoutCookieAuthenticationLoggingOrRetryHooks() {
        AiSummaryClient client = new AiSummaryClient();
        clients.add(client);
        OkHttpClient transport = client.transportForTest();
        assertSame(CookieJar.NO_COOKIES, transport.cookieJar());
        assertSame(Authenticator.NONE, transport.authenticator());
        assertSame(Authenticator.NONE, transport.proxyAuthenticator());
        assertTrue(transport.interceptors().isEmpty());
        assertTrue(transport.networkInterceptors().isEmpty());
        assertFalse(transport.followRedirects());
        assertFalse(transport.followSslRedirects());
        assertFalse(transport.retryOnConnectionFailure());
        assertNull(transport.cache());
        assertEquals(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT), transport.connectionSpecs());
        assertEquals(60_000, transport.callTimeoutMillis());
        assertEquals(15_000, transport.connectTimeoutMillis());
        assertEquals(45_000, transport.readTimeoutMillis());
        assertEquals(15_000, transport.writeTimeoutMillis());
        OkHttpClient summaries = client.summaryTransportForTest();
        assertEquals(180_000, summaries.callTimeoutMillis());
        assertEquals(60_000, summaries.readTimeoutMillis());
        assertEquals(15_000, summaries.connectTimeoutMillis());
        assertEquals(15_000, summaries.writeTimeoutMillis());
        assertSame(transport.dispatcher(), summaries.dispatcher());
        assertSame(transport.connectionPool(), summaries.connectionPool());
        assertSame(CookieJar.NO_COOKIES, summaries.cookieJar());
        assertSame(Authenticator.NONE, summaries.authenticator());
        assertSame(Authenticator.NONE, summaries.proxyAuthenticator());
        assertTrue(summaries.interceptors().isEmpty());
        assertTrue(summaries.networkInterceptors().isEmpty());
        assertFalse(summaries.followRedirects());
        assertFalse(summaries.followSslRedirects());
        assertFalse(summaries.retryOnConnectionFailure());
        assertThrows(IllegalArgumentException.class,
                () -> new AiSummaryClient(HttpUrl.get("http://remote.example.test/v1"), 500));
    }

    @Test
    public void invalidInputsNeverReachTheServer() {
        AiSummaryClient client = client(5_000);
        assertThrows(IllegalArgumentException.class, () -> client.summarize(null, "input", new Result()));
        assertThrows(IllegalArgumentException.class, () -> client.summarize(config, " ", new Result()));
        assertThrows(IllegalArgumentException.class,
                () -> client.summarize(config, "x".repeat(AiSummaryClient.MAX_PROMPT_CHARS + 1), new Result()));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void maximumProfileMetadataAndRepliesFitBothPresetsWithoutClipping() throws Exception {
        ProfileSummaryInput input = maximumProfileInput();
        AiSummaryClient client = client(5_000);
        for (AiProfilePrompt.Style style : new AiProfilePrompt.Style[]{
                AiProfilePrompt.Style.FORUM_ROAST, AiProfilePrompt.Style.DETAILED}) {
            String prompt = input.toPrompt(new AiProfilePrompt(style, ""));
            assertFalse(prompt.contains("主题正文："));
            assertTrue(prompt.length() <= AiSummaryClient.MAX_PROMPT_CHARS);
            server.enqueue(success("Synthetic summary"));
            Result result = new Result();
            client.summarize(config, prompt, result);
            result.await();
            assertNull(result.error);
            JSONObject request = SafeJsonParser.parseObject(takeRequest().getBody().readUtf8());
            assertEquals(prompt, request.getJSONArray("messages").getJSONObject(0).get("content"));
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void maximumCustomProfileFitsWithoutDroppingInstructionsOrReplies() throws Exception {
        String customText = "  " + "文".repeat(AiProfilePrompt.MAX_CUSTOM_PROMPT_CHARS - 4) + "\n\t";
        AiProfilePrompt custom = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, customText);
        String prompt = maximumProfileInput().toPrompt(custom);
        assertEquals(customText, custom.getInstructions());
        assertTrue(prompt.contains("输出要求：\n" + customText + "\n"));
        assertTrue(prompt.length() <= AiSummaryClient.MAX_PROMPT_CHARS);
        assertFalse(prompt.contains("主题正文："));
        AiSummaryClient client = client(5_000);
        server.enqueue(success("Synthetic summary"));
        Result result = new Result();
        client.summarize(config, prompt, result);
        result.await();
        assertNull(result.error);
        JSONObject request = SafeJsonParser.parseObject(takeRequest().getBody().readUtf8());
        assertEquals(prompt, request.getJSONArray("messages").getJSONObject(0).get("content"));
        assertEquals(1, server.getRequestCount());
    }

    private static ProfileSummaryInput maximumProfileInput() {
        ProfileSummaryInput.Entry entry = new ProfileSummaryInput.Entry("T".repeat(200),
                "B".repeat(80), "D".repeat(32), "文".repeat(ProfileSummaryInput.MAX_BODY_CHARS));
        List<ProfileSummaryInput.Entry> entries = Collections.nCopies(20, entry);
        return new ProfileSummaryInput("1234567890123456789", "N".repeat(100), entries, entries);
    }

    private AiSummaryClient client(long timeoutMillis) {
        AiSummaryClient client = new AiSummaryClient(server.url("/v1/chat/completions"), timeoutMillis);
        clients.add(client);
        return client;
    }

    private RecordedRequest takeRequest() throws Exception {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull("Expected one local test request", request);
        return request;
    }

    private static MockResponse success(String text) {
        return new MockResponse().addHeader("Content-Type", "application/json")
                .setBody(AiResponseParserTest.response(text));
    }

    private static MockResponse stream(String body) {
        return new MockResponse().addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody(body);
    }

    private static void assertGenerationOptions(JSONObject body) {
        assertEquals(new HashSet<>(Arrays.asList("model", "messages", "stream", "max_tokens")), body.keySet());
        assertEquals(10_000, body.get("max_tokens"));
        for (String field : new String[]{"max_completion_tokens", "max_output_tokens",
                "thinking", "enable_thinking", "reasoning", "reasoning_effort"}) {
            assertFalse(body.containsKey(field));
        }
    }

    private static final class Result implements AiSummaryClient.Callback {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final CountDownLatch progressed = new CountDownLatch(1);
        private final AtomicInteger callbacks = new AtomicInteger();
        volatile String text;
        volatile AiError error;
        volatile String answer = "";
        volatile String reasoning = "";
        volatile String firstAnswer;
        volatile String firstReasoning;
        volatile boolean firstProgressBeforeCompletion;

        @Override
        public void onProgress(String answer, String reasoning) {
            this.answer = answer;
            this.reasoning = reasoning;
            if (progressed.getCount() != 0) {
                // Capture on the callback thread; later chunks must not race test assertions.
                firstAnswer = answer;
                firstReasoning = reasoning;
                firstProgressBeforeCompletion = completed.getCount() != 0;
            }
            progressed.countDown();
        }

        @Override
        public void onSuccess(String value) {
            text = value;
            callbacks.incrementAndGet();
            completed.countDown();
        }

        @Override
        public void onError(AiError value) {
            error = value;
            callbacks.incrementAndGet();
            completed.countDown();
        }

        void await() throws Exception {
            assertTrue("Expected a bounded AI callback", completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
        }

        void awaitProgress() throws Exception {
            assertTrue("Expected progress before completion", progressed.await(5, TimeUnit.SECONDS));
        }
    }
}
