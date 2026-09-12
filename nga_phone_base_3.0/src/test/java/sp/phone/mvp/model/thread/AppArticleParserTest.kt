package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.ThreadRowInfo

/** Synthetic offline source shapes, not captured NGA responses or an availability claim. */
class AppArticleParserTest {
    private val rendered = mutableListOf<Pair<ThreadRowInfo, String>>()
    private val parser = AppArticleParser(ArticleRowRenderer { row, prefix -> rendered.add(row to prefix) }, ArticleBlacklist { it == "42" })
    private val full = ArticleQuery(100001, 0, 0, 0)

    private fun fixture(size: Int = 10, page: Int = 2, floors: List<Int> = listOf((page - 1) * size)): JSONObject {
        return JSONObject().apply {
            put("currentPage", page); put("perPage", size); put("totalPage", 30); put("vrows", 300)
            put("tsubject", "Synthetic topic"); put("tauthorid", 42)
            put("attachPrefix", "http://page.example/attachments/")
            put("result", JSONArray().apply { floors.forEach { floor -> add(JSONObject().apply {
                put("tid", 100001); put("pid", 50000 + floor); put("lou", floor); put("content", "source $floor")
                put("author", JSONObject().apply { put("uid", 42); put("username", "Synthetic author") })
            }) } })
        }
    }
    private fun parse(root: JSONObject, query: ArticleQuery = full, page: Int = 2) = parser.parse(root.toJSONString(), query, page)
    private fun row(root: JSONObject) = root.getJSONArray("result").getJSONObject(0)
    private fun fails(kind: ArticleFailureKind, block: () -> Unit) {
        try { block(); fail("Expected $kind") } catch (e: ArticleFailure) { assertEquals(kind, e.kind) }
    }

    @Test fun variableAndSparsePagesPreserveRowsSourceAndPrefix() {
        for (size in listOf(10, 20, 30, 40)) {
            val root = fixture(size, floors = listOf(size, size + 3, size + 8))
            val data = parse(root)
            assertEquals(listOf(size, size + 3, size + 8), data.rowList.map { it.lou })
            assertEquals(3, data.rowNum)
            assertEquals(size, data.pagingInfo.pageSize)
            assertEquals(root.toJSONString(), data.rawData)
            assertEquals("http://page.example/attachments", rendered.last().second)
            assertTrue(data.rowList[0].isInBlackList)
            assertEquals(true, data.rowList[0].presentation.threadAuthor)
            assertFalse(data.rowList[0].presentation.scoreKnown)
        }
    }

    @Test fun optionalOpaqueExtensionsNeverRejectOrEnterSource() {
        for (value in listOf(null, "", "0", 0, "[]", "{}", "opaque", JSONArray(), JSONObject(), true)) {
            val root = fixture()
            root["hot_post"] = value; root["html_head_extra"] = value
            row(root)["attches"] = value; row(root)["comment_to_id"] = value
            root["code"] = 92837; root["msg"] = "synthetic metadata"
            val data = parse(root)
            assertEquals("source 10", data.rowList[0].content)
            assertTrue(data.isContentComplete)
            assertNull(data.rowList[0].attachs)
            assertNull(data.rowList[0].comments)
            assertEquals(root.toJSONString(), data.rawData)
        }
    }

    @Test fun pidOnlyAndAuthorQueriesKeepTheirActualCoordinates() {
        val root = fixture(page = 1, floors = listOf(3, 85, 173))
        val lookup = parse(root, ArticleQuery(0, 50173, 42, 1), 1)
        assertEquals(100001, lookup.pagingInfo.resolvedTid)
        assertEquals(2, ArticleAnchor(0, 1, 50173, null).find(lookup.rowList))
        assertNull(lookup.pagingInfo.totalPages)
        val author = parse(root, ArticleQuery(100001, 0, 42, 0), 1)
        assertFalse(author.pagingInfo.canEstimateFloorPage)
        assertEquals(listOf(3, 85, 173), author.rowList.map { it.lou })
        fails(ArticleFailureKind.CONTENT) { parse(root, ArticleQuery(0, 59999, 0, 1), 1) }
        fails(ArticleFailureKind.CONTENT) { parse(root, ArticleQuery(100001, 0, 7, 0), 1) }
        row(root)["tid"] = 999
        fails(ArticleFailureKind.CONTENT) { parse(root) }
    }

