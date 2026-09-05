package gov.anzong.androidnga.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;

/**
 * NGA 图床地址的唯一权威来源。
 *
 * <p>历史上图床迁过两次（{@code img*.ngacn.cc} → {@code img*.nga.178.com} → {@code img*.nga.cn}），
 * 每次都因为域名散落在各解码器里而全线崩掉。所有附件地址一律从这里取，不要再各自持有域名字符串。
 *
 * <p><b>图床按路径族分主机</b>——这是本类两条归一化规则的由来：
 * <ul>
 *   <li>{@code /attachments/}（附件、正文图）只有 {@code img.nga.cn} 与 {@code img9.nga.cn} 提供；</li>
 *   <li>{@code /ngabbs/post/smile/}（表情）只有 {@code img4.nga.cn} 提供，{@code img.nga.cn} 对该路径 404。</li>
 * </ul>
 * 所以不能把旧域名一刀切重写成附件主机，否则表情路径会从「域名已死」变成「404」。
 *
 * <p><b>协议与主机绑定</b>：{@code img9.nga.cn} 的 https 稳定返回 NGA 自己的 404 页，只有 http 可用。
 * 不要为了整齐把 {@link #IMG9_BASE_URL} 的协议改成 https。
 */
public final class NgaImageHost {

    public static final int MODE_AUTO = 0;

    public static final int MODE_DEFAULT = 1;

    public static final int MODE_IMG9 = 2;

    public static final int MODE_CUSTOM = 3;

    /** 附件默认地址；实测唯一 http + https 双通的附件源。 */
    public static final String DEFAULT_BASE_URL = "https://img.nga.cn";

    /** img9 的附件地址只支持明文 HTTP，勿改为 HTTPS。 */
    public static final String IMG9_BASE_URL = "http://img9.nga.cn";

    public static final String DEFAULT_ATTACHMENTS_PREFIX = DEFAULT_BASE_URL + "/attachments";

    private static final String ATTACHMENTS_PATH = "/attachments";

    /** 遗留图床的域名后缀，附带 {@code img} 前缀锚定，避免误伤主站 nga.178.com / bbs.ngacn.cc。 */
    private static final String LEGACY_HOST = "img(\\d*)\\.(?:nga\\.178\\.com|ngacn\\.cc)";

    /** 已知退役图床作为页面级附件主机时，必须回退到当前附件主机。 */
    private static final Pattern RETIRED_ATTACHMENT_PREFIX = Pattern.compile(
            "https?://" + LEGACY_HOST + "(?::\\d+)?/attachments$",
            Pattern.CASE_INSENSITIVE);

    /** 规则 1：附件族。消费完整附件前缀，方便直接替换为页面级前缀。 */
    private static final Pattern LEGACY_ATTACHMENT = Pattern.compile(
            "https?://" + LEGACY_HOST + "/attachments(?=/|[?#]|$)",
            Pattern.CASE_INSENSITIVE);

