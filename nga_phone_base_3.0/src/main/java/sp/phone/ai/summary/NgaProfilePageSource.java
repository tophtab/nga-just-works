package sp.phone.ai.summary;

import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import sp.phone.ai.SafeJsonParser;

/**
 * Narrow TOPIC.LIST adapter. Wire fields come from pinned TopicListModel and TopicListBean.__P.
 * The legacy shared converter/parser logs bodies, so this path decodes its own bounded response.
 * Cookie and UA are captured once per summary and never exposed to the model input.
 */
public final class NgaProfilePageSource implements ProfileSummaryLoader.PageSource {

    static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final Set<String> NGA_HOSTS = new HashSet<>(Arrays.asList(
            "bbs.nga.cn", "bbs.ngacn.cc", "nga.178.com", "nga.donews.com", "ngabbs.com"));
    private static final String PREFIX = "window.script_muti_get_var_store=";
    private static final String RESPONSE_ERROR = "NGA 内容格式异常，请稍后重试";
    private static final String ACCESS_ERROR = "NGA 暂时无法提供公开内容，请检查登录状态或访问限制";

    private final Call.Factory client;
    private final String domain;
    private final String cookie;
    private final String userAgent;

    public NgaProfilePageSource(String domain, String cookie, String userAgent) {
        this(domain, cookie, userAgent, new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .cache(null)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build());
    }

    NgaProfilePageSource(String domain, String cookie, String userAgent, Call.Factory client) {
        this.domain = domain;
        this.cookie = cookie == null ? "" : cookie;
        this.userAgent = userAgent == null ? "" : userAgent;
        this.client = client;
    }

