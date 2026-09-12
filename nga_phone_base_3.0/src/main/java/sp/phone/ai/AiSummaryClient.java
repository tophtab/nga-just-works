package sp.phone.ai;

import com.alibaba.fastjson.JSON;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.CookieJar;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/** A standalone, cancellable, UTF-8 client with no NGA or system credential transport. */
public final class AiSummaryClient {
    static final int MAX_PROMPT_CHARS = 64 * 1024;
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final int MAX_SUMMARY_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int COMPLETION_TOKEN_LIMIT = 10_000;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final long CALL_TIMEOUT_MILLIS = 60_000;
    private static final long SUMMARY_TIMEOUT_MILLIS = 180_000;
    private final OkHttpClient http;
    private final OkHttpClient summaryHttp;
    private final HttpUrl testEndpoint;

    public interface Callback {
        /** Cumulative snapshots on the transport thread; UI callers dispatch to the main thread. */
        default void onProgress(String answer, String reasoning) { }
        void onSuccess(String text);
        void onError(AiError error);
    }

    public interface ModelsCallback {
        /** Runs on the transport thread; UI callers must dispatch to the main thread. */
        void onSuccess(List<String> models);
        void onError(AiError error);
    }

    public AiSummaryClient() {
        testEndpoint = null;
        http = createTransport(CALL_TIMEOUT_MILLIS);
        summaryHttp = createSummaryTransport(http, SUMMARY_TIMEOUT_MILLIS);
    }

    /** Local fake-server endpoint and deadline seam only. */
    AiSummaryClient(HttpUrl loopbackEndpoint, long timeoutMillis) {
        if (loopbackEndpoint == null || timeoutMillis <= 0 || timeoutMillis > CALL_TIMEOUT_MILLIS
                || !("localhost".equals(loopbackEndpoint.host())
                || "127.0.0.1".equals(loopbackEndpoint.host())
                || "::1".equals(loopbackEndpoint.host()))) {
            throw new IllegalArgumentException("Only a local test endpoint is allowed");
        }
        testEndpoint = loopbackEndpoint;
        http = createTransport(timeoutMillis);
        summaryHttp = createSummaryTransport(http, timeoutMillis);
    }

    public Call summarize(AiConfig config, String prompt, Callback callback) {
        return send(config, prompt, true, callback);
    }

    public Call testConnection(AiConfig config, Callback callback) {
        return send(config, "请只回复：连接成功", false, callback);
    }

