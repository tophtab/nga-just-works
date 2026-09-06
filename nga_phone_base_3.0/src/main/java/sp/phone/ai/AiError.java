package sp.phone.ai;

/** Only fixed, public messages cross the transport/UI boundary. */
public enum AiError {
    AUTHENTICATION("认证失败，请检查 API Key"),
    ADDRESS("API 服务地址不可用，请检查地址"),
    RATE_LIMIT("请求过于频繁或额度不足，请稍后重试"),
    SERVER("AI 服务暂时不可用，请稍后重试"),
    NETWORK("网络连接失败，请稍后重试"),
    TIMEOUT("请求超时，请稍后重试"),
    INVALID_REQUEST("请求未被接受，请检查模型和配置"),
    INVALID_RESPONSE("AI 服务返回了无法识别的结果"),
    RESPONSE_TOO_LARGE("AI 服务返回的内容过长，请重试"),
    CANCELLED("请求已取消");

    private final String message;

    AiError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    static AiError forHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return AUTHENTICATION;
        }
        if (status == 404 || (status >= 300 && status < 400)) {
            return ADDRESS;
        }
        if (status == 429) {
            return RATE_LIMIT;
        }
        if (status >= 500) {
            return SERVER;
        }
        if (status >= 400) {
            return INVALID_REQUEST;
        }
        return INVALID_RESPONSE;
    }
}