    /** 规则 2：其余族（表情等）。保留原编号，只换域名后缀。 */
    private static final Pattern LEGACY_OTHER = Pattern.compile(
            "https?://" + LEGACY_HOST + "(?=[/:?#\\s\"'<>]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VALID_HOST = Pattern.compile("[A-Za-z0-9.\\-]+(:\\d+)?");

    private static volatile PreferenceSelection sCachedPreferenceSelection;

    private NgaImageHost() {
    }

    /**
     * 生效的附件 base url，如 {@code https://img.nga.cn}（不带尾斜杠）。
     *
     * <p>任何异常都回退到 {@link #DEFAULT_BASE_URL}：本方法会被 {@code lib_core} 的解码器调用，
     * 而那些解码器在纯 JVM 单测里跑得到，彼时没有 Android 环境。
     */
    public static String attachmentBaseUrl() {
        String prefix = attachmentsPrefix();
        return prefix.substring(0, prefix.length() - ATTACHMENTS_PATH.length());
    }

    /** 附件目录前缀，如 {@code https://img.nga.cn/attachments}（不带尾斜杠）。 */
    public static String attachmentsPrefix() {
        return attachmentsPrefix(null);
    }

    /**
     * 解析当前页面的附件目录前缀。
     *
     * <p>只有自动模式会读取 {@code serverAttachmentBaseView}；手动模式始终以用户选择为准。
     * 服务端值只参与本次调用，不会写入缓存或偏好设置。
     */
    public static String attachmentsPrefix(String serverAttachmentBaseView) {
        PreferenceSelection selection = getPreferenceSelection();
        return resolveAttachmentsPrefix(
                selection.mode, selection.customBaseUrl, serverAttachmentBaseView);
    }

    /** 「图片域名」相关设置变更后调用，让下次取值重新解析。 */
    public static void invalidate() {
        sCachedPreferenceSelection = null;
    }

    /** 不依赖 Android 的模式解析入口，供契约测试锁定完整决策矩阵。 */
    static String resolveAttachmentsPrefix(
            int mode, String customBaseUrl, String serverAttachmentBaseView) {
        switch (normalizeMode(mode)) {
            case MODE_DEFAULT:
                return DEFAULT_ATTACHMENTS_PREFIX;
            case MODE_IMG9:
                return IMG9_BASE_URL + ATTACHMENTS_PATH;
            case MODE_CUSTOM:
                String custom = sanitizeBaseUrlInput(customBaseUrl);
                return custom == null
                        ? DEFAULT_ATTACHMENTS_PREFIX
                        : custom + ATTACHMENTS_PATH;
            case MODE_AUTO:
            default:
                String serverPrefix = sanitizeServerAttachmentBaseView(serverAttachmentBaseView);
                return serverPrefix == null || isRetiredImageHost(serverPrefix)
                        ? DEFAULT_ATTACHMENTS_PREFIX
                        : serverPrefix;
        }
    }

    /**
     * 页面字段是附件路径族的基址；历史退役图床即使语法合法，也不能继续向下游传播。
     * 只检查 sanitize 后的完整前缀，避免把用户手动选择或正文里的其他路径误判为页面值。
     */
    private static boolean isRetiredImageHost(String attachmentsPrefix) {
        return RETIRED_ATTACHMENT_PREFIX.matcher(attachmentsPrefix).matches();
    }

    private static int normalizeMode(int mode) {
        return mode >= MODE_AUTO && mode <= MODE_CUSTOM ? mode : MODE_AUTO;
    }

    private static PreferenceSelection getPreferenceSelection() {
        PreferenceSelection cached = sCachedPreferenceSelection;
        if (cached != null) {
            return cached;
        }

        int mode = MODE_AUTO;
        String customBaseUrl = "";
        try {
            mode = normalizeMode(Integer.parseInt(
                    PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0")));
            if (mode == MODE_CUSTOM) {
                customBaseUrl = PreferenceUtils.getData(
                        PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, "");
            }
        } catch (Throwable ignored) {
            // 无 Android context（JVM 单测里 PreferenceUtils 的静态初始化会抛 Error）、
            // 偏好读取失败或值损坏时按自动模式处理；有页面值就使用，没有则固定安全兜底。
            mode = MODE_AUTO;
            customBaseUrl = "";
        }

        PreferenceSelection resolved = new PreferenceSelection(mode, customBaseUrl);
        sCachedPreferenceSelection = resolved;
        return resolved;
    }

    /**
     * 把用户填写的自定义域名归一成完整 base url。
     *
     * <p>接受 {@code img.nga.cn}、{@code https://img.nga.cn}、{@code http://img9.nga.cn/}、
     * {@code  img.nga.cn/attachments } 等写法；未写协议时默认 https，写了则尊重用户选择。
     *
     * @return 形如 {@code https://img.nga.cn} 的 base url；输入空白或非法时返回 {@code null}
     */
    public static String sanitizeBaseUrlInput(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        String scheme = "https";
        int schemeEnd = value.indexOf("://");
        if (schemeEnd != -1) {
            String declared = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
            if (!"http".equals(declared) && !"https".equals(declared)) {
                return null;
            }
            scheme = declared;
            value = value.substring(schemeEnd + "://".length());
        }

        int cut = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        String host = value.substring(0, cut).trim();
        if (host.isEmpty()
                || isPlaceholderHost(host)
                || !VALID_HOST.matcher(host).matches()) {
            return null;
        }
        return scheme + "://" + host;
    }

    /**
     * 把服务端 {@code _ATTACH_BASE_VIEW} 规范成完整附件前缀。
     *
     * <p>与自定义输入不同，服务端字段只接受空路径、根路径或 {@code /attachments}，并拒绝
     * query/fragment，避免把其他资源基址误解释成附件主机。
     */
    static String sanitizeServerAttachmentBaseView(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            return null;
        }

        String scheme = "https";
        if (value.startsWith("//")) {
            value = value.substring(2);
        } else {
            int schemeEnd = value.indexOf("://");
            if (schemeEnd != -1) {
                String declared = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
                if (!"http".equals(declared) && !"https".equals(declared)) {
                    return null;
                }
                scheme = declared;
                value = value.substring(schemeEnd + "://".length());
            }
        }

        int pathStart = value.indexOf('/');
        String host = pathStart == -1 ? value : value.substring(0, pathStart);
        String path = pathStart == -1 ? "" : value.substring(pathStart);
        if (host.isEmpty()
                || isPlaceholderHost(host)
                || !VALID_HOST.matcher(host).matches()) {
            return null;
        }
        if (!path.isEmpty()
                && !"/".equals(path)
                && !ATTACHMENTS_PATH.equals(path)
                && !(ATTACHMENTS_PATH + "/").equals(path)) {
            return null;
        }
        return scheme + "://" + host + ATTACHMENTS_PATH;
    }

    /**
     * 上游在字段缺失时曾把 JavaScript 的占位值直接拼进 URL，形成
     * {@code http://null/...} 或 {@code http://undefined/...}。这些不是可用主机，
     * 即使语法上符合主机字符约束也必须走固定安全兜底。
     */
    private static boolean isPlaceholderHost(String host) {
        int portSeparator = host.indexOf(':');
        String hostName = portSeparator < 0 ? host : host.substring(0, portSeparator);
        return "null".equalsIgnoreCase(hostName) || "undefined".equalsIgnoreCase(hostName);
    }

    /**
     * 把内容里遗留的图床主机重写为当前可用地址，让写死了旧域名的历史帖子恢复可见。
     *
     * <p>只改协议与主机名，路径、查询串、{@code .thumb.jpg} 等画质后缀原样保留。
     * 附件路径走当前所选图片域名，其余路径保留原编号只换域名后缀（见类注释）。
     */
    public static String normalizeLegacyHosts(String content) {
        return normalizeLegacyHosts(content, attachmentsPrefix());
    }

    /**
     * 使用调用方提供的页面级附件前缀归一化历史图床地址。
     * 非附件路径仍保留原 {@code img} 编号，只迁移到 {@code .nga.cn}。
     */
    public static String normalizeLegacyHosts(String content, String attachmentsPrefix) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String safePrefix = sanitizeServerAttachmentBaseView(attachmentsPrefix);
        if (safePrefix == null) {
            safePrefix = DEFAULT_ATTACHMENTS_PREFIX;
        }
        String result = LEGACY_ATTACHMENT.matcher(content)
                .replaceAll(Matcher.quoteReplacement(safePrefix));
        return LEGACY_OTHER.matcher(result).replaceAll("https://img$1.nga.cn");
    }

    private static final class PreferenceSelection {

        private final int mode;

        private final String customBaseUrl;

        private PreferenceSelection(int mode, String customBaseUrl) {
            this.mode = mode;
            this.customBaseUrl = customBaseUrl;
        }
    }
}
