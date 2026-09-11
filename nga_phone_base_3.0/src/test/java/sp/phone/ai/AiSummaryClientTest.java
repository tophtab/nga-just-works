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
import java.util.ArrayList;
import java.util.Arrays;
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
    public void sendsOneUtf8NonStreamingRequestWithoutNgaHeaders() throws Exception {
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
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("X-User-Agent"));
        assertNull(request.getHeader("Proxy-Authorization"));
        JSONObject body = SafeJsonParser.parseObject(request.getBody().readUtf8());
        assertEquals("synthetic-model", body.get("model"));
        assertEquals(Boolean.FALSE, body.get("stream"));
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
        assertEquals(1024, body.getIntValue("max_tokens"));
        assertTrue(call.request().body().isOneShot());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void connectionTestHasOnlyAShortFixedInput() throws Exception {
        server.enqueue(success("连接成功"));
        Result result = new Result();
        client(5_000).testConnection(config, result);
        result.await();
        assertEquals("连接成功", result.text);
        String json = takeRequest().getBody().readUtf8();
        JSONObject body = SafeJsonParser.parseObject(json);
        assertEquals("请只回复：连接成功", body.getJSONArray("messages").getJSONObject(0).get("content"));
        assertEquals(8, body.getIntValue("max_tokens"));
        assertFalse(json.contains(config.getApiKey()));
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
        client.testConnection(config, first);
        first.await();
        assertEquals(AiError.SERVER, first.error);
        assertEquals(1, server.getRequestCount());

        Result second = new Result();
        client.testConnection(config, second);
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

    private static final class Result implements AiSummaryClient.Callback {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger callbacks = new AtomicInteger();
        volatile String text;
        volatile AiError error;

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
    }
}
