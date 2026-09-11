package sp.phone.mvp.model.thread

import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.param.ArticleListParam

class ArticleReaderSessionTest {
    private val full = ArticleQuery(100001, 0, 0, 0)
    private fun row(floor: Int, pid: Int = 50000 + floor) = ThreadRowInfo().apply {
        tid = full.tid; this.pid = pid; lou = floor; content = "source"; authorid = 42; author = "author"
        presentation = ArticleRowPresentation(ArticleRowKind.POST, true, true, false, true, true)
    }
    private fun page(source: ArticleSource, size: Int?, actual: Int, floor: Int, total: Int? = 40) = ThreadData().apply {
        rowList = listOf(row(floor)); rowNum = 1; rawData = "source"
        threadInfo = ThreadPageInfo().apply { tid = full.tid; subject = "topic" }
        pagingInfo = ArticlePagingInfo(full, source, full.tid, actual, actual, size, total, 400,
            ArticlePageBasis.REQUESTED, ArticlePagingInfo.floorWindow(full, actual, size, rowList))
    }
    private fun session() = ArticleReaderSession(full, 7).apply { ensureEnvironment("first", true, "42", "https://bbs.nga.cn", 7) }

    @Test fun sourceAndLayoutHandoffRetiresEveryOldKeyAndIsConsumedOnce() {
        val session = session()
        val old = session.key(7)
        val anchor = ArticleAnchor(old.generation, 7, 50120, 120)
        session.setAnchor(anchor)
        val firstApp = page(ArticleSource.APP_API, 10, 7, 60)
        assertEquals(13, ArticleNavigation.alignmentPage(anchor, firstApp))
        assertTrue(ArticleNavigation.handoffNotice(firstApp, anchor)!!.contains("阅读位置未能保留"))
        val aligned = page(ArticleSource.APP_API, 10, 13, 120)
        assertEquals("已使用兼容模式显示", ArticleNavigation.handoffNotice(aligned, anchor))
        assertTrue(ArticleNavigation.handoffNotice(aligned, null)!!.contains("阅读位置未能保留"))
        assertEquals(2, session.adopt(old, aligned, true))
        assertEquals(ArticleSource.APP_API, session.state().source)
        assertEquals(13, session.state().currentPage)
        assertFalse(session.accepts(old))
        assertFalse(session.state().canPrefetch())
        assertNull(session.takeHandoff(session.state().generation, 7))
        assertSame(aligned, session.takeHandoff(session.state().generation, 13))
        assertNull(session.takeHandoff(session.state().generation, 13))
        val current = session.key(14)
        assertEquals(ArticleSource.APP_API, current.source)
        assertEquals(10, current.pageSize)
        assertEquals(1, session.adopt(current, page(ArticleSource.APP_API, 10, 14, 130), true))
        assertEquals(0, session.adopt(old, page(ArticleSource.READ_PHP, 20, 7, 120), true))
    }

    @Test fun normalMetadataTransitionAndBackgroundResultsCannotReplaceCurrentSource() {
        val session = session()
        val old = session.key(7)
        assertEquals(0, session.adopt(old, page(ArticleSource.APP_API, 10, 7, 60), false))
        assertEquals(ArticleSource.READ_PHP, session.state().source)
        val normal = page(ArticleSource.READ_PHP, 20, 7, 120)
        assertEquals(2, session.adopt(old, normal, true))
        assertTrue(session.state().canPrefetch())
        assertSame(normal, session.takeHandoff(session.state().generation, 7))
        assertEquals(1, session.adopt(session.key(8), page(ArticleSource.READ_PHP, 20, 8, 140), false))
        assertEquals(7, session.state().currentPage)
        assertEquals(0, session.adopt(session.key(8), page(ArticleSource.READ_PHP, 20, 9, 160), false))
    }

    @Test fun changedAccountOrToggleResetsSourceAndRejectsInflightPages() {
        val session = session()
        session.adopt(session.key(7), page(ArticleSource.APP_API, 10, 7, 60), true)
        val app = session.key(7)
        assertFalse(session.ensureEnvironment("first", true, "42", "https://bbs.nga.cn", 7))
        assertTrue(session.ensureEnvironment("second", true, "43", "https://bbs.nga.cn", 7))
        assertFalse(session.accepts(app))
        assertEquals("43", session.state().owner)
        assertEquals(ArticleSource.READ_PHP, session.state().source)
        assertNull(session.state().pendingAnchor)
        session.adopt(session.key(7), page(ArticleSource.APP_API, 10, 7, 60), true)
        val second = session.key(7)
        assertTrue(session.ensureEnvironment("second", false, null, "https://bbs.nga.cn", 7))
        assertFalse(session.accepts(second))
        assertNull(session.state().owner)
        assertEquals(20, session.state().pageSize)
        assertNull(session.takeHandoff(second.generation, 7))
    }

