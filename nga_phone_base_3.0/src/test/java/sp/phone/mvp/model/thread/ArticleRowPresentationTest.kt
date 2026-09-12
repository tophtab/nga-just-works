package sp.phone.mvp.model.thread

import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.ThreadRowInfo

class ArticleRowPresentationTest {
    private fun row(kind: ArticleRowKind) = ThreadRowInfo().apply {
        tid = 100001; pid = 50173; lou = 173; content = "editable source"
        presentation = ArticleRowPresentation(kind, true, false, false, true, null)
    }

    @Test fun readableCommentsAndUnknownKindsCanReplyUsingTheirOwnPidWithoutAnAuthorIdentity() {
        for (kind in ArticleRowKind.entries) {
            val row = row(kind)
            assertTrue(ArticleRowPresentation.canReply(row))
            assertFalse(ArticleRowPresentation.hasUser(row))
            if (kind == ArticleRowKind.COMMENT) assertEquals("50173", ArticleNavigation.quoteAddress(row))
            row.presentation = row.presentation.copy(sourceAvailable = false)
            assertFalse(ArticleRowPresentation.canReply(row))
        }
    }

    @Test fun replyRequiresUsableSourceAndAnActualReplyOrRecognizedTopicRoot() {
        val row = row(ArticleRowKind.POST)
        row.pid = 0
        assertFalse(ArticleRowPresentation.canReply(row))
        row.lou = 0
        assertTrue(ArticleRowPresentation.canReply(row))
        row.presentation = row.presentation.copy(kind = ArticleRowKind.COMMENT)
        assertFalse(ArticleRowPresentation.canReply(row))
        row.pid = 50173; row.tid = 0
        assertFalse(ArticleRowPresentation.canReply(row))
        row.tid = 100001; row.content = null
        assertFalse(ArticleRowPresentation.canReply(row))
    }

    @Test fun unavailableDisplayInputPreservesTheEditableSourceAndDoesNotInventAHeader() {
        val row = row(ArticleRowKind.COMMENT)
        val source = "<b>Reply to [pid=1,2,3]Reply[/pid] metadata</b>remaining $ \\ source"
        row.content = source
        assertEquals(ArticleSourceText.normalizeReplyHeader(source), ArticleSourceText.renderBody(row))
        assertEquals(source, row.content)
        row.presentation = row.presentation.copy(sourceAvailable = false)
        assertTrue(ArticleSourceText.renderBody(row)!!.startsWith("此条内容暂无法完整显示"))
        assertEquals(source, row.content)
        row.content = null
        val notice = ArticleSourceText.renderBody(row)!!
        assertTrue(notice.contains("此条内容暂无法完整显示"))
        assertFalse(notice.contains("[pid="))
        assertNull(row.content)
    }
}
