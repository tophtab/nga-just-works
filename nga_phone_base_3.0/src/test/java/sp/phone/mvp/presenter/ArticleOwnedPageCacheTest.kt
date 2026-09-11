package sp.phone.mvp.presenter

import org.junit.Assert.*
import org.junit.Test
import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.mvp.model.thread.*
import sp.phone.param.ArticleListParam

class ArticleOwnedPageCacheTest {
    private fun param() = ArticleListParam().apply { tid = 100001; page = 7; readerGeneration = 9 }
    private fun data() = ThreadData().apply {
        rawData = "source"; rowNum = 1; rowList = listOf(ThreadRowInfo().apply { tid = 100001; pid = 50; content = "source" })
        threadInfo = ThreadPageInfo().apply { tid = 100001; subject = "topic" }
        pagingInfo = ArticlePagingInfo(ArticleQuery(100001, 0, 0, 0), ArticleSource.APP_API, 100001, 7, 7,
            10, 20, 200, ArticlePageBasis.REQUESTED, false, owner = "42", generation = 9)
    }

    @Test fun onlyTheActualCompleteSelectedGenerationGetsAnOwnedSnapshot() {
        val param = param()
        val data = data()
        val prepared = ArticlePageCache.prepare(param, data)!!
        assertEquals("42", prepared.cacheOwner)
        assertEquals("app_api-10", prepared.cacheLayoutId)
        assertEquals(7, prepared.page)
        assertNull(param.cacheOwner)
        for (paging in listOf(data.pagingInfo.copy(generation = 8), data.pagingInfo.copy(effectivePage = 13),
            data.pagingInfo.copy(query = ArticleQuery(100001, 0, 42, 0)), data.pagingInfo.copy(owner = null))) {
            val mismatch = data().apply { pagingInfo = paging }
            assertNull(ArticlePageCache.prepare(param, mismatch))
        }
        data.isContentComplete = false
        assertNull(ArticlePageCache.prepare(param, data))
        data.isContentComplete = true; data.rowList = emptyList()
        assertNull(ArticlePageCache.prepare(param, data))
    }

    @Test fun knownLayoutSnapshotsReuseTheirIdentityAndUnknownSizesAreIndependent() {
        val param = param()
        val data = data()
        assertEquals(ArticlePageCache.prepare(param, data)!!.cacheLayoutId, ArticlePageCache.prepare(param, data)!!.cacheLayoutId)
        data.pagingInfo = data.pagingInfo.copy(pageSize = null)
        val first = ArticlePageCache.prepare(param, data)!!
        val second = ArticlePageCache.prepare(param, data)!!
        assertNotEquals(first.cacheLayoutId, second.cacheLayoutId)
        assertTrue(first.cacheLayoutId.startsWith("app_api-window-"))
    }

    @Test fun damagedScopedNormalSourceCannotBeSavedAsACompleteOwnedPage() {
        val param = param()
        val raw = """{"data":{"__ROWS":140,"__R__ROWS":1,"__T":{"tid":100001,"subject":"topic"},"__R":{"0":{"tid":100001,"pid":50120,"lou":120,"content":{},"subject":"readable subject"}}}}"""
        val data = NormalArticleParser(ArticleRowRenderer { _, _ -> }, ArticleBlacklist { false })
            .parse(raw, ArticleQuery.from(param), param.page)
        data.pagingInfo = data.pagingInfo.withRequest("42", param.readerGeneration)
        assertEquals("readable subject", data.rowList.single().content)
        assertFalse(data.isContentComplete)
        assertNull(ArticlePageCache.prepare(param, data))
    }
}