    @Test fun optionalPaginationOnlyLimitsDependentNavigation() {
        val root = fixture()
        root.remove("perPage")
        assertEquals(30, parse(root).pagingInfo.totalPages)
        assertNull(parse(root).pagingInfo.candidatePage(30))
        root.remove("totalPage")
        assertNull(parse(root).pagingInfo.totalPages)
        root["perPage"] = 10
        assertEquals(30, parse(root).pagingInfo.totalPages)
        root["perPage"] = 999999999999L
        assertNull(parse(root).pagingInfo.pageSize)
        assertTrue(parse(root).pagingInfo.invalidMetadata)
        root["currentPage"] = -1
        assertEquals(2, parse(root).pagingInfo.effectivePage)
        assertNull(parse(root).pagingInfo.totalPages)
    }

    @Test fun unavailableBodyIsRetainedAndCannotPretendToBeComplete() {
        val root = fixture()
        row(root).remove("content")
        val data = parse(root)
        assertEquals(1, data.rowNum)
        assertFalse(data.isContentComplete)
        assertFalse(ArticleRowPresentation.hasSource(data.rowList[0]))
        row(root)["subject"] = "subject source"
        assertEquals("subject source", parse(root).rowList[0].content)
        assertTrue(parse(root).isContentComplete)
        row(root).remove("subject"); row(root)["content"] = ""
        assertTrue(parse(root).isContentComplete)
    }

    @Test fun commentUnknownIdentityAndReplyHeaderAreExplicit() {
        val root = fixture()
        row(root)["isTieTiao"] = true; row(root).remove("lou"); row(root).remove("author")
        var data = parse(root)
        assertEquals(ArticleRowKind.COMMENT, data.rowList[0].presentation.kind)
        assertFalse(ArticleRowPresentation.hasUser(data.rowList[0]))
        assertFalse(ArticleRowPresentation.hasFloor(data.rowList[0]))
        row(root)["isTieTiao"] = "true"
        data = parse(root)
        assertEquals(ArticleRowKind.UNKNOWN, data.rowList[0].presentation.kind)
        val text = "<b>Reply to [pid=2,3,4]Reply[/pid] Post by dollar $ \\ </b>body<b>keep</b>"
        val normalized = ArticleSourceText.normalizeReplyHeader(text)
        assertEquals("[b]Reply to [pid=2,3,4]Reply[/pid] Post by dollar $ \\ [/b]body<b>keep</b>", normalized)
        assertEquals(normalized, ArticleSourceText.normalizeReplyHeader(normalized))
        row(root)["content"] = text
        assertEquals(text, parse(root).rowList[0].content)
        assertEquals(root.toJSONString(), parse(root).rawData)
        assertEquals("5010", ArticleNavigation.quoteAddress(ThreadRowInfo().apply { pid = 5010; lou = -1 }))
    }

    @Test fun malformedBodyWithValidSubjectStillMarksPageIncomplete() {
        for (body in listOf(JSONObject(), JSONArray())) {
            val root = fixture()
            row(root)["content"] = body
            row(root)["subject"] = "readable subject"
            val data = parse(root)
            assertEquals(1, data.rowNum)
            assertEquals("readable subject", data.rowList[0].content)
            assertFalse(data.isContentComplete)
            assertFalse(ArticleRowPresentation.hasSource(data.rowList[0]))
        }
    }

    @Test fun knownErrorsWinOverResidualDataAndCodeAloneIsNotSuccess() {
        val root = fixture()
        root["msg"] = "未登录"
        fails(ArticleFailureKind.AUTH) { parse(root) }
        root.remove("msg"); root["error"] = JSON.parseObject("{\"0\":\"无此页\"}")
        fails(ArticleFailureKind.BUSINESS) { parse(root) }
        fails(ArticleFailureKind.FORMAT) { parser.parse("{\"code\":200}", full, 1) }
        fails(ArticleFailureKind.ACCESS) { parser.parse("<html>challenge</html>", full, 1) }
        root.remove("error"); root["result"] = JSONArray()
        fails(ArticleFailureKind.EMPTY) { parse(root) }
    }
}
