package sp.phone.json

import com.alibaba.fastjson2.JSON
import gov.anzong.androidnga.activity.compose.filter.FilterKeyword
import org.junit.Assert.*
import org.junit.Test
import sp.phone.common.User
import sp.phone.http.bean.Attachment
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.entity.Board
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.mvp.model.thread.ArticleCacheEntry

/** Frozen synthetic fastjson1 output; keys/values matter, object key order does not. */
class LegacyStorageGoldenTest {
    private fun fixture(name: String) = javaClass.getResource("/json-legacy/$name.json")!!.readText()
    private fun golden(name: String, value: Any) {
        val encoded = JSON.toJSONString(value)
        assertEquals(name, JSON.parse(fixture(name)), JSON.parse(encoded)) // old write → new read/write
        assertEquals(name, com.alibaba.fastjson.JSON.parse(fixture(name)),
            com.alibaba.fastjson.JSON.parse(encoded)) // new write → old parser
        if (value is List<*> && value.isNotEmpty() && value[0] != null) {
            val type = value[0]!!.javaClass
            val oldRead = com.alibaba.fastjson.JSON.parseArray(encoded, type)
            if (type == Board::class.java) {
                // Read-only BoardKey is not restored by the original reader; migration uses these fields.
                val expected = value as List<Board>
                val actual = oldRead as List<Board>
                assertEquals(expected.map { listOf(it.fid, it.stid, it.name, it.boardHead) },
                    actual.map { listOf(it.fid, it.stid, it.name, it.boardHead) })
            } else {
                assertEquals(name, JSON.parse(encoded), JSON.parse(JSON.toJSONString(oldRead)))
            }
            val newRead = JSON.parseArray(encoded, type)
            if (type != Board::class.java) {
                assertEquals(name, JSON.parse(encoded), JSON.parse(JSON.toJSONString(newRead)))
            }
        } else if (value is Attachment) {
            assertEquals(JSON.parse(encoded), JSON.parse(JSON.toJSONString(
                com.alibaba.fastjson.JSON.parseObject(encoded, Attachment::class.java))))
            assertEquals(JSON.parse(encoded), JSON.parse(JSON.toJSONString(
                JSON.parseObject(encoded, Attachment::class.java))))
        }
    }

    @Test fun topicHistoryAndNavigationPreserveEveryPersistedProperty() {
        val topic = ThreadPageInfo().apply {
            tid = 120002; fid = -7; authorId = 42; author = "合成作者"
            lastPoster = "最后一位"; replies = 83; subject = "标题\n\"引号\"\\路径"
            titleFont = "b red"; type = 16; topicMisc = "0,1"; page = 7
            pid = 50012; position = 3; isAnonymity = true; postDate = 1700000000
            board = "版面镜像"
            replyInfo = ThreadPageInfo.ReplyInfo().apply {
                pidStr = "50012"; tidStr = "120002"; authorId = "42"
                content = "合成回复\n\\"; subject = "回复题"; postDate = "1700000001"
            }
            cacheEntry = ArticleCacheEntry(120002, "42", "read_php-20")
        }
        golden("topic-history", listOf(topic, ThreadPageInfo().apply { tid = 120003 }))
        val restored = JSON.parseArray(fixture("topic-history"), ThreadPageInfo::class.java)
        assertEquals(42, restored[0].authorId)
        assertEquals("50012", restored[0].replyInfo.pidStr)
        assertTrue(restored[0].isMirrorBoard)
        assertNull(restored[1].subject)
        golden("topic-history", restored)
        val injected = JSON.parseObject("""{"tid":120002,"cacheEntry":{"tid":1,"owner":"43","layoutId":"app_api-10"},"cacheSummary":"injected"}""", ThreadPageInfo::class.java)
        assertNull(injected.cacheEntry)
        assertFalse(JSON.parseObject(JSON.toJSONString(injected)).containsKey("cacheSummary"))
    }