    @Test fun pendingAnchorsWaitForTheirPageAndMatchRealRowsWithoutModulo() {
        val session = session()
        val anchor = ArticleAnchor(session.state().generation, 10, 50173, 173)
        session.setAnchor(anchor)
        assertNull(session.consumeAnchor(anchor.generation, 7))
        assertNull(session.consumeAnchor(anchor.generation + 1, 10))
        assertEquals(anchor, session.consumeAnchor(anchor.generation, 10))
        assertNull(session.consumeAnchor(anchor.generation, 10))
        val rows = listOf(row(3), row(85), row(173))
        assertEquals(2, anchor.find(rows))
        assertEquals(-1, anchor.copy(pid = 999).find(rows))
        assertEquals(1, anchor.copy(pid = null, floor = 85).find(rows))
        assertEquals(-1, anchor.copy(pid = null, floor = 86).find(rows))
        rows[1].presentation = rows[1].presentation.copy(kind = ArticleRowKind.COMMENT)
        assertEquals(-1, anchor.copy(pid = null, floor = 85).find(rows))
        assertNull(ArticleNavigation.alignmentPage(anchor, page(ArticleSource.APP_API, null, 7, 60)))
    }

    @Test fun fallbackAndAlignmentBudgetsAreIndependentBoundedAndForegroundOnly() {
        for (kind in ArticleFailureKind.entries) {
            val policy = ArticleAttemptPolicy()
            assertEquals(kind == ArticleFailureKind.FORMAT, policy.tryApp(ArticleFailure(kind), true, true, true))
        }
        val policy = ArticleAttemptPolicy()
        val format = ArticleFailure(ArticleFailureKind.FORMAT)
        assertFalse(policy.tryApp(format, false, true, true))
        assertFalse(policy.tryApp(format, true, false, true))
        assertFalse(policy.tryApp(format, true, true, false))
        assertTrue(policy.tryApp(format, true, true, true))
        assertFalse(policy.tryApp(format, true, true, true))
        assertFalse(policy.tryAlignment(13, false, true))
        assertFalse(policy.tryAlignment(null, true, true))
        assertTrue(policy.tryAlignment(13, true, true))
        assertFalse(policy.tryAlignment(25, true, true))
    }

    @Test fun legacyAccountRetryCountsBeforeAdvancingAndRejectsEmptyOrRepeatedCookie() {
        var advances = 0
        for (count in listOf(0, 1)) assertNull(ArticleAttemptPolicy.retryCookie(count, "first") { advances++; "second" })
        assertEquals(0, advances)
        for (next in listOf(null, "", " ", "first")) assertNull(ArticleAttemptPolicy.retryCookie(2, "first") { next })
        assertEquals("second", ArticleAttemptPolicy.retryCookie(2, "first") { advances++; "second" })
        assertEquals(1, advances)
    }

    @Test fun showAllUsesResolvedIdentityClearsFiltersAndQuoteHintKeepsOrdinaryConvention() {
        val lookup = ArticleListParam().apply { tid = 0; pid = 50173; authorId = 42; searchPost = 1; page = 7
            loadCache = true; cacheOwner = "42"; cacheLayoutId = "app_api-10"; readerGeneration = 17 }
        val fullParam = ArticleNavigation.showAll(lookup, page(ArticleSource.APP_API, 10, 7, 173))!!
        assertEquals(full.tid, fullParam.tid)
        assertEquals(0, fullParam.pid); assertEquals(0, fullParam.authorId); assertEquals(0, fullParam.searchPost)
        assertEquals(1, fullParam.page); assertFalse(fullParam.loadCache); assertNull(fullParam.cacheOwner)
        assertEquals(0L, fullParam.readerGeneration)
        assertNull(ArticleNavigation.showAll(lookup, null))
        assertEquals("50173,100001,9", ArticleNavigation.quoteAddress(row(173)))
        val unknown = row(-1)
        unknown.presentation = unknown.presentation.copy(floorKnown = false, userKnown = false, threadAuthor = null)
        unknown.authorid = 0; unknown.author = "未知用户"
        assertEquals(unknown.pid.toString(), ArticleNavigation.quoteAddress(unknown))
        assertEquals("未知用户", ArticleQuote.authorMarkup(unknown)); assertNull(ArticleQuote.mention(unknown))
        assertFalse(ArticleRowPresentation.isThreadAuthor(unknown, "未知用户"))
        unknown.isanonymous = true; unknown.author = "匿名名称"
        assertEquals("[uid=-1]匿名名称[/uid]", ArticleQuote.authorMarkup(unknown))
        assertEquals("匿名名称", ArticleQuote.mention(unknown))
        assertTrue(ArticleRowPresentation.isThreadAuthor(row(173), "different name"))
        val nameFallback = row(173).apply { presentation = presentation.copy(threadAuthor = null) }
        assertTrue(ArticleRowPresentation.isThreadAuthor(nameFallback, "author"))
        nameFallback.author = "未知用户"
        assertFalse(ArticleRowPresentation.isThreadAuthor(nameFallback, "未知用户"))
    }
}
