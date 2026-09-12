package sp.phone.ui.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the Android-facing prefetch wiring. This module has no Robolectric, so lifecycle and UI
 * boundaries are verified from source while the planner and request state run as pure JVM tests.
 */
class TopicPagePrefetchContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val tabFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java")
    private val listFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java")
    private val searchFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleSearchFragment.java")
    private val cacheActivitySource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java")
    private val shareViewModelSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java")
    private val presenterSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java")
    private val modelSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java")

    @Test
    fun pagerKeepsTwoOffscreenPagesAndReplansFromAcceptedPagingAndActualSelection() {
        assertTrue(tabFragmentSource.contains("mViewPager.setOffscreenPageLimit(2);"))
        assertTrue(tabFragmentSource.contains("state.paging.totalPages"))
        assertTrue(tabFragmentSource.contains("mCurrentPage = mPagerAdapter.getActualPage(position);"))
        assertTrue(tabFragmentSource.contains("state().canPrefetch()"))
        assertTrue(
            tabFragmentSource.contains(
                "ArticlePagePrefetchPlanner.plan(mCurrentPage, mTotalPages)",
            ),
        )
        assertTrue(tabFragmentSource.substringAfter("private void renderReaderState").substringBefore("private void publishPrefetchPages").contains("publishPrefetchPages();"))
        assertFalse(tabFragmentSource.contains("mReplyCount / 20"))
    }

    @Test
    fun candidatePublicationAlwaysCopiesIntoAnImmutableList() {
        assertTrue(shareViewModelSource.contains("LiveData<List<Integer>> getPrefetchPages()"))
        assertTrue(shareViewModelSource.contains("new ArrayList<>(prefetchPages)"))
        assertTrue(shareViewModelSource.contains("Collections.unmodifiableList(snapshot)"))
        assertTrue(shareViewModelSource.contains("mPrefetchPages.setValue"))
    }

    @Test
    fun retainedPageLoadingTipsUseThePageViewLifecycle() {
        assertTrue(listFragmentSource.contains("public LoadingLayout mLoadingView;"))
        val viewCreation = listFragmentSource
            .substringAfter("public void onViewCreated(View view, Bundle savedInstanceState)")
            .substringBefore("public void onDestroyView()")
        val viewBinding = viewCreation.indexOf("ButterKnife.bind(this, view)")
        val tipBinding = viewCreation.indexOf(
            "mLoadingView.bindToLifecycle(getViewLifecycleOwner())",
        )

        // Retained prefetch pages are attached too; the widget needs this owner's RESUMED state.
        assertTrue(viewBinding >= 0)
        assertTrue(tipBinding > viewBinding)
    }

    @Test
    fun onlyNormalOnlinePagerChildrenObserveCandidates() {
        assertTrue(listFragmentSource.contains("getParentFragment() instanceof ArticleTabFragment"))
        assertTrue(listFragmentSource.contains("!mRequestParam.loadCache"))
        assertTrue(listFragmentSource.contains("mRequestParam.searchPost == 0"))
        assertTrue(listFragmentSource.contains("mRequestParam.pid == 0 && mRequestParam.authorId == 0"))
        assertTrue(listFragmentSource.contains("viewModel.getPrefetchPages().observe(this, pages ->"))
        assertTrue(listFragmentSource.contains("pages.contains(mRequestParam.page)"))
        assertTrue(listFragmentSource.contains("mPresenter.prefetchPage();"))

        assertFalse(searchFragmentSource.contains("prefetchPage"))
        assertFalse(cacheActivitySource.contains("getPrefetchPages"))
        assertFalse(cacheActivitySource.contains("prefetchPage"))
    }

    @Test
    fun prefetchUsesTheExistingModelPathAndHasNoForegroundFailureSideEffects() {
        val prefetch = presenterSource.substringAfter("public void prefetchPage()").substringBefore("private class PrefetchCallback")
        assertTrue(prefetch.contains("mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), mHeaderMap, callback);"))
        assertTrue(prefetch.contains("mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), operation, callback);"))
        assertTrue(prefetch.contains("key.source != ArticleSource.READ_PHP"))
        assertFalse(prefetch.contains("startScoped("))
        assertFalse(presenterSource.contains("RetrofitService"))
        assertFalse(presenterSource.contains("ArticleConvertFactory"))

        val silentCallback = presenterSource
            .substringAfter("private class PrefetchCallback")
            .substringBefore("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)")
        assertFalse(silentCallback.contains("showToast"))
        assertFalse(silentCallback.contains("showWithWebView"))
        assertFalse(silentCallback.contains("retryWithNewAccount"))

        val silentFailure = presenterSource
            .substringAfter("private void handlePrefetchFailure()")
            .substringBefore("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)")
        assertTrue(silentFailure.contains("mPageRequestState.failPrefetch()"))
        assertTrue(silentFailure.contains("requestForegroundLoad(false);"))
        assertFalse(silentFailure.contains("showToast"))
        assertFalse(silentFailure.contains("showWithWebView"))
        assertFalse(silentFailure.contains("retryWithNewAccount"))

        assertTrue(presenterSource.contains("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)"))
        val backgroundTransition = presenterSource
            .substringAfter("@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)")
            .substringBefore("private void showWithWebView")
        assertTrue(
            backgroundTransition.contains(
                "boolean wasPromoted = mPageRequestState.movePrefetchToBackground();",
            ),
        )
        assertTrue(backgroundTransition.contains("mBaseView.setRefreshing(false);"))
    }

    @Test
    fun normalRefreshRetryAndWebViewFallbackRemainOnTheForegroundPath() {
        assertTrue(listFragmentSource.contains("mSwipeRefreshLayout.setOnRefreshListener"))
        assertTrue(listFragmentSource.contains("mPresenter.loadPage(mRequestParam);"))
        assertTrue(presenterSource.contains("requestForegroundLoad(true);"))
        assertTrue(presenterSource.contains("private class LegacyCallback implements OnHttpCallBack<ThreadData>"))
        assertTrue(presenterSource.contains("ArticleAttemptPolicy.retryCookie(manager.getUserSize(), originalCookie, manager::getNextCookie)"))
        assertTrue(presenterSource.contains("retryWithNewAccount()"))
        assertTrue(presenterSource.contains("showWithWebView();"))
    }

    @Test
    fun ordinaryAndScopedModelPathsBothCancelAtDetach() {
        assertTrue(
            modelSource.contains(
                "\"/read.php?\" + \"&page=\" + page + \"&__output=8&noprefix&v2\"",
            ),
        )
        assertTrue(modelSource.contains("mService.get(url, header)"))
        assertTrue(modelSource.contains("ArticleConvertFactory.getArticleInfo(s)"))
        val normal = modelSource.substringAfter("public void loadPage(ArticleListParam param, Map<String, String> header")
            .substringBefore("public void cachePage")
        val scoped = modelSource.substringAfter("public void loadScopedPage").substringBefore("public String getUrl")
        for (path in listOf(normal, scoped)) {
            assertEquals(2, Regex("bindUntilEvent\\(FragmentEvent\\.DETACH\\)").findAll(path).count())
        }
        assertTrue(scoped.contains("new ArticleByteClient().read(operation)"))
    }
}