    /** Fetches service metadata using the current draft, before a model has been chosen. */
    public Call listModels(String endpoint, String apiKey, ModelsCallback callback) {
        HttpUrl completionUrl = HttpUrl.get(AiConfig.normalizeEndpoint(endpoint));
        String key = AiConfig.normalizeApiKey(apiKey);
        if (callback == null) {
            throw new IllegalArgumentException("缺少 AI 请求回调");
        }
        if (testEndpoint != null) {
            completionUrl = HttpUrl.get(AiConfig.normalizeEndpoint(testEndpoint.toString()));
        }
        // The normalized URL ends in /chat/completions. Keep the prefix, encoding, and port.
        int lastSegment = completionUrl.pathSize() - 1;
        HttpUrl modelsUrl = completionUrl.newBuilder()
                .removePathSegment(lastSegment)
                .setPathSegment(lastSegment - 1, "models")
                .build();
        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + key)
                .header("Accept", "application/json")
                .get()
                .build();
        return enqueue(http, request,
                (response, call) -> AiResponseParser.modelIds(readBoundedUtf8(response.body(), MAX_RESPONSE_BYTES)),
                callback::onSuccess, callback::onError);
    }

    private Call send(AiConfig config, String prompt, boolean streaming, Callback callback) {
        if (config == null) {
            throw new IllegalArgumentException("请先配置 AI");
        }
        if (callback == null) {
            throw new IllegalArgumentException("缺少 AI 请求回调");
        }
        if (prompt == null || prompt.trim().isEmpty() || prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException("待总结内容为空或过长");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModel());
        payload.put("messages", Collections.singletonList(message));
        payload.put("stream", streaming);
        payload.put("max_tokens", COMPLETION_TOKEN_LIMIT);

        Request request = new Request.Builder()
                .url(testEndpoint == null ? HttpUrl.get(config.getEndpoint()) : testEndpoint)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Accept", streaming ? "text/event-stream, application/json" : "application/json")
                .post(singleUseBody(JSON.toJSONString(payload)))
                .build();
        return enqueue(streaming ? summaryHttp : http, request,
                (response, call) -> readCompletion(response, call, callback, streaming),
                callback::onSuccess, callback::onError);
    }

    private <T> Call enqueue(OkHttpClient transport, Request request, ResponseParser<T> parser,
                             Consumer<T> onSuccess, Consumer<AiError> onError) {
        Call call = transport.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call failedCall, IOException failure) {
                onError.accept(classifyFailure(failedCall, failure));
            }

            @Override
            public void onResponse(Call responseCall, Response response) {
                T result = null;
                AiError error = null;
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        // Error bodies are deliberately neither parsed nor exposed.
                        error = AiError.forHttpStatus(response.code());
                    } else {
                        result = parser.parse(response, responseCall);
                    }
                } catch (AiResponseParser.InvalidResponseException invalid) {
                    error = invalid.error;
                } catch (CharacterCodingException invalidUtf8) {
                    error = AiError.INVALID_RESPONSE;
                } catch (ResponseTooLargeException tooLarge) {
                    error = AiError.RESPONSE_TOO_LARGE;
                } catch (IOException failure) {
                    error = classifyFailure(responseCall, failure);
                } catch (RuntimeException ignored) {
                    error = AiError.INVALID_RESPONSE;
                }
                // Cancellation can arrive while the response is being decoded.
                if (responseCall.isCanceled() && error != AiError.TIMEOUT) {
                    error = AiError.CANCELLED;
                }
                if (error == null) {
                    onSuccess.accept(result);
                } else {
                    onError.accept(error);
                }
            }
        });
        return call;
    }

    private static String readCompletion(Response response, Call call, Callback callback, boolean summary)
            throws IOException, AiResponseParser.InvalidResponseException {
        ResponseBody body = response.body();
        int limit = summary ? MAX_SUMMARY_RESPONSE_BYTES : MAX_RESPONSE_BYTES;
        // A call deadline also sets isCanceled(). Always flush received text; the controller
        // rejects obsolete generations after deliberate cancellation, retry, or target change.
        AiResponseParser.ProgressListener progress = callback::onProgress;
        MediaType type = body == null ? null : body.contentType();
        if (summary && type != null && "text".equalsIgnoreCase(type.type())
                && "event-stream".equalsIgnoreCase(type.subtype())) {
            if (body.contentLength() > limit) {
                throw new ResponseTooLargeException();
            }
            try {
                return AiStreamParser.read(new BoundedInputStream(body.byteStream(), limit), progress);
            } catch (CharacterCodingException | ResponseTooLargeException failure) {
                throw failure;
            } catch (IOException failure) {
                if (classifyFailure(call, failure) == AiError.NETWORK) {
                    throw new AiResponseParser.InvalidResponseException(AiError.INTERRUPTED_RESPONSE);
                }
                throw failure;
            }
        }
        return AiResponseParser.completionText(readBoundedUtf8(body, limit), progress);
    }

    private static String readBoundedUtf8(ResponseBody body, int limit) throws IOException {
        if (body == null) {
            return "";
        }
        if (body.contentLength() > limit
                || body.source().request(limit + 1L)) {
            throw new ResponseTooLargeException();
        }
        // request(limit + 1) returned false, so EOF was reached with at most limit bytes buffered.
        byte[] bytes = body.source().readByteArray();
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static AiError classifyFailure(Call call, IOException failure) {
        // OkHttp's total call deadline also cancels its internal Call. Preserve timeout meaning.
        if (failure instanceof InterruptedIOException) {
            return AiError.TIMEOUT;
        }
        if (call.isCanceled()) {
            return AiError.CANCELLED;
        }
        return AiError.NETWORK;
    }

    private static RequestBody singleUseBody(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON_TYPE;
            }

            @Override
            public long contentLength() {
                return bytes.length;
            }

            @Override
            public boolean isOneShot() {
                // retryOnConnectionFailure(false) alone does not cover every HTTP follow-up
                // (notably 503 + Retry-After: 0). OkHttp 4.12 retains that first response here.
                return true;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(bytes);
            }
        };
    }

    private static OkHttpClient createTransport(long timeoutMillis) {
        return new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .eventListener(EventListener.NONE)
                .cache(null)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .connectTimeout(Math.min(timeoutMillis, 15_000), TimeUnit.MILLISECONDS)
                .readTimeout(Math.min(timeoutMillis, 45_000), TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(timeoutMillis, 15_000), TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    private static OkHttpClient createSummaryTransport(OkHttpClient base, long timeoutMillis) {
        // newBuilder shares the isolated dispatcher/pool; discovery and connection-test
        // deadlines remain unchanged. Summaries allow more time for the model's default thinking.
        return base.newBuilder()
                .readTimeout(Math.min(timeoutMillis, 60_000), TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    OkHttpClient transportForTest() {
        return http;
    }

    OkHttpClient summaryTransportForTest() {
        return summaryHttp;
    }

    private interface ResponseParser<T> {
        T parse(Response response, Call call) throws IOException, AiResponseParser.InvalidResponseException;
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final int limit;
        private int count;

        BoundedInputStream(InputStream input, int limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1 && ++count > limit) {
                throw new ResponseTooLargeException();
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = in.read(bytes, offset, Math.min(length, limit - count + 1));
            if (read != -1) {
                count += read;
                if (count > limit) {
                    throw new ResponseTooLargeException();
                }
            }
            return read;
        }
    }

    private static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException() {
            super("AI response exceeds limit");
        }
    }
}
