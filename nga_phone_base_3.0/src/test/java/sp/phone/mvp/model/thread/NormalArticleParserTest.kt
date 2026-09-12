package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.convert.ArticleConvertFactory
import sp.phone.util.FunctionUtils

class NormalArticleParserTest {
    private val query = ArticleQuery(100001, 0, 0, 0)
    private fun fixture(prefix: String = "https://first.example/attachments/") = JSON.parseObject("""
        {"data":{"__ROWS":160,"__R__ROWS":1,"__T":{"tid":100001,"authorid":42,"subject":"topic"},
          "__GLOBAL":{"_ATTACH_BASE_VIEW":"$prefix"},
          "__U":{"42":{"username":"author","avatar":"http://img.nga.178.com/a.jpg","rvrc":"10","postnum":"5"},
                   "43":{"username":"commenter","avatar":"https://avatar.example/a.jpg","rvrc":"0"}},
          "__R":{"0":{"tid":100001,"pid":50120,"lou":120,"authorid":42,"from_client":"103 original device",
                       "content":"%u4E2D%u6587","score":5,"attachs":{"0":{"attachurl":"mon_test/a.jpg","thumb":"1"}},
                       "comment":{"0":{"tid":100001,"pid":50121,"authorid":43,"content":"comment source"}}}}}}
    """)

    @Test fun scopedAndLegacySeamsPreserveAttachmentCommentBlacklistAndWpPreparationOrder() {
        val snapshots = mutableListOf<Pair<ThreadRowInfo, String>>()
        val renderer = ArticleRowRenderer { row, prefix ->
            snapshots.add(row to prefix)
            if (row.pid == 50120) {
                assertEquals("中文", row.content)
                assertEquals("mon_test/a.jpg", row.attachs["0"]!!.attachurl)
                assertEquals("commenter", row.comments.single().author)
                assertEquals("https://avatar.example/a.jpg", row.comments.single().js_escap_avatar)
                assertTrue(row.isInBlackList)
                assertEquals(5, row.score)
                assertEquals(true, row.presentation.threadAuthor)
                assertFalse(FunctionUtils.isComment(row))
                assertEquals("wp", row.fromClientModel)
                assertEquals("103 original device", row.fromClient)
            } else {
                assertTrue(FunctionUtils.isComment(row))
                assertFalse(ArticleRowPresentation.hasFloor(row))
            }
        }
        val raw = "\uFEFF/*${'$'}js${'$'}*/" + fixture().toJSONString() + "/*error fill content synthetic*/"
        val normal = NormalArticleParser(renderer) { it == "42" }.parse(raw, query, 7)
        assertEquals(listOf(50121, 50120), snapshots.map { it.first.pid })
        assertTrue(snapshots.all { it.second == "https://first.example/attachments" })
        assertEquals(raw, normal.rawData)
        assertEquals(8, normal.pagingInfo.totalPages)
        assertEquals(20, normal.pagingInfo.pageSize)
        assertEquals(160, normal.__ROWS)
        val legacy = ArticleConvertFactory.getArticleInfo(raw, renderer) { it == "42" }
        assertNotNull(legacy)
        assertEquals(normal.rowList[0].content, legacy.rowList[0].content)
        assertEquals(normal.rawData, legacy.rawData)
    }

    @Test fun pagePrefixesStayIndependentAndUidIdentityWorksWithoutFirstFloor() {
        val prefixes = mutableListOf<String>()
        val parser = NormalArticleParser(ArticleRowRenderer { _, prefix -> prefixes.add(prefix) }, ArticleBlacklist { false })
        val first = parser.parse(fixture().toJSONString(), ArticleQuery(100001, 50120, 42, 1), 7)
        assertEquals(true, first.rowList.single().presentation.threadAuthor)
        assertNull(first.pagingInfo.totalPages)
        assertFalse(first.pagingInfo.canEstimateFloorPage)
        val second = parser.parse(fixture("https://second.example/attachments").toJSONString(), ArticleQuery(100001, 0, 42, 0), 7)
        assertEquals(8, second.pagingInfo.totalPages)
        assertFalse(second.pagingInfo.canEstimateFloorPage)
        assertEquals(listOf("https://first.example/attachments", "https://first.example/attachments",
            "https://second.example/attachments", "https://second.example/attachments"), prefixes)
    }

