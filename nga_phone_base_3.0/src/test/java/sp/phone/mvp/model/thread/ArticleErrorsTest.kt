package sp.phone.mvp.model.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** Decoded synthetic bodies only; classification must stop before any alternate request. */
class ArticleErrorsTest {
    private val query = ArticleQuery(100001, 0, 0, 0)
    private val noBlacklist = ArticleBlacklist { false }

    private fun assertTerminal(raw: String, kind: ArticleFailureKind) {
        try {
            ArticleErrors.json(raw)
            fail("Expected a terminal response")
        } catch (failure: ArticleFailure) {
            assertEquals(kind, failure.kind)
            assertFalse(failure.allowsAppFallback())
        }
    }

    @Test fun bomPrefixedUnknownHtmlIsStillAnAccessStop() {
        for (prefix in listOf("", "\uFEFF", " \r\n\uFEFF\t")) {
            assertTerminal(prefix + "<!doctype html><html>synthetic page</html>", ArticleFailureKind.ACCESS)
        }
    }

    @Test fun bomAndWhitespaceWithoutContentRemainEmpty() {
        for (raw in listOf("", " \n\t", "\uFEFF", " \r\n\uFEFF\t\u3000")) {
            assertTerminal(raw, ArticleFailureKind.EMPTY)
        }
    }

    private fun capture(block: () -> Unit): ArticleFailure {
        try { block() } catch (failure: ArticleFailure) { return failure }
        throw AssertionError("Expected failure")
    }

    @Test fun actualHttpEvidencePreservesStatusAndRecoveryPolicy() {
        val cases = listOf(
            Triple(403, ArticleFailureKind.ACCESS, "无法访问帖子（HTTP 403）"),
            Triple(302, ArticleFailureKind.ACCESS, "帖子请求发生重定向（HTTP 302）"),
            Triple(401, ArticleFailureKind.AUTH, "登录状态已失效，请重新登录（HTTP 401）"),
            Triple(429, ArticleFailureKind.RATE_LIMIT, "请求过于频繁，请稍后重试（HTTP 429）"),
            Triple(503, ArticleFailureKind.PROTOCOL, "帖子请求失败（HTTP 503）"),
        )
        for ((status, kind, message) in cases) {
            val failure = capture { ArticleErrors.classifyHttp(status) }
            assertEquals(status, failure.httpStatus)
            assertEquals(kind, failure.kind)
            assertEquals(message, failure.message)
            assertEquals(null, failure.reason)
            assertFalse(failure.allowsAppFallback())
            assertEquals(kind == ArticleFailureKind.PROTOCOL, failure.allowsBrowser())
        }
        ArticleErrors.classifyHttp(200)
    }

    @Test fun structuredCausesHaveBoundedMessagesWithoutFabricatingHttpStatus() {
        val cases = listOf(
            Triple("请输入验证码", ArticleFailureKind.ACCESS, "站点要求访问验证，请稍后手动重试"),
            Triple("访问受限", ArticleFailureKind.ACCESS, "暂时无法访问帖子"),
            Triple("访问限制，没有权限", ArticleFailureKind.ACCESS, "没有权限访问帖子"),
            Triple("主题已被删除", ArticleFailureKind.BUSINESS, "帖子已被删除"),
            Triple("帖子不存在", ArticleFailureKind.BUSINESS, "请求的帖子或页面不存在"),
            Triple("没有权限", ArticleFailureKind.BUSINESS, "没有权限访问帖子"),
        )
        for ((siteText, kind, message) in cases) {
            for (raw in listOf(
                """{"msg":"$siteText"}""",
                """{"data":{"__MESSAGE":{"1":"$siteText"}}}""",
                """{"error":{"0":"$siteText"}}""",
            )) {
                val failure = capture { ArticleErrors.json(raw) }
                assertEquals(kind, failure.kind)
                assertEquals(message, failure.message)
                assertEquals(null, failure.httpStatus)
                assertFalse(failure.allowsAppFallback())
                assertFalse(failure.allowsBrowser())
            }
        }
        val unknown = capture { ArticleErrors.json("""{"error":"synthetic private text HTTP 403"}""") }
        assertEquals("站点未能提供所请求的帖子", unknown.message)
        assertEquals(null, unknown.httpStatus)
    }

    @Test fun arbitraryHtmlDoesNotProveDeletionChallengeOrHttpStatus() {
        val failure = capture { ArticleErrors.bodyText("<html>帖子已删除 HTTP 403 验证码</html>") }
        assertEquals("站点返回了网页内容，暂无法读取帖子", failure.message)
        assertEquals(ArticleFailureReason.HTML, failure.reason)
        assertEquals(null, failure.httpStatus)
        assertFalse(failure.allowsAppFallback())
        assertFalse(failure.allowsBrowser())
    }

    @Test fun ordinaryContentMentionsDoNotBecomeSiteErrors() {
        val source = "帖子已删除、HTTP 403、没有权限和验证码只是讨论内容"
        val raw = """{"currentPage":1,"perPage":20,"totalPage":1,"result":[{"tid":100001,"pid":50001,"lou":1,"content":"$source","author":{"uid":42,"username":"Synthetic"}}]}"""
        val data = AppArticleParser(ArticleRowRenderer { _, _ -> }, noBlacklist).parse(raw, query, 1)
        assertEquals(source, data.rowList.single().content)
        assertEquals(raw, data.rawData)
    }

    @Test fun legacyHttpPresentationRetainsUnrecognizedExceptionsAndTheirPolicyIdentity() {
        val response = retrofit2.Response.error<String>(403, okhttp3.ResponseBody.create(null, "ignored"))
        val http = retrofit2.HttpException(response)
        assertEquals("无法访问帖子（HTTP 403）", ArticleErrors.legacyMessage(http, http.message))
        val generic = sp.phone.mvp.model.ArticleListModel.ServerException("existing parser message")
        assertEquals("existing parser message", ArticleErrors.legacyMessage(generic, generic.message))
        assertEquals("existing structured message", ArticleErrors.legacyMessage(Exception(), "existing structured message"))
    }

}
