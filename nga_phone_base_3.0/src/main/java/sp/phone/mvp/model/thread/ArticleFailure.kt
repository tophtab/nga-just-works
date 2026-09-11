package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject

/** These are local outcomes, not guessed meanings for App API numeric codes. */
enum class ArticleFailureKind { FORMAT, CONTENT, EMPTY, AUTH, ACCESS, RATE_LIMIT, BUSINESS, NETWORK, PROTOCOL, CANCELLED, STALE, CACHE }

class ArticleFailure(@JvmField val kind: ArticleFailureKind) : RuntimeException(when (kind) {
    ArticleFailureKind.FORMAT -> "帖子数据格式暂不兼容"
    ArticleFailureKind.CONTENT -> "返回内容与当前查询不一致，暂无法显示"
    ArticleFailureKind.EMPTY -> "未返回帖子内容"
    ArticleFailureKind.AUTH -> "登录状态已失效，请重新登录"
    ArticleFailureKind.ACCESS -> "站点要求访问验证，请稍后手动重试"
    ArticleFailureKind.RATE_LIMIT -> "请求过于频繁，请稍后重试"
    ArticleFailureKind.BUSINESS -> "站点未能提供所请求的帖子"
    ArticleFailureKind.NETWORK -> "网络连接失败，请重试"
    ArticleFailureKind.PROTOCOL -> "帖子响应无法读取"
    ArticleFailureKind.CANCELLED, ArticleFailureKind.STALE -> "读取已结束"
    ArticleFailureKind.CACHE -> "读取缓存失败！"
}) {
    fun allowsAppFallback() = kind == ArticleFailureKind.FORMAT
    fun allowsBrowser() = kind == ArticleFailureKind.FORMAT || kind == ArticleFailureKind.CONTENT || kind == ArticleFailureKind.PROTOCOL
}

object ArticleErrors {
    @JvmStatic fun classifyHttp(status: Int) {
        when (status) {
            401 -> throw ArticleFailure(ArticleFailureKind.AUTH)
            403 -> throw ArticleFailure(ArticleFailureKind.ACCESS)
            429 -> throw ArticleFailure(ArticleFailureKind.RATE_LIMIT)
            in 300..399 -> throw ArticleFailure(ArticleFailureKind.ACCESS)
            !in 200..299 -> throw ArticleFailure(ArticleFailureKind.PROTOCOL)
        }
    }

    /** Strip only a leading Unicode BOM/outer whitespace; callers retain the original response. */
    @JvmStatic fun bodyText(raw: String): String {
        val text = raw.trim().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw ArticleFailure(ArticleFailureKind.EMPTY)
        // Unknown HTML is an access stop, never evidence for an alternate authenticated request.
        if (text.startsWith("<")) throw ArticleFailure(ArticleFailureKind.ACCESS)
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
        knownMessage(message)?.let { throw ArticleFailure(it) }
        val data = root["data"] as? JSONObject
        val siteMessage = data?.get("__MESSAGE")
        if (siteMessage != null) {
            val text = (siteMessage as? JSONObject)?.get("1") as? String
            throw ArticleFailure(knownMessage(text) ?: ArticleFailureKind.BUSINESS)
        }
        val error = root["error"]
        if (error != null && error != false && error != "") {
            val text = (error as? JSONObject)?.get("0") as? String ?: error as? String
            throw ArticleFailure(knownMessage(text) ?: ArticleFailureKind.BUSINESS)
        }
    }

    private fun knownMessage(message: String?): ArticleFailureKind? {
        if (message == null) return null
        return when {
            listOf("未登录", "请先登录", "重新登录", "登录失效").any(message::contains) -> ArticleFailureKind.AUTH
            listOf("验证码", "访问验证", "访问受限", "访问限制", "验证访问").any(message::contains) -> ArticleFailureKind.ACCESS
            listOf("过于频繁", "频率限制", "请求频繁").any(message::contains) -> ArticleFailureKind.RATE_LIMIT
            listOf("无此页", "无此主题", "主题不存在", "帖子不存在", "没有权限").any(message::contains) -> ArticleFailureKind.BUSINESS
            else -> null
        }
    }

    @JvmStatic fun failure(error: Throwable): ArticleFailure = error as? ArticleFailure
        ?: ArticleFailure(if (error is java.io.IOException) ArticleFailureKind.NETWORK else ArticleFailureKind.PROTOCOL)
}
