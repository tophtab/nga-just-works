package sp.phone.ai;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final long CALL_TIMEOUT_MILLIS = 60_000;
    private final OkHttpClient http;
    private final HttpUrl testEndpoint;

    public interface Callback {
        /** Runs on the transport thread; UI callers must dispatch to the main thread. */
        void onSuccess(String text);
        void onError(AiError error);
    }

    public AiSummaryClient() {
        testEndpoint = null;
        http = createTransport(CALL_TIMEOUT_MILLIS, false);
    }

    /** Local fake-server seam only. Production configuration always remains HTTPS-only. */
    AiSummaryClient(HttpUrl loopbackEndpoint, long timeoutMillis) {
        if (loopbackEndpoint == null || timeoutMillis <= 0 || timeoutMillis > CALL_TIMEOUT_MILLIS
                || !("localhost".equals(loopbackEndpoint.host())
                || "127.0.0.1".equals(loopbackEndpoint.host())
                || "::1".equals(loopbackEndpoint.host()))) {
            throw new IllegalArgumentException("Only a local test endpoint is allowed");
        }
        testEndpoint = loopbackEndpoint;
        http = createTransport(timeoutMillis, true);
    }

    public Call summarize(AiConfig config, String prompt, Callback callback) {
        return send(config, prompt, 1024, callback);
    }

    public Call testConnection(AiConfig config, Callback callback) {
        return send(config, "请只回复：连接成功", 8, callback);
    }

    private Call send(AiConfig config, String prompt, int maxTokens, Callback callback) {
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
        payload.put("stream", false);
        payload.put("max_tokens", maxTokens);

        Request request = new Request.Builder()
                .url(testEndpoint == null ? HttpUrl.get(config.getEndpoint()) : testEndpoint)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Accept", "application/json")
                .post(singleUseBody(JSON.toJSONString(payload)))
                .build();
        Call call = http.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call failedCall, IOException failure) {
                callback.onError(classifyFailure(failedCall, failure));
            }

            @Override
            public void onResponse(Call responseCall, Response response) {
                String text = null;
                AiError error = null;
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        // Error bodies are deliberately neither parsed nor exposed.
                        error = AiError.forHttpStatus(response.code());
                    } else {
                        text = AiResponseParser.firstText(readBoundedUtf8(response.body()));
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
                    callback.onSuccess(text);
                } else {
                    callback.onError(error);
                }
            }
        });
        return call;
    }

    private static String readBoundedUtf8(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        if (body.contentLength() > MAX_RESPONSE_BYTES
                || body.source().request(MAX_RESPONSE_BYTES + 1L)) {
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

    private static OkHttpClient createTransport(long timeoutMillis, boolean allowLoopbackHttp) {
        return new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .eventListener(EventListener.NONE)
                .cache(null)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectionSpecs(allowLoopbackHttp
                        ? Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
                        : Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .connectTimeout(Math.min(timeoutMillis, 15_000), TimeUnit.MILLISECONDS)
                .readTimeout(Math.min(timeoutMillis, 45_000), TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(timeoutMillis, 15_000), TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    OkHttpClient transportForTest() {
        return http;
    }

    private static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException() {
            super("AI response exceeds limit");
        }
    }
}
