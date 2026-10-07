package sp.phone.mvp.model.thread

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.junit.Assert.*
import org.junit.Test
import gov.anzong.androidnga.core.data.HtmlData
import gov.anzong.androidnga.core.decode.ForumBasicDecoder
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.profile.ArticleAuthorIds

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

    @Test fun pageTitleReachesMainPostRendererWithoutLeakingIntoReplies() {
        for (subject in listOf(null, "")) {
            val root = fixture(page = 1, floors = listOf(0, 1))
            row(root)["subject"] = subject
            val titles = mutableListOf<String?>()
            val titleParser = AppArticleParser(ArticleRowRenderer { row, _ -> titles.add(row.subject) },
                ArticleBlacklist { false })
            val raw = root.toJSONString()
            val data = titleParser.parse(raw, full, 1)
            assertEquals(listOf("Synthetic topic", null), titles)
            assertEquals(listOf("source 0", "source 1"), data.rowList.map { it.content })
            assertEquals(raw, data.rawData)
            assertTrue(data.isContentComplete)
        }
    }

    @Test fun explicitSubjectAndSubjectOnlyBodyKeepTheirExistingMeaning() {
        val root = fixture(page = 1, floors = listOf(0))
        row(root)["subject"] = "Main post title"
        assertEquals("Main post title", parse(root, page = 1).rowList.single().subject)
        row(root)["content"] = ""
        val post = parse(root, page = 1).rowList.single()
        assertNull(post.subject)
        assertEquals("Main post title", post.content)
    }

    @Test fun pageTitleDoesNotInventMainFloorOrMakeMissingSourceComplete() {
        val root = fixture(page = 1, floors = listOf(0))
        row(root).remove("content")
        val missingBody = parse(root, page = 1)
        assertEquals("Synthetic topic", missingBody.rowList.single().subject)
        assertFalse(missingBody.isContentComplete)
        assertFalse(ArticleRowPresentation.hasSource(missingBody.rowList.single()))
        row(root).remove("lou")
        assertNull(parse(root, page = 1).rowList.single().subject)
    }

    @Test fun mediaRenderingUsesPageContextAndPreservesEditableSource() {
        val source = "前[flash]./视频.mp4?x=1&amp;y=2[/flash]后"
        val mediaParser = AppArticleParser(ArticleRowRenderer { row, prefix ->
            val htmlData = HtmlData.create(ArticleSourceText.renderBody(row), "https://forum.example/")
            htmlData.setAttachmentsPrefix(prefix)
            row.formattedHtmlData = ForumBasicDecoder().decode(htmlData.rawData, htmlData)
        }, ArticleBlacklist { false })
        for (prefix in listOf("https://first.example/attachments", "http://img9.nga.cn/attachments")) {
            val root = fixture()
            root["attachPrefix"] = prefix
            row(root)["content"] = source
            val raw = root.toJSONString()
            val data = mediaParser.parse(raw, full, 2)
            assertEquals(raw, data.rawData)
            assertEquals(source, data.rowList.single().content)
            assertEquals("前<video src='$prefix/视频.mp4?x=1&amp;y=2' controls='controls'></video>后",
                data.rowList.single().formattedHtmlData)
        }
    }

    @Test fun variableAndSparsePagesPreserveRowsSourceAndPrefix() {
        for (size in listOf(10, 40)) {
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
        for (value in listOf(null, "opaque", JSONObject())) {
            val root = fixture()
            root["hot_post"] = value; root["html_head_extra"] = value
            row(root)["comment_to_id"] = value; row(root)["isTieTiao"] = value
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

    @Test fun parentMarkerNeverChangesPostKindAndReplyHeaderPreservesSource() {
        val root = fixture()
        row(root)["isTieTiao"] = true; row(root).remove("lou"); row(root).remove("author")
        var data = parse(root)
        assertEquals(ArticleRowKind.POST, data.rowList[0].presentation.kind)
        assertFalse(ArticleRowPresentation.hasUser(data.rowList[0]))
        assertFalse(ArticleRowPresentation.hasFloor(data.rowList[0]))
        row(root)["isTieTiao"] = "true"
        data = parse(root)
        assertEquals(ArticleRowKind.POST, data.rowList[0].presentation.kind)
        val text = "<b>Reply to [pid=2,3,4]Reply[/pid] Post by dollar $ \\ </b>body<b>keep</b>"
        val normalized = ArticleSourceText.normalizeReplyHeader(text)
        assertEquals("[b]Reply to [pid=2,3,4]Reply[/pid] Post by dollar $ \\ [/b]body<b>keep</b>", normalized)
        assertEquals(normalized, ArticleSourceText.normalizeReplyHeader(normalized))
        row(root)["content"] = text
        assertEquals(text, parse(root).rowList[0].content)
        assertEquals(root.toJSONString(), parse(root).rawData)
        assertEquals("5010", ArticleNavigation.quoteAddress(ThreadRowInfo().apply { pid = 5010; lou = -1 }))
    }

    @Test fun supportCountMatchesDefaultScoreIndependentOfOppositionAndPollMarkup() {
        for (good in listOf(0, 19, Int.MAX_VALUE, "31")) {
            for (bad in listOf(null, 0, 7, -2, "invalid", JSONObject())) {
                val root = fixture()
                row(root)["vote_good"] = good
                row(root)["vote_bad"] = bad
                row(root)["vote"] = "synthetic poll markup"
                val post = parse(root).rowList.single()
                assertTrue(post.presentation.scoreKnown)
                assertEquals(good.toString().toInt(), post.score)
                assertEquals("synthetic poll markup", post.vote)
                assertTrue(ArticleRowPresentation.canReply(post))
            }
        }
        for (good in listOf(null, -1, 1.5, "1.5", "invalid", 2147483648L, true, JSONObject())) {
            val root = fixture()
            row(root)["vote_good"] = good
            row(root)["vote_bad"] = 0
            assertFalse(parse(root).rowList.single().presentation.scoreKnown)
            assertTrue(parse(root).isContentComplete)
        }
    }

    private fun child(content: Any? = "child body") = JSONObject().apply {
        put("tid", 100001); put("pid", 60001); put("lou", 0); put("content", content)
        put("author", JSONObject().apply { put("uid", 77); put("username", "Comment author") })
        put("postdate", "2026-01-01 12:00")
    }

    @Test fun parentWithCommentKeepsFloorActionsAndAuthorFilterWhileHotPostsStayExcluded() {
        val root = fixture(page = 1, floors = listOf(0))
        row(root)["pid"] = 0
        row(root)["isTieTiao"] = true
        row(root)["comments"] = JSONArray().apply { add(child()) }
        root["hot_post"] = JSONArray().apply { add(child()) }
        val data = parse(root, ArticleQuery(100001, 0, 42, 0), 1)
        val parent = data.rowList.single()
        val nested = parent.comments.single()
        assertEquals(1, data.rowNum)
        assertEquals(ArticleRowKind.POST, parent.presentation.kind)
        assertEquals(0, parent.lou)
        assertTrue(ArticleRowPresentation.canReply(parent))
        assertTrue(ArticleRowPresentation.isThreadAuthor(parent, null))
        assertTrue(ArticleRowPresentation.hasFloor(parent))
        assertTrue(parent.isInBlackList)
        assertEquals(ArticleRowKind.COMMENT, nested.presentation.kind)
        assertFalse(ArticleRowPresentation.hasFloor(nested))
        assertEquals(77, nested.authorid)
        assertFalse(nested.isInBlackList)
        assertEquals(setOf(42), ArticleAuthorIds.fromPage(data))
        assertSame(nested, rendered.last().first.comments.single())
        assertTrue(data.isContentComplete)
        assertNull(parent.hotReplies)
    }

    @Test fun damagedSupplementalDataKeepsReadableParentAndSignalsIncompleteDisplay() {
        for (field in listOf("attches", "comments")) {
            for (value in listOf("invalid", JSONObject(), JSONArray().apply { add(null); add(4) })) {
                val root = fixture()
                row(root)[field] = value
                val data = parse(root)
                val parent = data.rowList.single()
                assertEquals("source 10", parent.content)
                assertFalse(data.isContentComplete)
                assertTrue(ArticleRowPresentation.canReply(parent))
                assertTrue(ArticleSourceText.renderBody(parent)!!.contains("暂无法完整显示"))
            }
            for (value in listOf(null, JSONArray())) {
                val root = fixture()
                row(root)[field] = value
                assertTrue(parse(root).isContentComplete)
            }
        }
    }

    @Test fun compatibilityPageUsesSharedLocationAuthorSelection() {
        val root = fixture(floors = listOf(10, 11, 12, 13))
        val rows = root.getJSONArray("result")
        rows.getJSONObject(1).getJSONObject("author")["annoy"] = "#anony_synthetic"
        rows.getJSONObject(2).remove("author")
        rows.getJSONObject(3).getJSONObject("author")["uid"] = 0
        val data = parse(root)
        assertEquals(setOf(42), ArticleAuthorIds.fromPage(data))
        assertFalse(ArticleRowPresentation.hasUser(data.rowList[1]))
        assertFalse(ArticleRowPresentation.isThreadAuthor(data.rowList[1], "Synthetic author"))
    }

    @Test fun damagedChildBodyOrIdentityRemainsVisibleWithoutLosingItsParent() {
        val damagedChildren = listOf(child(JSONObject()), child().apply { remove("pid") },
            child().apply { put("tid", 999) }, child().apply { put("pid", 0) })
        for (damaged in damagedChildren) {
            val root = fixture()
            row(root)["comments"] = JSONArray().apply { add(damaged) }
            val data = parse(root)
            val parent = data.rowList.single()
            val nested = parent.comments.single()
            assertFalse(data.isContentComplete)
            assertTrue(ArticleRowPresentation.canReply(parent))
            assertFalse(ArticleRowPresentation.canReply(nested))
            assertTrue(ArticleSourceText.renderBody(nested)!!.contains("暂无法完整显示"))
            assertEquals("source 10", parent.content)
            assertEquals(root.toJSONString(), data.rawData)
        }
    }

    @Test fun unsupportedChildSupplementsAreVisibleAndCannotMakeACompletePage() {
        for (field in listOf("attches", "comments")) {
            val root = fixture()
            val supplement = if (field == "attches") JSONObject().apply { put("attachurl", "mon_test/child.jpg") }
                else child()
            val nested = child().apply { put(field, JSONArray().apply { add(supplement) }) }
            row(root)["comments"] = JSONArray().apply { add(nested) }
            val data = parse(root)
            val comment = data.rowList.single().comments.single()
            assertEquals("child body", comment.content)
            assertFalse(data.isContentComplete)
            assertTrue(ArticleSourceText.renderBody(comment)!!.contains("暂无法完整显示"))
        }
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
