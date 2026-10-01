package sp.phone.mvp.model.thread

import com.alibaba.fastjson2.JSON
import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.TopicListBean
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.param.TopicListParam
import java.io.File

class ReplySearchNavigationTest {
    // Use the wire DTO without the Android/global filtering in TopicConvertFactory.
    private fun searchResult(topicAuthor: Int = 17): ThreadPageInfo {
        val bean = JSON.parseObject("""{"data":{"__T":{"0":{
            "tid":100001,"author":"topic author","authorid":"$topicAuthor","subject":"topic &amp; title",
            "__P":{"pid":50120,"authorid":"42","content":"reply","postdate":123}
        }}}}""", TopicListBean::class.java)
        val topic = bean.data.__T["0"]!!
        return ThreadPageInfo().apply {
            tid = topic.tid
            authorId = topic.authorid.toInt()
            subject = topic.subject
            page = 3
            pid = topic.__P.pid
            replyInfo = ThreadPageInfo.ReplyInfo().apply { authorId = topic.__P.authorid }
        }
    }

    private fun replyRequest() = TopicListParam().apply { searchPost = 1; authorId = 42 }

    private fun response(author: Int = 42, pid: Int = 50120, tid: Int = 100001) = """
        {"data":{"__ROWS":160,"__R__ROWS":1,"__T":{"tid":$tid,"authorid":17},
        "__R":{"0":{"tid":$tid,"pid":$pid,"lou":120,"authorid":$author,"content":"reply"}}}}
    """

    @Test fun replyToAnotherAuthorsTopicUsesReplyIdentityAndRemainsNativeReadable() {
        val info = searchResult()
        assertEquals(17, info.authorId)
        assertEquals("42", info.replyInfo.authorId)
        val param = ArticleNavigation.fromSearchResult(info, replyRequest())
        assertEquals(ArticleQuery(100001, 50120, 42, 1), ArticleQuery.from(param))
        assertEquals(3, param.page)
        assertEquals("topic & title", param.title)
        assertEquals(JSON.toJSONString(info), param.topicInfo)
        val parser = NormalArticleParser(ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
        val data = parser.parse(response(), ArticleQuery.from(param), param.page)
        assertEquals(50120, data.rowList.single().pid)
        assertEquals(120, data.rowList.single().lou)
        assertEquals(100001, data.pagingInfo.resolvedTid)
        assertEquals(7, ArticleNavigation.showAll(param, data)!!.page)

        // Correcting the input must not make wrong authors, replies or threads acceptable.
        for (raw in listOf(response(author = 17), response(pid = 50121), response(tid = 100002))) {
            try { parser.parse(raw, ArticleQuery.from(param), param.page); fail("Wrong identity accepted") }
            catch (failure: ArticleFailure) { assertEquals(ArticleFailureKind.CONTENT, failure.kind) }
        }
    }

    @Test fun ownTopicAndNonReplyNavigationPreserveTheirQueryKinds() {
        val info = searchResult(topicAuthor = 42)
        assertEquals(ArticleQuery(100001, 50120, 42, 1),
            ArticleQuery.from(ArticleNavigation.fromSearchResult(info, replyRequest())))
        val full = ArticleNavigation.fromSearchResult(info, TopicListParam())
        assertEquals(ArticleQuery(100001, 0, 0, 0), ArticleQuery.from(full))
        assertEquals(3, full.page)
    }

    @Test fun absentOrUnparseableReplyAuthorNeverBecomesTheTopicAuthor() {
        val info = searchResult()
        for (author in listOf(null, "", "#anony_synthetic", "2147483648")) {
            info.replyInfo.authorId = author
            assertEquals(ArticleQuery(100001, 50120, 0, 1),
                ArticleQuery.from(ArticleNavigation.fromSearchResult(info, replyRequest())))
        }
        info.replyInfo = null
        assertEquals(ArticleQuery(100001, 50120, 0, 1),
            ArticleQuery.from(ArticleNavigation.fromSearchResult(info, replyRequest())))
    }

    @Test fun searchClickUsesTheTestedNavigationBuilder() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.first { File(it, "nga_phone_base_3.0").isDirectory }
        val source = File(root, "nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java").readText()
        assertTrue(source.contains("ArticleListParam param = ArticleNavigation.fromSearchResult(info, requestParam);"))
        assertFalse(source.contains("param.authorId = info.getAuthorId()"))
        val converter = File(root, "nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java").readText()
        assertTrue(converter.contains("String authorId = tBean.getAuthorid();"))
        assertTrue(converter.contains("pageInfo.setAuthorId(Integer.parseInt(authorId));"))
        assertTrue(converter.contains("replyInfo.setAuthorId(pBean.getAuthorid());"))
    }
}