    @Override
    public SummaryController.Cancelable loadFirstPage(String uid, ProfileSummaryLoader.Kind kind,
                                                      ProfileSummaryLoader.PageCallback callback) {
        final Request request;
        try {
            request = buildRequest(domain, cookie, userAgent, uid, kind);
        } catch (IllegalArgumentException ignored) {
            callback.onError("论坛地址或用户资料无效，请检查后重试");
            return SummaryController.Cancelable.NONE;
        }
        Call call = client.newCall(request);
        AtomicBoolean userCanceled = new AtomicBoolean();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                if (!userCanceled.get()) {
                    callback.onError(networkError(exception));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closedResponse = response) {
                    if (userCanceled.get()) {
                        return;
                    }
                    if (call.isCanceled()) {
                        callback.onError("读取 NGA 内容超时，请稍后重试");
                        return;
                    }
                    String error = statusError(closedResponse.code());
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    ProfileSummaryLoader.Page page = parsePage(readBody(closedResponse.body(),
                            closedResponse.header("Content-Type")), uid, kind);
                    if (!userCanceled.get()) {
                        if (call.isCanceled()) {
                            callback.onError("读取 NGA 内容超时，请稍后重试");
                        } else {
                            callback.onSuccess(page);
                        }
                    }
                } catch (PageException exception) {
                    if (!userCanceled.get()) {
                        callback.onError(exception.getMessage());
                    }
                } catch (IOException exception) {
                    if (!userCanceled.get()) {
                        callback.onError(networkError(exception));
                    }
                } catch (RuntimeException ignored) {
                    if (!userCanceled.get()) {
                        callback.onError(RESPONSE_ERROR);
                    }
                }
            }
        });
        return () -> {
            userCanceled.set(true);
            call.cancel();
        };
    }

    private static String networkError(IOException error) {
        return error instanceof InterruptedIOException
                ? "读取 NGA 内容超时，请稍后重试" : "读取 NGA 内容失败，请检查网络后重试";
    }

    static Request buildRequest(String domain, String cookie, String userAgent, String uid,
                                ProfileSummaryLoader.Kind kind) {
        HttpUrl base = HttpUrl.parse(domain);
        if (base == null || !"https".equals(base.scheme()) || base.port() != 443
                || !NGA_HOSTS.contains(base.host()) || !base.username().isEmpty()
                || !base.password().isEmpty() || !"/".equals(base.encodedPath())
                || base.query() != null || base.fragment() != null
                || uid == null || !uid.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("论坛地址或用户资料无效");
        }
        HttpUrl.Builder url = base.newBuilder().addPathSegment("thread.php")
                .addQueryParameter("authorid", uid);
        if (kind == ProfileSummaryLoader.Kind.REPLIES) {
            url.addQueryParameter("searchpost", "1");
        }
        url.addQueryParameter("page", "1").addQueryParameter("lite", "js")
                .addQueryParameter("noprefix", null);
        Request.Builder request = new Request.Builder().url(url.build())
                .header("X-User-Agent", "Nga_Official");
        if (cookie != null && !cookie.isEmpty()) {
            request.header("Cookie", cookie);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            request.header("User-Agent", userAgent);
        }
        return request.get().build();
    }

    private static String statusError(int status) {
        if (status >= 200 && status < 300) {
            return null;
        }
        if (status == 401) {
            return "NGA 登录状态已失效，请重新登录后重试";
        }
        if (status == 403) {
            return "NGA 拒绝访问，请在论坛中确认访问权限";
        }
        if (status == 429) {
            return "NGA 请求过于频繁，请稍后手动重试";
        }
        if (status >= 300 && status < 400) {
            return "NGA 返回了重定向，请检查论坛域名后重试";
        }
        if (status >= 500) {
            return "NGA 服务暂时不可用，请稍后重试";
        }
        return ACCESS_ERROR;
    }

    private static String readBody(ResponseBody body, String contentTypeHeader) throws IOException, PageException {
        if (body == null || body.contentLength() > MAX_RESPONSE_BYTES) {
            throw new PageException(RESPONSE_ERROR);
        }
        BufferedSource source = body.source();
        source.request(MAX_RESPONSE_BYTES + 1L);
        if (source.buffer().size() > MAX_RESPONSE_BYTES) {
            throw new PageException("NGA 内容过长，暂时无法总结");
        }
        MediaType type = body.contentType();
        // OkHttp returns null for both absent and malformed media types; only absence permits
        // the legacy GBK fallback. A declared but invalid type/charset is a protocol failure.
        if (contentTypeHeader != null && type == null) {
            throw new PageException(RESPONSE_ERROR);
        }
        Charset charset = type == null ? null : type.charset();
        if (charset == null && type != null && type.toString().toLowerCase(java.util.Locale.ROOT)
                .contains("charset=")) {
            throw new PageException(RESPONSE_ERROR);
        }
        // TOPIC.LIST's pinned lite=js representation defaults to GBK; explicit charset wins.
        if (charset == null) {
            charset = Charset.forName("GBK");
        }
        byte[] bytes = source.readByteArray();
        try {
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {
            throw new PageException(RESPONSE_ERROR);
        }
    }

    static ProfileSummaryLoader.Page parsePage(String raw, String uid, ProfileSummaryLoader.Kind kind)
            throws PageException {
        if (raw == null || raw.length() > MAX_RESPONSE_BYTES) {
            throw new PageException(RESPONSE_ERROR);
        }
        String text = raw.trim();
        if (text.startsWith(PREFIX)) {
            text = text.substring(PREFIX.length()).trim();
        }
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.startsWith("<")) {
            throw new PageException(ACCESS_ERROR);
        }
        try {
            JSONObject root = SafeJsonParser.parseObject(text);
            if (root == null) {
                throw new PageException(RESPONSE_ERROR);
            }
            if (root.containsKey("error")) {
                throw new PageException(ACCESS_ERROR);
            }
            JSONObject data = object(root.get("data"));
            if (data == null || data.containsKey("__MESSAGE")) {
                throw new PageException(ACCESS_ERROR);
            }
            JSONObject rows = object(data.get("__T"));
            Object rowCount = data.get("__T__ROWS");
            if (rows == null || !(rowCount instanceof Number) || ((Number) rowCount).intValue() < 0) {
                throw new PageException(RESPONSE_ERROR);
            }
            List<Integer> keys = new ArrayList<>();
            for (String key : rows.keySet()) {
                if (key.matches("[0-9]{1,6}")) {
                    keys.add(Integer.parseInt(key));
                }
            }
            Collections.sort(keys);
            if (keys.isEmpty() && ((Number) rowCount).intValue() != 0) {
                throw new PageException(RESPONSE_ERROR);
            }
            List<ProfileSummaryInput.Entry> entries = new ArrayList<>();
            for (Integer index : keys) {
                JSONObject row = object(rows.get(String.valueOf(index)));
                if (row == null) {
                    throw new PageException(RESPONSE_ERROR);
                }
                JSONObject authored = kind == ProfileSummaryLoader.Kind.REPLIES
                        ? object(row.get("__P")) : row;
                if (authored == null || !uid.equals(scalar(authored.get("authorid")))) {
                    throw new PageException("NGA 返回的内容与当前用户不符，请重新发起总结");
                }
                String reply = "";
                if (kind == ProfileSummaryLoader.Kind.REPLIES) {
                    if (!(authored.get("content") instanceof String)) {
                        throw new PageException("NGA 未提供回复正文，暂时无法总结");
                    }
                    reply = (String) authored.get("content");
                }
                String title = scalar(row.get("subject"));
                if (title.isEmpty()) {
                    throw new PageException(RESPONSE_ERROR);
                }
                entries.add(new ProfileSummaryInput.Entry(title, board(row),
                        date(authored.get("postdate"), ZoneId.systemDefault()), reply));
                if (entries.size() == ProfileSummaryInput.MAX_ITEMS_PER_PAGE) {
                    break;
                }
            }
            return new ProfileSummaryLoader.Page(uid, kind, entries);
        } catch (PageException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            // Do not retain the parser exception: its text may contain the original response.
            throw new PageException(RESPONSE_ERROR);
        }
    }

    private static JSONObject object(Object value) {
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    private static String scalar(Object value) {
        return value instanceof String || value instanceof Number ? value.toString() : "";
    }

    private static String board(JSONObject row) {
        JSONObject parent = object(row.get("parent"));
        if (parent == null && row.get("parent") instanceof String) {
            String parentText = (String) row.get("parent");
            if (parentText.length() <= 4096) {
                parent = SafeJsonParser.parseObject(parentText);
            }
        }
        String name = parent == null ? "" : scalar(parent.get("2"));
        return name.isEmpty() ? "版面 " + scalar(row.get("fid")) : name;
    }

    static String date(Object value, ZoneId zone) {
        String number = scalar(value);
        if (!number.matches("[0-9]{1,11}")) {
            return "日期未提供";
        }
        // Match the legacy app's SimpleDateFormat/Calendar use of the device's display zone.
        return Instant.ofEpochSecond(Long.parseLong(number)).atZone(zone)
                .toLocalDate().toString();
    }

    static final class PageException extends Exception {
        PageException(String safeMessage) {
            super(safeMessage);
        }
    }

}
