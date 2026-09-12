package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import okio.GzipSink;

public class AiModelsClientTest {
    private static final String API_KEY = "synthetic-draft-key";
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
    public void productionHttpLookupNeedsOnlyDraftEndpointAndKey() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"id\":\"vendor/model\"},"
                + "{\"id\":\"示例模型\"},{\"id\":\"vendor/model\"}]}"));
        Result result = new Result();
        Call call = client().listModels(endpoint(), " " + API_KEY + " ", result);
        result.await();
        assertEquals(Arrays.asList("vendor/model", "示例模型"), result.models);
        assertNull(result.error);
        assertFalse(Thread.currentThread() == result.callbackThread);
        assertThrows(UnsupportedOperationException.class, () -> result.models.add("extra"));
        RecordedRequest request = takeRequest(server);
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/models", request.getPath());
        assertEquals("Bearer " + API_KEY, request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertNull(request.getHeader("Content-Type"));
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("X-User-Agent"));
        assertNull(request.getHeader("Proxy-Authorization"));
        assertEquals(0, request.getBodySize());
        assertNull(call.request().body());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void baseCompleteAndCustomUrlsKeepTheirPrefixEncodingAndPort() throws Exception {
        String[][] paths = {
                {"/", "/models"},
                {"/chat/completions", "/models"},
                {"/v1", "/v1/models"},
                {"/v1/chat/completions///", "/v1/models"},
                {"/custom/openai/v2/", "/custom/openai/v2/models"},
                {"/custom/openai/v2/chat/completions", "/custom/openai/v2/models"},
                {"/tenant%2Fname/%E6%A8%A1%E5%9E%8B/v2", "/tenant%2Fname/%E6%A8%A1%E5%9E%8B/v2/models"}
        };
        AiSummaryClient client = client();
        for (String[] path : paths) {
            server.enqueue(success("model"));
            Result result = new Result();
            Call call = client.listModels(server.url(path[0]).toString(), API_KEY, result);
            result.await();
            assertNull(result.error);
            assertEquals(path[1], takeRequest(server).getPath());
            assertEquals(server.getPort(), call.request().url().port());
        }
        assertEquals(paths.length, server.getRequestCount());
    }

    @Test
    public void emptyDataIsASuccessfulImmutableList() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        Result result = new Result();
        client().listModels(endpoint(), API_KEY, result);
        result.await();
        assertEquals(Collections.emptyList(), result.models);
        assertNull(result.error);
        assertThrows(UnsupportedOperationException.class, () -> result.models.add("extra"));
    }

    @Test
    public void invalidDraftFieldsNeverReachTheServer() {
        AiSummaryClient client = client();
        String[] invalidEndpoints = {null, "", "ftp://localhost/v1", "localhost/v1",
                "http://user:synthetic-private-key@localhost/v1", "http://localhost/v1?",
                "http://localhost/v1#", "http://localhost/v1\n", "http://localhost:99999/v1",
                "http://localhost/" + "x".repeat(2048)};
        for (String endpoint : invalidEndpoints) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> client.listModels(endpoint, API_KEY, new Result()));
            assertFalse(error.toString().contains("synthetic-private-key"));
            assertNull(error.getCause());
        }
        String[] invalidKeys = {null, "", "   ", "key with space", "key\r\nCookie: synthetic", "密钥", "x".repeat(4097)};
        for (String key : invalidKeys) {
            assertThrows(IllegalArgumentException.class, () -> client.listModels(endpoint(), key, new Result()));
        }
        assertThrows(IllegalArgumentException.class, () -> client.listModels(endpoint(), API_KEY, null));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void statusesUseFixedErrorsWithoutExposingResponseBodies() throws Exception {
        int[] statuses = {400, 401, 403, 404, 429, 500, 503};
        AiError[] errors = {AiError.INVALID_REQUEST, AiError.AUTHENTICATION, AiError.AUTHENTICATION,
                AiError.ADDRESS, AiError.RATE_LIMIT, AiError.SERVER, AiError.SERVER};
        AiSummaryClient client = client();
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i])
                    .setBody("synthetic-private-error"));
            Result result = new Result();
            client.listModels(endpoint(), API_KEY, result);
            result.await();
            assertEquals(errors[i], result.error);
            assertNull(result.models);
            assertFalse(result.error.getMessage().contains("synthetic-private-error"));
        }
        assertEquals(statuses.length, server.getRequestCount());
    }

    @Test
    public void redirectsNeverForwardTheKeyToAnotherServer() throws Exception {
        MockWebServer destination = new MockWebServer();
        destination.start();
        try {
            AiSummaryClient client = client();
            for (int status : new int[]{301, 302, 303, 307, 308}) {
                server.enqueue(new MockResponse().setResponseCode(status)
                        .addHeader("Location", destination.url("/stolen")));
                Result result = new Result();
                client.listModels(endpoint(), API_KEY, result);
                result.await();
                assertEquals(AiError.ADDRESS, result.error);
                assertNull(result.models);
            }
            assertEquals(5, server.getRequestCount());
            assertEquals(0, destination.getRequestCount());
        } finally {
            destination.shutdown();
        }
    }

    @Test
    public void eachDraftUsesItsOwnKeyWithoutSystemOrResponseCookies() throws Exception {
        MockWebServer otherService = new MockWebServer();
        otherService.start();
        CookieHandler previous = CookieHandler.getDefault();
        CookieManager manager = new CookieManager();
        manager.getCookieStore().add(server.url("/").uri(), new HttpCookie("ngaPassportUid", "synthetic-user"));
        CookieHandler.setDefault(manager);
        try {
            AiSummaryClient client = client();
            server.enqueue(success("first").addHeader("Set-Cookie", "ngaPassportCid=synthetic-session; Path=/"));
            otherService.enqueue(success("second"));
            server.enqueue(success("third"));
            MockWebServer[] services = {server, otherService, server};
            for (int i = 0; i < services.length; i++) {
                String key = "synthetic-key-" + i;
                Result result = new Result();
                client.listModels(services[i].url("/v1").toString(), key, result);
                result.await();
                assertNull(result.error);
                RecordedRequest request = takeRequest(services[i]);
                assertEquals("Bearer " + key, request.getHeader("Authorization"));
                assertNull(request.getHeader("Cookie"));
                assertNull(request.getHeader("Proxy-Authorization"));
            }
        } finally {
            CookieHandler.setDefault(previous);
            otherService.shutdown();
        }
    }

    @Test
    public void authenticationChallengeDoesNotUseSystemCredentialsOrRetry() throws Exception {
        // The Android compile stubs omit this JVM method.
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
            client().listModels(endpoint(), API_KEY, result);
            result.await();
            assertEquals(AiError.AUTHENTICATION, result.error);
            assertEquals(0, authentications.get());
            assertEquals(1, server.getRequestCount());
        } finally {
            java.net.Authenticator.setDefault(previous);
        }
    }

    @Test
    public void cancellationTerminatesInflightDiscoveryWithoutSuccess() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        Call call = client().listModels(endpoint(), API_KEY, result);
        takeRequest(server);
        call.cancel();
        result.await();
        assertTrue(call.isCanceled());
        assertEquals(AiError.CANCELLED, result.error);
        assertNull(result.models);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void stalledDiscoveryIsATimeoutRatherThanUserCancellation() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        deadlineClient().listModels(endpoint(), API_KEY, result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertNull(result.models);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void bodyReadTimeoutCannotBecomeSuccessOrCancellation() throws Exception {
        server.enqueue(success("delayed").setBodyDelay(2, TimeUnit.SECONDS));
        Result result = new Result();
        deadlineClient().listModels(endpoint(), API_KEY, result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertNull(result.models);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void malformedEmptyNonModelAndInvalidUtf8ResponsesAreErrors() throws Exception {
        MockResponse[] responses = {
                new MockResponse().setBody("{malformed synthetic-private-error"),
                new MockResponse().setResponseCode(204),
                new MockResponse().setBody(new Buffer().write(new byte[]{(byte) 0xc3, 0x28})),
                new MockResponse().setBody(AiResponseParserTest.response("not a model list")),
                new MockResponse().setBody("{\"data\":[{\"id\":1}]}")
        };
        AiSummaryClient client = client();
        for (MockResponse response : responses) {
            server.enqueue(response);
            Result result = new Result();
            client.listModels(endpoint(), API_KEY, result);
            result.await();
            assertEquals(AiError.INVALID_RESPONSE, result.error);
            assertNull(result.models);
        }
    }

    @Test
    public void knownChunkedAndDecompressedResponsesAreBounded() throws Exception {
        String oversized = "x".repeat(AiSummaryClient.MAX_RESPONSE_BYTES + 1);
        Buffer compressed = new Buffer();
        try (GzipSink gzip = new GzipSink(compressed)) {
            Buffer uncompressed = new Buffer().writeUtf8(oversized);
            gzip.write(uncompressed, uncompressed.size());
        }
        MockResponse[] responses = {
                new MockResponse().setBody(oversized),
                new MockResponse().setChunkedBody(oversized, 4096),
                new MockResponse().addHeader("Content-Encoding", "gzip").setBody(compressed)
        };
        AiSummaryClient client = client();
        for (MockResponse response : responses) {
            server.enqueue(response);
            Result result = new Result();
            client.listModels(endpoint(), API_KEY, result);
            result.await();
            assertEquals(AiError.RESPONSE_TOO_LARGE, result.error);
            assertNull(result.models);
        }
    }

    private String endpoint() {
        return server.url("/v1").toString();
    }

    private AiSummaryClient client() {
        AiSummaryClient client = new AiSummaryClient();
        clients.add(client);
        return client;
    }

    private AiSummaryClient deadlineClient() {
        AiSummaryClient client = new AiSummaryClient(server.url("/v1/chat/completions"), 500);
        clients.add(client);
        return client;
    }

    private static RecordedRequest takeRequest(MockWebServer server) throws Exception {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull("Expected one local model-list request", request);
        return request;
    }

    private static MockResponse success(String model) {
        return new MockResponse().addHeader("Content-Type", "application/json")
                .setBody(AiResponseParserTest.modelResponse(model));
    }

    private static final class Result implements AiSummaryClient.ModelsCallback {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger callbacks = new AtomicInteger();
        volatile List<String> models;
        volatile AiError error;
        volatile Thread callbackThread;

        @Override
        public void onSuccess(List<String> value) {
            models = value;
            callbackThread = Thread.currentThread();
            callbacks.incrementAndGet();
            completed.countDown();
        }

        @Override
        public void onError(AiError value) {
            error = value;
            callbackThread = Thread.currentThread();
            callbacks.incrementAndGet();
            completed.countDown();
        }

        void await() throws Exception {
            assertTrue("Expected a bounded model-list callback", completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
        }
    }
}
