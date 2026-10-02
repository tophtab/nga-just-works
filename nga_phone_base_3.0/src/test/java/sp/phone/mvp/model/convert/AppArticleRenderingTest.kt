package sp.phone.mvp.model.convert

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import gov.anzong.androidnga.core.corebuild.HtmlAttachmentBuilder
import gov.anzong.androidnga.core.corebuild.HtmlCommentBuilder
import gov.anzong.androidnga.core.data.HtmlData
import gov.anzong.androidnga.core.decode.ForumDecoder
import org.junit.Assert.*
import org.junit.Test
import sp.phone.mvp.model.thread.AppArticleParser
import sp.phone.mvp.model.thread.ArticleBlacklist
import sp.phone.mvp.model.thread.ArticleQuery
import sp.phone.mvp.model.thread.ArticleRowRenderer
import sp.phone.mvp.model.thread.ArticleSourceText

/** Exercises the same row projections and core builders as the ordinary reader, without Android settings. */
class AppArticleRenderingTest {
    private fun fixture() = JSONObject().apply {
        put("currentPage", 1); put("perPage", 20); put("totalPage", 1)
        put("result", JSONArray().apply { add(JSONObject().apply {
            put("tid", 100001); put("pid", 0); put("lou", 0); put("content", "parent body")
        }) })
    }

    private fun row(root: JSONObject) = root.getJSONArray("result").getJSONObject(0)

    private fun attachment(path: Any?, thumb: Any?) = JSONObject().apply {
        put("attachurl", path); put("thumb", thumb)
    }

    private val parser = AppArticleParser(ArticleRowRenderer { row, prefix ->
        val html = HtmlData.create(ArticleSourceText.renderBody(row), "https://forum.example/")
        html.attachmentsPrefix = prefix
        if (row.attachs != null) html.attachmentList = ArticleConvertFactory.buildAttachmentData(row.attachs)
        if (row.comments != null) html.commentList = ArticleConvertFactory.buildCommentData(row.comments)
        row.formattedHtmlData = ForumDecoder.decode(html.rawData, html, row.imageUrls) +
            HtmlAttachmentBuilder().build(html, row.imageUrls) + renderComments(html)
    }, ArticleBlacklist { false })

    private fun parse(root: JSONObject) = parser.parse(root.toJSONString(), ArticleQuery(100001, 0, 0, 0), 1)

    // Only replace asset loading; all comment header stripping, decoding and row HTML use production code.
    private fun renderComments(html: HtmlData): String {
        val template = HtmlCommentBuilder::class.java.getDeclaredField("sFormattedHtml")
        template.isAccessible = true
        val previous = template.get(null)
        return try {
            template.set(null, "<table>%s</table>")
            HtmlCommentBuilder().build(html).toString()
        } finally {
            template.set(null, previous)
        }
    }

    @Test fun attachmentAndInlineMediaUseOnePagePrefixAndDeduplicateImageViewerEntries() {
        val source = "[img]./mon_test/a.jpg[/img][flash]./mon_test/v.mp4[/flash]"
        for (prefix in listOf("https://first.example/attachments", "http://img9.nga.cn/attachments")) {
            val root = fixture().apply { put("attachPrefix", prefix) }
            row(root)["content"] = source
            row(root)["attches"] = JSONArray().apply {
                add(attachment("mon_test/a.jpg", "56"))
                add(attachment("mon_test/b.jpg", "120"))
                add(attachment("mon_test/c.jpg", "1"))
                add(attachment("mon_test/sound.mp3", "0"))
                add(attachment("mon_test/v.mp4", "0"))
            }
            val data = parse(root)
            val post = data.rowList.single()
            val html = post.formattedHtmlData
            assertEquals(source, post.content)
            assertEquals(root.toJSONString(), data.rawData)
            assertTrue(html.contains("附件"))
            assertTrue(html.contains("<video src='$prefix/mon_test/v.mp4'"))
            assertTrue(html.contains("href='$prefix/mon_test/sound.mp3'"))
            assertTrue(html.contains("$prefix/mon_test/c.jpg.thumb.jpg"))
            assertFalse(html.contains("a.jpg.thumb.jpg"))
            assertFalse(html.contains("b.jpg.thumb.jpg"))
            assertEquals(listOf("$prefix/mon_test/a.jpg", "$prefix/mon_test/b.jpg", "$prefix/mon_test/c.jpg"), post.imageUrls)
            assertTrue(post.attachs.values.all { it.aid == null })
            assertTrue(data.isContentComplete)
        }
    }

