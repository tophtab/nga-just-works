package sp.phone.ui.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins the persistent direct-action FABs and selected-page long-press refresh wiring. The module
 * has no Robolectric, so the Android-facing behavior is verified from source and XML resources.
 */
class ArticlePageRefreshContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun file(relativePath: String) = File(projectRoot, relativePath)

    private fun source(relativePath: String) = file(relativePath).readText()

    private val boardLayout =
        source("nga_phone_base_3.0/src/main/res/layout/fragment_topic_list_board.xml")
    private val articleLayout =
        source("nga_phone_base_3.0/src/main/res/layout/fragment_article_tab.xml")
    private val articleListSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java")
    private val articleTabSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java")
    private val tabLayoutSource =
        source("lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/TabLayoutEx.java")
    private val topicListSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicListFragment.java")
    private val topicSearchSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java")
    private val longPressRepeaterSource =
        source("lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/LongPressRepeater.java")
    private val cacheActivitySource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java")
    private val dimensSource = source("nga_phone_base_3.0/src/main/res/values/dimens.xml")

    @Test
    fun boardAndArticleFabsStayVisibleAndKeepTheirDirectActions() {
        assertFalse(boardLayout.contains("ScrollAwareFabBehavior"))
        assertFalse(articleLayout.contains("ScrollAwareFabBehavior"))
        assertTrue(boardLayout.contains("android:contentDescription=\"@string/new_thread\""))
        assertTrue(articleLayout.contains("android:contentDescription=\"@string/reply_thread\""))
        assertTrue(topicListSource.contains("@OnClick(R.id.fab_post)"))
        assertTrue(topicListSource.contains("public void startPostActivity()"))
        assertTrue(articleTabSource.contains("@OnClick(R.id.fab_post)"))
        assertTrue(articleTabSource.contains("public void reply()"))
        assertTrue(cacheActivitySource.contains("findViewById(R.id.fab_post).setVisibility(View.GONE);"))
    }

    @Test
    fun onlyLiveArticlePagesReceiveReplyFabClearance() {
        assertTrue(
            dimensSource.contains(
                "<dimen name=\"article_list_reply_fab_clearance\">80dp</dimen>",
            ),
        )
        assertTrue(articleListSource.contains("applyReplyFabClearance();"))

        val clearanceMethod = articleListSource
            .substringAfter("private void applyReplyFabClearance()")
            .substringBefore("public void loadPage()")
        assertTrue(clearanceMethod.contains("if (mRequestParam.loadCache)"))
        assertTrue(clearanceMethod.contains("R.dimen.article_list_reply_fab_clearance"))
        assertTrue(clearanceMethod.contains("mListView.setPadding("))
        assertTrue(clearanceMethod.contains("mListView.setClipToPadding(false);"))
        assertFalse(boardLayout.contains("article_list_reply_fab_clearance"))
    }

    @Test
    fun articleOverflowDoesNotExposeRefresh() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file("nga_phone_base_3.0/src/main/res/menu/article_list_option_menu.xml"))
        val items = document.getElementsByTagName("item")
        val firstItem = items.item(0) as Element

        assertEquals("@+id/menu_goto_floor", firstItem.getAttribute("android:id"))
        assertTrue(
            (0 until items.length).none { index ->
                (items.item(index) as Element).getAttribute("android:id") == "@+id/item_refresh"
            },
        )
        assertFalse(articleTabSource.contains("case R.id.item_refresh:"))
    }

    @Test
    fun selectedPageLongPressRefreshesImmediatelyAndEveryFiveSeconds() {
        assertTrue(
            articleTabSource.contains(
                "private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;",
            ),
        )
        assertTrue(articleTabSource.contains("mTabLayout.setOnCurrentTabLongPressListener("))
        assertTrue(articleTabSource.contains("position -> refreshCurrentPage()"))
        assertTrue(articleTabSource.contains("CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS);"))

        val refreshMethod = articleTabSource
            .substringAfter("private void refreshCurrentPage()")
            .substringBefore("@Override\n    public void onResume()")
        assertTrue(refreshMethod.contains("mPagerAdapter.getCurrentFragment()"))
        assertTrue(refreshMethod.contains("!fragment.isRefreshing()"))
        assertTrue(refreshMethod.contains("fragment.loadPage();"))
        assertFalse(refreshMethod.contains("scrollCurrentPageToTop"))
        assertFalse(refreshMethod.contains("setCurrentItem"))
        assertFalse(refreshMethod.contains("reply()"))

        assertTrue(
            articleTabSource.contains(
                "mTabLayout.setOnTabReselectedListener(position -> scrollCurrentPageToTop());",
            ),
        )
        assertTrue(
            articleTabSource.contains(
                "mTabLayout.setOnCurrentTabLongPressListener(null, 0L);",
            ),
        )
    }

    @Test
    fun tabLongPressRepeatsOnlyWhileTheSelectedTabRemainsPressed() {
        // The scheduling itself now lives in LongPressRepeater; TabLayoutEx keeps only the
        // current-tab guard, the dispatch, and its teardown hooks.
        assertTrue(tabLayoutSource.contains("position == mViewPager.getCurrentItem()"))
        assertTrue(tabLayoutSource.contains("mOnCurrentTabLongPressListener != null"))
        assertTrue(
            tabLayoutSource.contains(
                "mOnCurrentTabLongPressListener.onCurrentTabLongPress(getChildAdapterPosition(tabView));",
            ),
        )
        assertTrue(tabLayoutSource.contains("repeater.setRepeatCondition(this::isCurrentTab);"))
        assertTrue(tabLayoutSource.contains("mCurrentTabLongPressRepeater.attach(holder.itemView);"))
        assertTrue(tabLayoutSource.contains("mCurrentTabLongPressRepeater.stop(holder.itemView);"))
        assertTrue(tabLayoutSource.contains("protected void onDetachedFromWindow()"))
        assertTrue(tabLayoutSource.contains("public void onViewRecycled(ViewHolder holder)"))

        // The public wiring ArticleTabFragment depends on must not drift.
        assertTrue(
            tabLayoutSource.contains(
                "public void setOnCurrentTabLongPressListener(\n" +
                    "            OnCurrentTabLongPressListener listener, long repeatIntervalMillis) {",
            ),
        )
        assertTrue(
            tabLayoutSource.contains(
                "throw new IllegalArgumentException(\"repeatIntervalMillis must be positive\");",
            ),
        )
    }

    @Test
    fun longPressRepeaterIsTheOnlyPressAndRepeatLoop() {
        assertTrue(longPressRepeaterSource.contains("view.setOnLongClickListener(this);"))
        assertTrue(longPressRepeaterSource.contains("!view.isAttachedToWindow()"))
        assertTrue(longPressRepeaterSource.contains("!view.isPressed()"))
        assertTrue(longPressRepeaterSource.contains("view.postDelayed(this, mRepeatIntervalMillis);"))
        assertTrue(longPressRepeaterSource.contains("mPressedView.removeCallbacks(mRepeatRunnable);"))

        // Rejecting the press must leave the event unconsumed so the caller's click path survives.
        val onLongClick = longPressRepeaterSource
            .substringAfter("public boolean onLongClick(View view)")
            .substringBefore("private boolean canRepeat(View view)")
        assertTrue(onLongClick.contains("if (!canRepeat(view)) {\n            return false;\n        }"))
        assertTrue(onLongClick.contains("return true;"))

        // TabLayoutEx must not keep a second copy of the loop.
        assertFalse(tabLayoutSource.contains("mRepeatCurrentTabLongPressRunnable"))
        assertFalse(tabLayoutSource.contains("mLongPressedTabView"))
        assertFalse(tabLayoutSource.contains("postDelayed("))
        assertTrue(tabLayoutSource.contains("new LongPressRepeater("))
    }

    @Test
    fun postFabLongPressRefreshesTheCurrentPageAndRepeatsWhileHeld() {
        // Board: scroll back to the top and reload page one, every cycle.
        assertTrue(
            topicListSource.contains(
                "private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;",
            ),
        )
        assertTrue(
            topicListSource.contains(
                "mFabRefreshRepeater = new LongPressRepeater(\n" +
                    "                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS, v -> scrollToTopAndRefresh());",
            ),
        )
        assertTrue(topicListSource.contains("mFabRefreshRepeater.attach(mFab);"))
        assertTrue(topicListSource.contains("mFabRefreshRepeater.detach(mFab);"))
        assertTrue(topicListSource.contains("public void onDestroyView()"))

        // The board action stays the one the toolbar title tap already used.
        assertTrue(topicSearchSource.contains("protected void onTitleClick() {\n        scrollToTopAndRefresh();\n    }"))
        val scrollToTopAndRefresh = topicSearchSource
            .substringAfter("protected void scrollToTopAndRefresh()")
            .substringBefore("public void scrollTo(int position)")
        assertTrue(scrollToTopAndRefresh.contains("scrollTo(0);"))
        assertTrue(scrollToTopAndRefresh.contains("mPresenter.loadPage(1, mRequestParam);"))
        assertTrue(scrollToTopAndRefresh.contains("mSwipeRefreshLayout.isEnabled() && !isRefreshing()"))

        // Article: refresh only; no scroll, no page change, no composition.
        assertTrue(
            articleTabSource.contains(
                "mFabRefreshRepeater = new LongPressRepeater(\n" +
                    "                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS, v -> refreshCurrentPage());",
            ),
        )
        assertTrue(articleTabSource.contains("mFabRefreshRepeater.attach(mFab);"))
        assertTrue(articleTabSource.contains("mFabRefreshRepeater.detach(mFab);"))

        // No cross-screen refresh abstraction was introduced.
        assertFalse(topicListSource.contains("refreshCurrentPage"))
        assertFalse(articleTabSource.contains("scrollToTopAndRefresh"))
    }
}