    @Test fun wireAliasesAndEveryAttachmentFieldRemainReadable() {
        val topic = JSON.parseObject("""{"authorid":42,"lastposter":"last","titlefont":"b","topic_misc":"misc","postdate":1700000000}""", ThreadPageInfo::class.java)
        assertEquals(42, topic.authorId); assertEquals("last", topic.lastPoster)
        assertEquals("b", topic.titleFont); assertEquals("misc", topic.topicMisc)
        assertEquals(1700000000, topic.postDate)
        val row = JSON.parseObject("""{"from_client":"103 synthetic","from_client_model":"model","authorid":42,"attachs":{"first":${fixture("attachment")}}}""", ThreadRowInfo::class.java)
        assertEquals("103 synthetic", row.fromClient); assertEquals("model", row.fromClientModel)
        assertEquals(42, row.authorid)
        golden("attachment", row.attachs["first"]!!)
        val attachment = JSON.parseObject(fixture("attachment"), Attachment::class.java)
        assertEquals(12345, attachment.size); assertEquals(7, attachment.subid)
        assertEquals("原名.mp4", attachment.url_utf8_org_name)
    }

    @Test fun filterKeywordsExcludeCompiledPatternAndKeepNullsAndEnabled() {
        val keyword = FilterKeyword("合成.*").apply { isEnabled = true }
        assertTrue(keyword.match("合成内容"))
        golden("filter-keywords", listOf(keyword, FilterKeyword()))
        val restored = JSON.parseArray(fixture("filter-keywords"), FilterKeyword::class.java)
        assertTrue(restored[0].isEnabled); assertTrue(restored[0].match("合成内容"))
        assertNull(restored[1].keyword); assertFalse(restored[1].isEnabled)
        golden("filter-keywords", restored)
    }

    @Test fun filterUsersPreserveLegacyPublicFieldsAndBeanNamesWithoutCredentials() {
        val users = listOf(User("42", "合成\"名字"), User("43", null))
        users[0].avatarUrl = "https://example.invalid/avatar.png"
        golden("filter-users", users)
        val restored = JSON.parseArray(fixture("filter-users"), User::class.java)
        assertEquals(listOf("42", "43"), restored.map { it.userId })
        assertEquals("合成\"名字", restored[0].nickName); assertNull(restored[1].nickName)
        assertTrue(restored.all { it.cid == null })
        golden("filter-users", restored)
    }

    @Test fun oldBookmarkPreferenceRetainsBoardFields() {
        val boards = listOf(Board(-7, 0, "普通").apply { boardHead = "头" }, Board(0, 12345678, "合集"))
        golden("legacy-boards", boards)
        val restored = JSON.parseArray(fixture("legacy-boards"), Board::class.java)
        assertEquals(-7, restored[0].fid); assertEquals("头", restored[0].boardHead)
        assertEquals(12345678, restored[1].stid); assertEquals("合集", restored[1].name)
        // Migration consumes name/fid/stid/head; BoardKey has no public JSON fields.
    }

    @Test fun canonicalBoardHierarchyAndBookmarkWriterMatchOldFields() {
        val input = fixture("board-tree")
        val roots = JSON.parseArray(input, gov.anzong.androidnga.core.board.data.BoardEntity::class.java)
        val child = roots[0].children!![0]
        child.parentId = "derived-parent"
        child.iconUrl = "https://example.invalid/derived.png"
        golden("board-tree", roots)
        val encoded = gov.anzong.androidnga.activity.compose.board.ForumBoardRepository.encodeBookmarkBoards(roots)
        assertEquals(JSON.parse(input), JSON.parse(encoded))
        val oldRead = com.alibaba.fastjson.JSON.parseArray(encoded, gov.anzong.androidnga.core.board.data.BoardEntity::class.java)
        assertEquals(JSON.parse(input), JSON.parse(JSON.toJSONString(oldRead)))
        val restored = gov.anzong.androidnga.activity.compose.board.ForumBoardRepository.decodeBookmarkBoards(encoded)
        assertEquals("重复", restored[0].children!![1].name)
        assertEquals(-7, restored[0].children!![0].fid)
        assertEquals(12345678, restored[1].stid)
        assertEquals("", restored[0].children!![0].iconUrl)
    }

    @Test fun searchAndImplicitEmoticonPreferenceArraysKeepOrderEscapesAndNull() {
        val values = listOf("第三.png", "quote\".png", "back\\slash.png", "第三.png", null)
        golden("string-list", values)
        assertEquals(values, JSON.parseArray(fixture("string-list"), String::class.java))
        assertNull(JSON.parseArray("null", String::class.java))
        assertNull(JSON.parseArray("", String::class.java))
        assertTrue(JSON.parseArray("[]", String::class.java).isEmpty())
    }
}
