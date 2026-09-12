package sp.phone.ui.fragment

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** JVM source boundaries supplement the executable query/session/cache tests without a device. */
class ArticleReaderUiContractTest {
    private fun source(path: String) = File("src/main/java/$path.java").readText()
    private val presenter = source("sp/phone/mvp/presenter/ArticleListPresenter")
    private val fragment = source("sp/phone/ui/fragment/ArticleListFragment")

    @Test fun generationHandoffReturnsBeforeAnOldCallbackCanClearNewReadyOrLoading() {
        val scoped = presenter.substringAfter("private void finishScoped").substringBefore("private class LegacyCallback")
        val legacy = presenter.substringAfter("private class LegacyCallback").substringBefore("@Override public void onError(String message)")
        for (callback in listOf(scoped, legacy)) {
            val adoption = callback.indexOf("viewModel().adoptPage")
            val handoffGuard = callback.indexOf("if (adoption == 2 || sequence != mSequence")
            val completion = callback.indexOf("showData(data)")
            assertTrue(adoption >= 0 && handoffGuard > adoption && completion > handoffGuard)
            assertTrue(callback.substring(handoffGuard, completion).contains("return;"))
        }
        assertTrue(presenter.contains("if (viewModel().ensureEnvironment("))
        assertTrue(presenter.contains("reader().takeHandoff(key.generation, key.page)"))
    }

    @Test fun onlyTheReadyDecisionMayDisplayRetainedDataAndFinishLoading() {
        val loading = presenter.substringAfter("ArticlePageRequestState.ForegroundLoadDecision decision =")
            .substringBefore("private ArticleAnchor transitionAnchor")
        assertTrue(loading.contains("ForegroundLoadDecision.SHOW_READY_DATA && mThreadData != null"))
        assertFalse(loading.contains("ForegroundLoadDecision.NONE && mThreadData != null"))
        val waiting = loading.substringAfter("ForegroundLoadDecision.WAIT_FOR_PREFETCH")
            .substringBefore("else if (decision == ArticlePageRequestState.ForegroundLoadDecision.START)")
        assertTrue(waiting.contains("ForegroundLoadDecision.NONE"))
        assertTrue(waiting.contains("mBaseView.setRefreshing(true)"))
        assertFalse(waiting.contains("showData("))
    }

    @Test fun localFloorLookupAndDelayedScrollUseActualRowsAndDisplayedDataIdentity() {
        val tab = source("sp/phone/ui/fragment/ArticleTabFragment")
        assertTrue(tab.contains("current.containsFloor(floor)"))
        assertTrue(tab.contains("current.maxKnownFloor()"))
        assertTrue(tab.contains("state.paging.candidatePage(floor)"))
        assertFalse(tab.contains("floor %")); assertFalse(tab.contains("floor / 20"))
        val anchor = fragment.substringAfter("private void consumePendingAnchor()").substringBefore("@Override public void onResume()")
        assertTrue(anchor.contains("anchor.find(mDisplayedData.getRowList())"))
        assertTrue(anchor.contains("isResumed() && mDisplayedData == displayed"))
        assertTrue(anchor.contains("anchor.find(displayed.getRowList()) == index"))
    }

    @Test fun rowBindingResetsUnknownScoreIdentityAndVariableWebViewRetention() {
        val adapter = source("sp/phone/ui/adapter/ArticleListAdapter")
        assertFalse(adapter.contains("new LocalWebView[20]"))
        assertTrue(adapter.contains("data.getRowList().size()"))
        assertTrue(adapter.contains("holder.scoreTv.setVisibility(scoreKnown ? View.VISIBLE : View.GONE)"))
        assertTrue(adapter.contains("holder.nickNameTV.setEnabled(userKnown)"))
        assertTrue(adapter.contains("holder.avatarPanel.setEnabled(userKnown)"))
        assertTrue(fragment.contains("mArticleAdapter.releaseWebViews()"))
        assertTrue(adapter.contains("ArticleQuote.authorMarkup(row)"))
        assertTrue(adapter.contains("ArticleQuote.mention(row)"))
        assertTrue(adapter.contains("holder.replyBtn.setVisibility(ArticleRowPresentation.canReply(row) ? View.VISIBLE : View.GONE)"))
        assertTrue(adapter.contains("if (!ArticleRowPresentation.canReply(row)) return;"))
        assertEquals(2, Regex("ArticleQuote.authorMarkup\\(row\\)").findAll(presenter).count())
        assertTrue(presenter.contains("ArticleQuote.mention(row)"))
    }

    @Test fun nestedCommentsUseDisplayProjectionWithoutChangingEditableSource() {
        val converter = source("sp/phone/mvp/model/convert/ArticleConvertFactory")
        val comments = converter.substringAfter("if (row.getComments() != null) {")
            .substringBefore("return htmlData;")
        assertTrue(comments.contains("ArticleSourceText.renderBody(value)"))
        assertTrue(comments.contains("comment.setContent(body == null || body.isEmpty() ? value.getAlterinfo() : body)"))
        assertFalse(comments.contains("value.setContent("))
    }

    @Test fun cacheOpenUsesValidatedHandleAfterSuperCreateAndListsActualPagesOffThread() {
        val cache = source("gov/anzong/androidnga/activity/ArticleCacheActivity")
        assertTrue(cache.indexOf("super.onCreate(savedInstanceState)") < cache.indexOf("if (mRequestParam == null"))
        assertTrue(cache.contains("ArticleCacheEntry.from(mRequestParam)"))
        assertTrue(cache.contains(".pages(mEntry, ArticleAccounts.currentOwner())"))
        assertTrue(cache.contains(".subscribeOn(Schedulers.io())"))
        assertTrue(cache.contains("count <= 5 ? count : 0"))
        assertTrue(cache.contains("adapter.positionOfPage(mRequestParam.page)"))
        assertFalse(cache.contains("cacheFile.getName().contains(tid)"))
    }
}
