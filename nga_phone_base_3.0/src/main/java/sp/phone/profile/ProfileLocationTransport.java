package sp.phone.profile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** A bounded USER.PROFILE read without the legacy task's UI, logger, or shared interceptors. */
final class ProfileLocationTransport implements AuthorLocationRepository.Transport {

    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Charset PROFILE_CHARSET = Charset.forName("GBK");
    private final Call.Factory calls;
    private final LongSupplier clock;

    ProfileLocationTransport(LongSupplier clock) {
        this(newClient(), clock);
    }

    ProfileLocationTransport(Call.Factory calls, LongSupplier clock) {
        this.calls = calls;
        this.clock = clock;
    }

    static OkHttpClient newClient() {
        return new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    // OkHttp 3.12 retries 503 + Retry-After: 0 even when connection retries
                    // are disabled. Remove only its 503 follow-up hint before that layer sees
                    // it; preserve status/body and all 429 retry metadata for our classifier.
                    return response.code() == 503
                            ? response.newBuilder().removeHeader("Retry-After").build() : response;
                })
                .build();
    }

    @Override
    public AuthorLocationRepository.Cancellation fetch(ProfileSession session, int author,
                                                        Consumer<ProfileLocationResult> callback) {
        if (session == null || author <= 0) {
            callback.accept(ProfileLocationResult.failure());
            return () -> { };
        }
        Request request = new Request.Builder()
                .url(session.origin + "/nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid=" + author)
                .header("Referer", session.origin + "/nuke.php?func=ucp&lite=jsx&uid=" + author)
                .header("Cookie", session.cookie)
                .header("User-Agent", session.userAgent)
                .header("X-User-Agent", "Nga_Official")
                .get()
                .build();
        Call call = calls.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                callback.accept(ProfileLocationResult.failure());
            }

            @Override
            public void onResponse(Call call, Response response) {
                ProfileLocationResult result;
                try (Response closeable = response) {
                    result = readResponse(closeable, author, clock.getAsLong());
                } catch (IOException | RuntimeException ignored) {
                    result = ProfileLocationResult.failure();
                }
                callback.accept(result);
            }
        });
        return call::cancel;
    }

    static ProfileLocationResult readResponse(Response response, int author, long now) throws IOException {
        int code = response.code();
        if (code == 429) {
            return ProfileLocationResult.rateLimit(retryAt(response.header("Retry-After"), now));
        }
        if (code == 503 || (code >= 300 && code < 500)) {
            // Includes redirects, authentication, challenge and unknown site rejection.
            // A 503 stops supplemental reads even without a usable error body. Otherwise
            // the queue would keep querying other authors through the same failing endpoint.
            return ProfileLocationResult.rejected();
        }
        ResponseBody body = response.body();
        if (body == null || body.contentLength() > MAX_RESPONSE_BYTES) {
            return ProfileLocationResult.failure();
        }
        byte[] bytes = readBounded(body.byteStream());
        if (bytes == null) {
            return ProfileLocationResult.failure();
        }
        try {
            String text = PROFILE_CHARSET.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            ProfileLocationResult parsed = ProfileLocationParser.parse(text, author);
            if (code != 200 && parsed.kind != ProfileLocationResult.Kind.SESSION_REJECTED) {
                return ProfileLocationResult.failure();
            }
            return parsed;
        } catch (CharacterCodingException ignored) {
            return ProfileLocationResult.failure();
        }
    }

    private static byte[] readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer, 0,
                Math.min(buffer.length, MAX_RESPONSE_BYTES + 1 - output.size()))) != -1) {
            output.write(buffer, 0, count);
            if (output.size() > MAX_RESPONSE_BYTES) {
                return null;
            }
        }
        return output.toByteArray();
    }

    static long retryAt(String value, long now) {
        long minimum = AuthorLocationCache.addTime(now, AuthorLocationCache.RATE_LIMIT_MILLIS);
        if (value == null || value.length() > 128) {
            return minimum;
        }
        value = value.trim();
        try {
            if (value.matches("[0-9]+")) {
                long seconds = Long.parseLong(value);
                long millis = seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000;
                return Math.max(minimum, AuthorLocationCache.addTime(now, millis));
            }
            long serverTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            return Math.max(minimum, serverTime);
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException ignored) {
            return minimum;
        }
    }
}
