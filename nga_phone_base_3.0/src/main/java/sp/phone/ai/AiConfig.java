package sp.phone.ai;

import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.HttpUrl;

/** One validated, in-memory configuration. Never serialize this object to logs or Bundles. */
public final class AiConfig {
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_KEY_LENGTH = 4096;
    private static final int MAX_MODEL_LENGTH = 256;
    private static final String COMPLETIONS_PATH = "/chat/completions";

    private final String endpoint;
    private final String apiKey;
    private final String model;

    public AiConfig(String endpoint, String apiKey, String model) {
        this.endpoint = normalizeEndpoint(endpoint);
        this.apiKey = requiredText(apiKey, MAX_KEY_LENGTH, "请输入有效的 API Key");
        for (int i = 0; i < this.apiKey.length(); i++) {
            char character = this.apiKey.charAt(i);
            if (character <= 0x20 || character >= 0x7f) {
                throw new IllegalArgumentException("API Key 不能包含空白或非 ASCII 字符");
            }
        }
        this.model = requiredText(model, MAX_MODEL_LENGTH, "请输入有效的模型名称");
    }

    /** The complete HTTPS Chat Completions URL, including any user-supplied version prefix. */
    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String toString() {
        return "AiConfig{configured}";
    }

    private static String normalizeEndpoint(String value) {
        String candidate = requiredText(value, MAX_ENDPOINT_LENGTH, "请输入有效的 HTTPS API 服务地址");
        final URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException ignored) {
            // URI exceptions include their input. Never retain one as a cause.
            throw new IllegalArgumentException("请输入有效的 HTTPS API 服务地址");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
                || uri.getRawAuthority() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("API 地址必须使用 HTTPS，且不能包含账号、查询参数或片段");
        }
        HttpUrl url = HttpUrl.parse(candidate);
        if (url == null || !url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty()) {
            throw new IllegalArgumentException("请输入有效的 HTTPS API 服务地址");
        }
        String path = url.encodedPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith(COMPLETIONS_PATH)) {
            path += COMPLETIONS_PATH;
        }
        String normalized = url.newBuilder().encodedPath(path).build().toString();
        if (normalized.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException("API 服务地址过长");
        }
        return normalized;
    }

    private static String requiredText(String value, int maxLength, String error) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalArgumentException(error);
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(error);
            }
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }
}
