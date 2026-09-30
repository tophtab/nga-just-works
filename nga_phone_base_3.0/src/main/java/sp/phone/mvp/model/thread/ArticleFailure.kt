package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject

/** These are local outcomes, not guessed meanings for App API numeric codes. */
enum class ArticleFailureKind { FORMAT, CONTENT, EMPTY, AUTH, ACCESS, RATE_LIMIT, BUSINESS, NETWORK, PROTOCOL, CANCELLED, STALE, CACHE }

/** Display evidence is deliberately separate from recovery policy; no raw site body is retained. */
enum class ArticleFailureReason { HTML, VALIDATION, DELETED, MISSING, NO_PERMISSION }

class ArticleFailure @JvmOverloads constructor(
    @JvmField val kind: ArticleFailureKind,
    @JvmField val httpStatus: Int? = null,
    @JvmField val reason: ArticleFailureReason? = null,
) : RuntimeException(displayMessage(kind, httpStatus, reason)) {
    fun allowsAppFallback() = kind == ArticleFailureKind.FORMAT
    fun allowsBrowser() = kind == ArticleFailureKind.FORMAT || kind == ArticleFailureKind.CONTENT || kind == ArticleFailureKind.PROTOCOL
}

private fun displayMessage(kind: ArticleFailureKind, status: Int?, reason: ArticleFailureReason?): String {
    if (status != null) return when (status) {
        403 -> "无法访问帖子（HTTP 403）"
        in 300..399 -> "帖子请求发生重定向（HTTP $status）"
        401 -> "登录状态已失效，请重新登录（HTTP 401）"
        429 -> "请求过于频繁，请稍后重试（HTTP 429）"
        else -> "帖子请求失败（HTTP $status）"
    }
    if (reason != null) return when (reason) {
        ArticleFailureReason.HTML -> "站点返回了网页内容，暂无法读取帖子"
        ArticleFailureReason.VALIDATION -> "站点要求访问验证，请稍后手动重试"
        ArticleFailureReason.DELETED -> "帖子已被删除"
        ArticleFailureReason.MISSING -> "请求的帖子或页面不存在"
        ArticleFailureReason.NO_PERMISSION -> "没有权限访问帖子"
    }
    return when (kind) {
        ArticleFailureKind.FORMAT -> "帖子数据格式暂不兼容"
        ArticleFailureKind.CONTENT -> "返回内容与当前查询不一致，暂无法显示"
        ArticleFailureKind.EMPTY -> "未返回帖子内容"
        ArticleFailureKind.AUTH -> "登录状态已失效，请重新登录"
        ArticleFailureKind.ACCESS -> "暂时无法访问帖子"
        ArticleFailureKind.RATE_LIMIT -> "请求过于频繁，请稍后重试"
        ArticleFailureKind.BUSINESS -> "站点未能提供所请求的帖子"
        ArticleFailureKind.NETWORK -> "网络连接失败，请重试"
        ArticleFailureKind.PROTOCOL -> "帖子响应无法读取"
        ArticleFailureKind.CANCELLED, ArticleFailureKind.STALE -> "读取已结束"
        ArticleFailureKind.CACHE -> "读取缓存失败！"
    }
}

object ArticleErrors {
    @JvmStatic fun classifyHttp(status: Int) {
        httpFailure(status)?.let { throw it }
    }

    private fun httpFailure(status: Int): ArticleFailure? {
        val kind = when (status) {
            401 -> ArticleFailureKind.AUTH
            403 -> ArticleFailureKind.ACCESS
            429 -> ArticleFailureKind.RATE_LIMIT
            in 300..399 -> ArticleFailureKind.ACCESS
            in 200..299 -> return null
            else -> ArticleFailureKind.PROTOCOL
        }
        return ArticleFailure(kind, httpStatus = status)
    }

    /** Keep the legacy throwable (and therefore its retry/browser decisions) intact. */
    @JvmStatic fun legacyMessage(error: Throwable, original: String?): String? {
        return if (error is retrofit2.HttpException) httpFailure(error.code())?.message ?: original
        else original
    }

    /** Strip only a leading Unicode BOM/outer whitespace; callers retain the original response. */
    @JvmStatic fun bodyText(raw: String): String {
        val text = raw.trim().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw ArticleFailure(ArticleFailureKind.EMPTY)
        // Unknown HTML is an access stop, never evidence for an alternate authenticated request.
        if (text.startsWith("<")) throw ArticleFailure(ArticleFailureKind.ACCESS, reason = ArticleFailureReason.HTML)
        return text
    }

    @JvmStatic fun json(raw: String): JSONObject {
        val text = bodyText(raw)
        val root = try { JSON.parse(text) as? JSONObject }
            catch (_: RuntimeException) { null } ?: throw ArticleFailure(ArticleFailureKind.FORMAT)
        inspect(root)
        return root
    }

    @JvmStatic fun inspect(root: JSONObject) {
        val message = root["msg"] as? String
        knownMessage(message)?.let { throw ArticleFailure(it, reason = knownReason(message)) }
        val data = root["data"] as? JSONObject
        val siteMessage = data?.get("__MESSAGE")
        if (siteMessage != null) {
            val text = (siteMessage as? JSONObject)?.get("1") as? String
            throw ArticleFailure(knownMessage(text) ?: ArticleFailureKind.BUSINESS, reason = knownReason(text))
        }
        val error = root["error"]
        if (error != null && error != false && error != "") {
            val text = (error as? JSONObject)?.get("0") as? String ?: error as? String
            throw ArticleFailure(knownMessage(text) ?: ArticleFailureKind.BUSINESS, reason = knownReason(text))
        }
    }

    private fun knownMessage(message: String?): ArticleFailureKind? {
        if (message == null) return null
        return when {
            listOf("未登录", "请先登录", "重新登录", "登录失效").any(message::contains) -> ArticleFailureKind.AUTH
            listOf("验证码", "访问验证", "访问受限", "访问限制", "验证访问").any(message::contains) -> ArticleFailureKind.ACCESS
            listOf("过于频繁", "频率限制", "请求频繁").any(message::contains) -> ArticleFailureKind.RATE_LIMIT
            listOf("无此页", "无此主题", "主题不存在", "帖子不存在", "没有权限", "无权限", "主题已删除", "主题已被删除", "帖子已删除", "帖子已被删除").any(message::contains) -> ArticleFailureKind.BUSINESS
            else -> null
        }
    }

    // Only called on the existing structured error/message fields, never on post content or HTML.
    private fun knownReason(message: String?): ArticleFailureReason? {
        if (message == null) return null
        if (knownMessage(message) in listOf(ArticleFailureKind.AUTH, ArticleFailureKind.RATE_LIMIT)) return null
        return when {
            listOf("验证码", "访问验证", "验证访问").any(message::contains) -> ArticleFailureReason.VALIDATION
            listOf("主题已删除", "主题已被删除", "帖子已删除", "帖子已被删除").any(message::contains) -> ArticleFailureReason.DELETED
            listOf("没有权限", "无权限").any(message::contains) -> ArticleFailureReason.NO_PERMISSION
            listOf("无此页", "无此主题", "主题不存在", "帖子不存在").any(message::contains) -> ArticleFailureReason.MISSING
            else -> null
        }
    }

    @JvmStatic fun failure(error: Throwable): ArticleFailure = error as? ArticleFailure
        ?: ArticleFailure(if (error is java.io.IOException) ArticleFailureKind.NETWORK else ArticleFailureKind.PROTOCOL)
}
