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
        assertModelRequest(request, "/v1/models");
        assertNull(call.request().body());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void rootModelRoutesFallBackOnceFor404And405() throws Exception {
        AiSummaryClient client = client();
        for (String path : new String[]{"/"}) {
            for (int status : new int[]{404, 405}) {
                server.enqueue(new MockResponse().setResponseCode(status)
                        .addHeader("Set-Cookie", "synthetic-session=private; Path=/")
                        .setBody("synthetic-private-error"));
                server.enqueue(success("fallback-model"));
                Result result = new Result();
                Call call = client.listModels(server.url(path).toString(), API_KEY, result);
                result.await();
                assertEquals(Collections.singletonList("fallback-model"), result.models);
                assertNull(result.error);
                assertModelRequest(takeRequest(server), "/models");
                assertModelRequest(takeRequest(server), "/v1/models");
                assertEquals("/models", call.request().url().encodedPath());
                assertThrows(UnsupportedOperationException.class, () -> result.models.add("extra"));
            }
        }
        assertEquals(4, server.getRequestCount());
    }

    @Test
    public void rootHtmlDocumentsFallBackRegardlessOfContentType() throws Exception {
        String[] documents = {
                "\ufeff \r\n<!DOCTYPE HTML PUBLIC \"synthetic\"><html></html>",
                " \t\n<HTML lang=\"zh\"><body>页面</body></HTML>",
                "<html/>"
        };
        AiSummaryClient client = client();
        for (int i = 0; i < documents.length; i++) {
            server.enqueue(new MockResponse().setBody(documents[i])
                    .addHeader("Content-Type", i % 2 == 0 ? "text/html" : "application/json"));
            server.enqueue(success("discovered"));
            Result result = new Result();
            client.listModels(server.url("/").toString(), API_KEY, result);
            result.await();
            assertEquals(Collections.singletonList("discovered"), result.models);
            assertNull(result.error);
            assertModelRequest(takeRequest(server), "/models");
            assertModelRequest(takeRequest(server), "/v1/models");
        }
        assertEquals(documents.length * 2, server.getRequestCount());
    }

    @Test
    public void validRootJsonWithHtmlContentTypeDoesNotFallBack() throws Exception {
        server.enqueue(success("示例<html>").setHeader("Content-Type", "text/html; charset=iso-8859-1"));
        Result result = new Result();
        client().listModels(server.url("/").toString(), API_KEY, result);
        result.await();
        assertEquals(Collections.singletonList("示例<html>"), result.models);
        assertNull(result.error);
        assertModelRequest(takeRequest(server), "/models");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void partialHtmlTokensAndDocumentsOutsideThePrefixDoNotFallBack() throws Exception {
        String[] bodies = {
                "<htmlish>not an HTML document</htmlish>",
                "<!doctype htmlish><html></html>",
                "<html",
                " ".repeat(4096) + "<html></html>"
        };
        AiSummaryClient client = client();
        for (String body : bodies) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody(body));
            Result result = new Result();
            client.listModels(server.url("/").toString(), API_KEY, result);
            result.await();
            assertEquals(AiError.INVALID_RESPONSE, result.error);
            assertNull(result.models);
            assertModelRequest(takeRequest(server), "/models");
        }
        assertEquals(bodies.length, server.getRequestCount());
    }

    @Test
    public void explicitCustomAndEncodedPrefixesNeverFallBack() throws Exception {
        String[][] paths = {
                {"/v1", "/v1/models"},
                {"/custom/openai/v2/", "/custom/openai/v2/models"},
                {"/tenant%2Fname/%E6%A8%A1%E5%9E%8B", "/tenant%2Fname/%E6%A8%A1%E5%9E%8B/models"}
        };
        int[] statuses = {404, 405, 200};
        AiError[] errors = {AiError.ADDRESS, AiError.INVALID_REQUEST, AiError.INVALID_RESPONSE};
        AiSummaryClient client = client();
        for (String[] path : paths) {
            for (int i = 0; i < statuses.length; i++) {
                server.enqueue(new MockResponse().setResponseCode(statuses[i])
                        .setHeader("Content-Type", "text/html").setBody("<!doctype html><html></html>"));
                Result result = new Result();
                client.listModels(server.url(path[0]).toString(), API_KEY, result);
                result.await();
                assertEquals(errors[i], result.error);
                assertNull(result.models);
                assertModelRequest(takeRequest(server), path[1]);
            }
        }
        assertEquals(paths.length * statuses.length, server.getRequestCount());
    }

    @Test
    public void emptyDataIsASuccessfulImmutableList() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        Result result = new Result();
        client().listModels(server.url("/").toString(), API_KEY, result);
        result.await();
        assertEquals(Collections.emptyList(), result.models);
        assertNull(result.error);
        assertThrows(UnsupportedOperationException.class, () -> result.models.add("extra"));
        assertModelRequest(takeRequest(server), "/models");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void invalidDraftFieldsNeverReachTheServer() {
        AiSummaryClient client = client();
        String[] invalidEndpoints = {"http://user:synthetic-private-key@localhost/v1"};
        for (String endpoint : invalidEndpoints) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> client.listModels(endpoint, API_KEY, new Result()));
            assertFalse(error.toString().contains("synthetic-private-key"));
            assertNull(error.getCause());
        }
        String[] invalidKeys = {"key\r\nCookie: synthetic"};
        for (String key : invalidKeys) {
            assertThrows(IllegalArgumentException.class, () -> client.listModels(endpoint(), key, new Result()));
        }
        assertThrows(IllegalArgumentException.class, () -> client.listModels(endpoint(), API_KEY, null));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void failedFallbackUsesExistingStatusErrorsWithoutAnotherAttempt() throws Exception {
        int[] statuses = {404, 405};
        AiError[] errors = {AiError.ADDRESS, AiError.INVALID_REQUEST};
        AiSummaryClient client = client();
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setResponseCode(statuses[i])
                    .setHeader("Retry-After", "0").setBody("synthetic-private-error"));
            Result result = new Result();
            client.listModels(server.url("/").toString(), API_KEY, result);
            result.await();
            assertEquals(errors[i], result.error);
            assertNull(result.models);
            assertFalse(result.error.getMessage().contains("synthetic-private-error"));
            assertModelRequest(takeRequest(server), "/models");
            assertModelRequest(takeRequest(server), "/v1/models");
        }
        assertEquals(statuses.length * 2, server.getRequestCount());
    }

    @Test
    public void serviceUnavailableCannotConsumeAnExtraDiscoveryResponse() throws Exception {
        AiSummaryClient client = client();
        for (boolean fallback : new boolean[]{false, true}) {
            if (fallback) {
                server.enqueue(new MockResponse().setResponseCode(404));
            }
            server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0"));
            server.enqueue(success("deliberate-next-call"));
            Result failure = new Result();
            client.listModels(server.url("/").toString(), API_KEY, failure);
            failure.await();
            assertEquals(AiError.SERVER, failure.error);
            assertNull(failure.models);
            assertModelRequest(takeRequest(server), "/models");
            if (fallback) {
                assertModelRequest(takeRequest(server), "/v1/models");
            }
            Result next = new Result();
            client.listModels(endpoint(), API_KEY, next);
            next.await();
            assertEquals(Collections.singletonList("deliberate-next-call"), next.models);
            assertNull(next.error);
            assertModelRequest(takeRequest(server), "/v1/models");
        }
        assertEquals(5, server.getRequestCount());
    }

    @Test
    public void redirectsNeverForwardTheKeyToAnotherServer() throws Exception {
        MockWebServer destination = new MockWebServer();
        destination.start();
        try {
            AiSummaryClient client = client();
            for (boolean fallback : new boolean[]{false, true}) {
                for (int status : new int[]{302, 307}) {
                    if (fallback) {
                        server.enqueue(new MockResponse().setResponseCode(404));
                    }
                    server.enqueue(new MockResponse().setResponseCode(status)
                            .addHeader("Location", destination.url("/stolen")));
                    Result result = new Result();
                    client.listModels(server.url("/").toString(), API_KEY, result);
                    result.await();
                    assertEquals(AiError.ADDRESS, result.error);
                    assertNull(result.models);
                    assertModelRequest(takeRequest(server), "/models");
                    if (fallback) {
                        assertModelRequest(takeRequest(server), "/v1/models");
                    }
                }
            }
            assertEquals(6, server.getRequestCount());
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
    public void cancellingTheReturnedCallAlsoStopsTheFallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        Call call = client().listModels(server.url("/").toString(), API_KEY, result);
        assertModelRequest(takeRequest(server), "/models");
        assertModelRequest(takeRequest(server), "/v1/models");
        call.cancel();
        result.await();
        assertTrue(call.isCanceled());
        assertEquals(AiError.CANCELLED, result.error);
        assertNull(result.models);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void bodyReadTimeoutCannotBecomeSuccessOrCancellation() throws Exception {
        // The root route peeks in the interceptor; the explicit route reads in the parser.
        for (String path : new String[]{"/", "/v1"}) {
            server.enqueue(success("delayed").setBodyDelay(1, TimeUnit.SECONDS));
            Result result = new Result();
            deadlineClient(path, 500).listModels(server.url(path).toString(), API_KEY, result);
            result.await();
            assertEquals(AiError.TIMEOUT, result.error);
            assertNull(result.models);
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void fallbackSharesTheOriginalTotalTimeoutBudget() throws Exception {
        // Each delay fits the deadline separately; together they must time out.
        server.enqueue(new MockResponse().setResponseCode(404)
                .setHeadersDelay(900, TimeUnit.MILLISECONDS));
        server.enqueue(success("too-late").setBodyDelay(900, TimeUnit.MILLISECONDS));
        Result result = new Result();
        Call call = deadlineClient("/chat/completions", 1500)
                .listModels(server.url("/").toString(), API_KEY, result);
        result.await();
        assertEquals(AiError.TIMEOUT, result.error);
        assertNull(result.models);
        assertTrue(call.isCanceled());
        assertModelRequest(takeRequest(server), "/models");
        assertModelRequest(takeRequest(server), "/v1/models");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void failedHtmlPeekDoesNotStartFallback() throws Exception {
        server.enqueue(new MockResponse().setBody("<html>" + "x".repeat(200))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        Result result = new Result();
        client().listModels(server.url("/").toString(), API_KEY, result);
        result.await();
        assertEquals(AiError.NETWORK, result.error);
        assertNull(result.models);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void malformedEmptyNonModelAndInvalidUtf8ResponsesAreErrors() throws Exception {
        MockResponse[] responses = {
                new MockResponse().setBody("{malformed synthetic-private-error"),
                new MockResponse().setResponseCode(204),
                new MockResponse().setBody(new Buffer().write(new byte[]{(byte) 0xc3, 0x28})),
                new MockResponse().setBody(new Buffer().writeByte(0xff).writeUtf8("<html></html>"))
        };
        AiSummaryClient client = client();
        for (boolean fallback : new boolean[]{false, true}) {
            for (MockResponse response : responses) {
                if (fallback) {
                    server.enqueue(new MockResponse().setResponseCode(404));
                }
                server.enqueue(response);
                Result result = new Result();
                client.listModels(server.url("/").toString(), API_KEY, result);
                result.await();
                assertEquals(AiError.INVALID_RESPONSE, result.error);
                assertNull(result.models);
            }
        }
        assertEquals(responses.length * 3, server.getRequestCount());
    }

    @Test
    public void fallbackHtmlRemainsAnInvalidResponseAndCannotRecurse() throws Exception {
        server.enqueue(new MockResponse().setBody("<!doctype html><html></html>"));
        server.enqueue(new MockResponse().setBody("<!doctype html><html></html>"));
        Result result = new Result();
        client().listModels(server.url("/").toString(), API_KEY, result);
        result.await();
        assertEquals(AiError.INVALID_RESPONSE, result.error);
        assertNull(result.models);
        assertEquals(2, server.getRequestCount());
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
        for (boolean fallback : new boolean[]{false, true}) {
            for (MockResponse response : responses) {
                if (fallback) {
                    server.enqueue(new MockResponse().setResponseCode(404));
                }
                server.enqueue(response);
                Result result = new Result();
                client.listModels(server.url("/").toString(), API_KEY, result);
                result.await();
                assertEquals(AiError.RESPONSE_TOO_LARGE, result.error);
                assertNull(result.models);
            }
        }
        assertEquals(responses.length * 3, server.getRequestCount());
    }

    @Test
    public void rootNonPathFailuresDoNotFallBackEvenWithHtmlBodies() throws Exception {
        int[] statuses = {400, 401, 429, 503};
        AiError[] errors = {AiError.INVALID_REQUEST, AiError.AUTHENTICATION,
                AiError.RATE_LIMIT, AiError.SERVER};
        AiSummaryClient client = client();
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i])
                    .setHeader("Content-Type", "text/html").setHeader("Retry-After", "0")
                    .setBody("<html>synthetic-private-error</html>"));
            Result result = new Result();
            client.listModels(server.url("/").toString(), API_KEY, result);
            result.await();
            assertEquals(errors[i], result.error);
            assertNull(result.models);
            assertFalse(result.error.getMessage().contains("synthetic-private-error"));
            assertModelRequest(takeRequest(server), "/models");
        }
        assertEquals(statuses.length, server.getRequestCount());
    }

    private String endpoint() {
        return server.url("/v1").toString();
    }

    private AiSummaryClient client() {
        AiSummaryClient client = new AiSummaryClient();
        clients.add(client);
        return client;
    }

    private AiSummaryClient deadlineClient(String path, long timeoutMillis) {
        AiSummaryClient client = new AiSummaryClient(server.url(path), timeoutMillis);
        clients.add(client);
        return client;
    }

    private void assertModelRequest(RecordedRequest request, String path) {
        assertEquals("GET", request.getMethod());
        assertEquals(path, request.getPath());
        assertNotNull(request.getRequestUrl());
        assertEquals(server.url("/").scheme(), request.getRequestUrl().scheme());
        assertEquals(server.url("/").host(), request.getRequestUrl().host());
        assertEquals(server.getPort(), request.getRequestUrl().port());
        assertEquals("Bearer " + API_KEY, request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertNull(request.getHeader("Content-Type"));
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("X-User-Agent"));
        assertNull(request.getHeader("Proxy-Authorization"));
        assertEquals(0, request.getBodySize());
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