    @Test fun knownErrorsAndWrongQueryAreRejectedBeforeSuccessfulDelivery() {
        var renders = 0
        val parser = NormalArticleParser(ArticleRowRenderer { _, _ -> renders++ }, ArticleBlacklist { false })
        val root = fixture()
        root["error"] = JSON.parseObject("{\"0\":\"未登录\"}")
        try { parser.parse(root.toJSONString(), query, 7); fail() }
        catch (e: ArticleFailure) { assertEquals(ArticleFailureKind.AUTH, e.kind) }
        assertEquals(0, renders)
        root.remove("error")
        try { parser.parse(root.toJSONString(), ArticleQuery(100002, 0, 0, 0), 7); fail() }
        catch (e: ArticleFailure) { assertEquals(ArticleFailureKind.CONTENT, e.kind) }
        try { parser.parse(root.toJSONString(), ArticleQuery(0, 99999, 0, 1), 7); fail() }
        catch (e: ArticleFailure) { assertEquals(ArticleFailureKind.CONTENT, e.kind) }
    }

    @Test fun malformedCoreBodyIsNotSerializedIntoAnApparentlyCompletePost() {
        val parser = NormalArticleParser(ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
        for (body in listOf(JSON.parseObject("{}"), JSON.parseArray("[]"))) {
            val root = fixture()
            val row = root.getJSONObject("data").getJSONObject("__R").getJSONObject("0")
            row["content"] = body
            row["subject"] = "readable subject"
            val data = parser.parse(root.toJSONString(), query, 7)
            assertFalse(data.isContentComplete)
            assertFalse(ArticleRowPresentation.hasSource(data.rowList.single()))
            assertEquals("readable subject", data.rowList.single().content)
            assertEquals(50120, data.rowList.single().pid)
            assertEquals(root.toJSONString(), data.rawData)

            val legacy = ArticleConvertFactory.getArticleInfo(root.toJSONString(), ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
            assertTrue(legacy.isContentComplete)
            assertEquals(body.toString(), legacy.rowList.single().content)
        }
    }

    @Test fun scopedSourceValidationKeepsKnownEmptySubjectAlterNumericAndWpBehavior() {
        val parser = NormalArticleParser(ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
        for ((content, subject, alter) in listOf(
            Triple("", null, null), Triple(null, "subject only", null), Triple(null, null, "moderation notice"),
            Triple(123, null, null), Triple("%u4E2D%u6587", null, null),
        )) {
            val root = fixture()
            val row = root.getJSONObject("data").getJSONObject("__R").getJSONObject("0")
            row["content"] = content; row["subject"] = subject; row["alterinfo"] = alter
            val data = parser.parse(root.toJSONString(), query, 7)
            assertTrue(data.isContentComplete)
            assertTrue(data.rowList.single().presentation.sourceAvailable)
            if (content == "%u4E2D%u6587") assertEquals("中文", data.rowList.single().content)
            if (content is Int) assertEquals("123", data.rowList.single().content)
        }
        val missing = fixture()
        missing.getJSONObject("data").getJSONObject("__R").getJSONObject("0").remove("content")
        assertFalse(parser.parse(missing.toJSONString(), query, 7).isContentComplete)
    }

    @Test fun unusableNestedCommentKeepsItsKnownParentAndMarksThePageIncomplete() {
        val root = fixture()
        val comments = root.getJSONObject("data").getJSONObject("__R").getJSONObject("0").getJSONObject("comment")
        comments.getJSONObject("0")["content"] = JSON.parseObject("{}")
        val parser = NormalArticleParser(ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
        val data = parser.parse(root.toJSONString(), query, 7)
        val parent = data.rowList.single()
        val comment = parent.comments.single()
        assertEquals("中文", parent.content)
        assertEquals(50121, comment.pid)
        assertEquals(100001, comment.tid)
        assertEquals(43, comment.authorid)
        assertNull(comment.content)
        assertTrue(parent.presentation.sourceAvailable)
        assertFalse(comment.presentation.sourceAvailable)
        assertFalse(ArticleRowPresentation.hasSource(comment))
        assertTrue(ArticleSourceText.renderBody(comment)!!.contains("此条内容暂无法完整显示"))
        assertFalse(data.isContentComplete)
        assertEquals(root.toJSONString(), data.rawData)
    }
}
