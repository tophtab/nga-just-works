package sp.phone.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class ProfileLocationTransportTest {

    private final long now = 1_700_000_000_000L;

    @Test
    public void operationClientDisablesRedirectsRetryAndUnboundedWaits() {
        OkHttpClient client = ProfileLocationTransport.newClient();
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertFalse(client.retryOnConnectionFailure());
        assertEquals(20_000, client.callTimeoutMillis());
        assertEquals(10_000, client.readTimeoutMillis());
        assertEquals(10_000, client.connectTimeoutMillis());
        assertTrue(client.interceptors().isEmpty());
        assertEquals(1, client.networkInterceptors().size());
    }

    @Test
    public void actualClientDoesNotRepeat503WithZeroRetryAfterAndPreserves429Metadata() throws Exception {
        for (int status : new int[]{503, 429}) {
            AtomicInteger requests = new AtomicInteger();
            String retryAfter = status == 429 ? "3600" : "0";
            try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
                Thread responder = new Thread(() -> {
                    try {
                        while (!server.isClosed()) {
                            try (Socket socket = server.accept()) {
                                socket.setSoTimeout(5000);
                                BufferedReader input = new BufferedReader(new InputStreamReader(
                                        socket.getInputStream(), StandardCharsets.US_ASCII));
                                String line;
                                while ((line = input.readLine()) != null && !line.isEmpty()) {
                                    // Consume the local fixture's request without retaining/logging headers.
                                }
                                int code = requests.incrementAndGet() == 1 ? status : 200;
                                byte[] body = (code == 503 ? "/*$js$*/<html>验证</html>" : "")
                                        .getBytes(Charset.forName("GBK"));
                                String wire = "HTTP/1.1 " + code + " Offline fixture\r\n"
                                        + "Retry-After: " + retryAfter + "\r\n"
                                        + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                                socket.getOutputStream().write(wire.getBytes(StandardCharsets.US_ASCII));
                                socket.getOutputStream().write(body);
                            }
                        }
                    } catch (IOException ignored) {
                        // Closing the fixture server ends accept after the one expected request.
                    }
                });
                responder.setDaemon(true);
                responder.start();
                OkHttpClient client = ProfileLocationTransport.newClient();
                Request request = new Request.Builder()
                        .url("http://127.0.0.1:" + server.getLocalPort() + "/profile-fixture").build();
                try (Response response = client.newCall(request).execute()) {
                    assertEquals(status, response.code());
                    assertEquals(1, requests.get());
                    if (status == 429) {
                        assertEquals("3600", response.header("Retry-After"));
                        assertEquals(now + 3_600_000L,
                                ProfileLocationTransport.readResponse(response, 42, now).retryAt);
                    } else {
                        assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                                ProfileLocationTransport.readResponse(response, 42, now).kind);
                    }
                } finally {
                    client.connectionPool().evictAll();
                    server.close();
                    responder.join(1000);
                }
            }
        }
    }

    @Test
    public void requestUsesOnlyTheImmutableSnapshotAndEstablishedProfileRoute() throws Exception {
        AtomicReference<Request> captured = new AtomicReference<>();
        AtomicReference<ProfileLocationResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        // The interceptor returns a fixture without calling proceed: no network/DNS is used.
        OkHttpClient offline = ProfileLocationTransport.newClient().newBuilder()
                .addInterceptor(chain -> {
                    captured.set(chain.request());
                    return response(chain.request(), 200, fixtureBody());
                }).build();
        ProfileSession session = ProfileSession.create("https://bbs.nga.cn", "7", "fixture-session", "Fixture UA");
        new ProfileLocationTransport(offline, () -> now).fetch(session, 42, value -> {
            result.set(value);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        Request request = captured.get();
        assertNotNull(request);
        assertEquals("GET", request.method());
        assertEquals("https://bbs.nga.cn/nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid=42", request.url().toString());
        assertEquals("https://bbs.nga.cn/nuke.php?func=ucp&lite=jsx&uid=42", request.header("Referer"));
        assertEquals("ngaPassportUid=7; ngaPassportCid=fixture-session", request.header("Cookie"));
        assertEquals("Fixture UA", request.header("User-Agent"));
        assertEquals("Nga_Official", request.header("X-User-Agent"));
        assertEquals("广东", result.get().location);
        offline.dispatcher().executorService().shutdown();
    }

    @Test
    public void redirectAndAccessResponsesStopWithoutReadingTheirBodies() throws IOException {
        for (int code : new int[]{301, 302, 307, 401, 403, 404}) {
            Response response = response(request(), code, unreadableBody(1)).newBuilder()
                    .header("Location", "https://example.invalid/private").build();
            assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                    ProfileLocationTransport.readResponse(response, 42, now).kind);
        }
        assertEquals(ProfileLocationResult.Kind.FAILURE,
                ProfileLocationTransport.readResponse(response(request(), 503,
                        ResponseBody.create(null, new byte[0])), 42, now).kind);
    }

    @Test
    public void rateLimitPreservesAValidLongerDelayAndNeverParsesTheBody() throws IOException {
        Response response = response(request(), 429, unreadableBody(1)).newBuilder()
                .header("Retry-After", "3600").build();
        ProfileLocationResult result = ProfileLocationTransport.readResponse(response, 42, now);
        assertEquals(ProfileLocationResult.Kind.RATE_LIMIT, result.kind);
        assertEquals(now + 3_600_000, result.retryAt);
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(now + 7_200_000).atZone(ZoneOffset.UTC));
        assertEquals(now + 7_200_000, ProfileLocationTransport.retryAt(date, now));
        for (String header : new String[]{null, "0", "60", "-1", "invalid", "9".repeat(129),
                "Tue, 14 Nov 2000 22:13:20 GMT"}) {
            assertEquals(now + AuthorLocationCache.RATE_LIMIT_MILLIS,
                    ProfileLocationTransport.retryAt(header, now));
        }
        assertEquals(Long.MAX_VALUE, ProfileLocationTransport.retryAt(Long.toString(Long.MAX_VALUE), now));
    }

    @Test
    public void declaredAndStreamingOversizedBodiesCannotReachTheParser() throws IOException {
        assertEquals(ProfileLocationResult.Kind.FAILURE,
                ProfileLocationTransport.readResponse(response(request(), 200,
                        unreadableBody(ProfileLocationTransport.MAX_RESPONSE_BYTES + 1)), 42, now).kind);
        Buffer bytes = new Buffer();
        bytes.write(new byte[ProfileLocationTransport.MAX_RESPONSE_BYTES + 100]);
        AtomicInteger closed = new AtomicInteger();
        ResponseBody streaming = new ResponseBody() {
            @Override public MediaType contentType() { return MediaType.parse("application/json"); }
            @Override public long contentLength() { return -1; }
            @Override public BufferedSource source() { return bytes; }
            @Override public void close() { closed.incrementAndGet(); super.close(); }
        };
        try (Response response = response(request(), 200, streaming)) {
            assertEquals(ProfileLocationResult.Kind.FAILURE,
                    ProfileLocationTransport.readResponse(response, 42, now).kind);
            assertTrue(bytes.size() >= 99);
        }
        assertEquals(1, closed.get());
    }

    @Test
    public void malformedGbkAndHtmlChallengesAreNotEmptyLocationSuccess() throws IOException {
        ResponseBody malformed = ResponseBody.create(MediaType.parse("text/plain"), new byte[]{(byte) 0x81});
        assertEquals(ProfileLocationResult.Kind.FAILURE,
                ProfileLocationTransport.readResponse(response(request(), 200, malformed), 42, now).kind);
        ResponseBody html = ResponseBody.create(MediaType.parse("text/html"),
                "<html><title>验证</title></html>".getBytes(Charset.forName("GBK")));
        assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                ProfileLocationTransport.readResponse(response(request(), 200, html), 42, now).kind);
        ResponseBody challenge503 = ResponseBody.create(MediaType.parse("text/html"),
                "<html><title>验证</title></html>".getBytes(Charset.forName("GBK")));
        assertEquals(ProfileLocationResult.Kind.SESSION_REJECTED,
                ProfileLocationTransport.readResponse(response(request(), 503, challenge503), 42, now).kind);
    }

    private static Request request() {
        return new Request.Builder().url("https://bbs.nga.cn/nuke.php").build();
    }

    private static Response response(Request request, int code, ResponseBody body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Offline fixture").body(body).build();
    }

    private static ResponseBody fixtureBody() {
        return ResponseBody.create(MediaType.parse("text/javascript; charset=GBK"),
                "{\"data\":{\"0\":{\"uid\":42,\"ipLoc\":\"广东\"}}}".getBytes(Charset.forName("GBK")));
    }

    private static ResponseBody unreadableBody(long size) {
        return new ResponseBody() {
            @Override public MediaType contentType() { return null; }
            @Override public long contentLength() { return size; }
            @Override public BufferedSource source() { throw new AssertionError("Body must not be read"); }
        };
    }
}