    @Test fun invalidAttachmentKeepsValidSiblingsAndParentBodyWithNotice() {
        val root = fixture()
        row(root)["attches"] = JSONArray().apply {
            add(attachment("mon_test/valid.jpg", "56"))
            add(attachment(null, "1"))
            add(attachment(JSONObject(), "1"))
            add(attachment("mon_test/unknown-thumb.jpg", 120))
        }
        val data = parse(root)
        val post = data.rowList.single()
        assertEquals("parent body", post.content)
        assertTrue(post.formattedHtmlData.contains("parent body"))
        assertTrue(post.formattedHtmlData.contains("暂无法完整显示"))
        assertEquals(2, post.imageUrls.size)
        assertTrue(post.imageUrls[0].endsWith("/mon_test/valid.jpg"))
        assertFalse(post.formattedHtmlData.contains("/null"))
        assertFalse(data.isContentComplete)
    }

    @Test fun nestedCommentUsesDefaultAuthorTimeAvatarAndHeaderlessBodyRendering() {
        val root = fixture().apply { put("attachPrefix", "https://page.example/attachments") }
        val childSource = "<b>Reply to [pid=0,100001,1]Reply[/pid] Post by x </b>child[img]./mon_test/comment.jpg[/img]"
        row(root)["isTieTiao"] = true
        row(root)["comments"] = JSONArray().apply { add(JSONObject().apply {
            put("tid", 100001); put("pid", 60001); put("content", childSource)
            put("postdate", "2026-01-01 12:00")
            put("author", JSONObject().apply {
                put("uid", 77); put("username", "Comment author")
                put("avatar", "https://avatar.example/synthetic.jpg")
            })
        }) }
        val data = parse(root)
        val post = data.rowList.single()
        val html = post.formattedHtmlData
        assertEquals(childSource, post.comments.single().content)
        assertTrue(html.contains("parent body"))
        assertTrue(html.contains("Comment author (2026-01-01 12:00)"))
        assertTrue(html.contains("src='https://avatar.example/synthetic.jpg'"))
        assertTrue(html.contains("https://page.example/attachments/mon_test/comment.jpg"))
        assertFalse(html.contains("Reply to"))
        assertTrue(data.isContentComplete)

        row(root).getJSONArray("comments").getJSONObject(0)["content"] = "短贴条正文"
        assertTrue(parse(root).rowList.single().formattedHtmlData.contains("短贴条正文"))
        row(root).getJSONArray("comments").getJSONObject(0)["content"] = JSONObject()
        val incomplete = parse(root)
        assertTrue(incomplete.rowList.single().formattedHtmlData.contains("暂无法完整显示"))
        assertTrue(incomplete.rowList.single().formattedHtmlData.contains("parent body"))
        assertFalse(incomplete.isContentComplete)
    }

    @Test fun incompleteCommentWithOnlyAlterInfoKeepsItsReadableTextBesideTheNotice() {
        for (source in listOf(null, "")) {
            val root = fixture()
            row(root)["comments"] = JSONArray().apply { add(JSONObject().apply {
                put("tid", 100001); put("pid", 60001); put("content", source)
                put("alterinfo", "readable comment notice")
                put("attches", "damaged attachment list")
            }) }
            val data = parse(root)
            val post = data.rowList.single()
            assertTrue(post.formattedHtmlData.contains("parent body"))
            assertTrue(post.formattedHtmlData.contains("暂无法完整显示"))
            assertTrue(post.formattedHtmlData.contains("readable comment notice"))
            assertFalse(data.isContentComplete)
        }
    }
}
