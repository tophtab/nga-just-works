package sp.phone.ui.fragment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Android view wiring alongside executing repository/page-delivery tests; no runtime stubs. */
class ArticleAuthorLocationContractTest {

    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(path: String) = File(root, "nga_phone_base_3.0/src/main/$path").readText()

    @Test
    fun successfulPageDeliveryEnrichesIndependentlyAndRetainedViewRebindingIsCacheOnly() {
        val fragment = source("java/sp/phone/ui/fragment/ArticleListFragment.java")
        val delivery = fragment.substringAfter("public void setData(ThreadData data)")
            .substringBefore("private void renderData")
        assertTrue(delivery.contains("getView() == null"))
        assertTrue(delivery.contains("mAuthorLocations == null"))
        assertTrue(delivery.contains("mAuthorLocations.deliver(data, !mRequestParam.loadCache)"))
        assertTrue(delivery.indexOf("mDeliveredData = data") < delivery.indexOf("getView() == null"))
        assertTrue(delivery.indexOf("getView() == null") < delivery.indexOf("renderData(data)"))
        assertTrue(delivery.indexOf("renderData(data)") < delivery.indexOf("mAuthorLocations.deliver(data,"))
        assertFalse(delivery.contains("RESUMED"))
        assertFalse(delivery.contains("getCurrentFragment"))
        assertFalse(delivery.contains("getPrefetchPages"))
        assertTrue(fragment.contains("AuthorLocationService.bind(getContext(), getViewLifecycleOwner()"))
        val rebind = fragment.substringAfter("public void onViewCreated(View view, Bundle savedInstanceState)")
            .substringBefore("public void onDestroyView()")
        assertTrue(rebind.contains("renderData(mDeliveredData)"))
        assertTrue(rebind.contains("mAuthorLocations.deliver(mDeliveredData, false)"))
        assertTrue(rebind.indexOf("renderData(mDeliveredData)") <
            rebind.indexOf("mAuthorLocations.deliver(mDeliveredData, false)"))
        val destroy = fragment.substringAfter("public void onDestroyView()")
            .substringBefore("private void applyReplyFabClearance")
        assertTrue(destroy.contains("mAuthorLocations.close()"))
        assertTrue(destroy.contains("mAuthorLocations = null"))
        assertTrue(destroy.contains("mDisplayedData = null"))
        assertFalse(destroy.contains("mDeliveredData = null"))
    }

    @Test
    fun readyResumeReplaysRestoreCurrentMetadataWithoutReplacingThePageSubscription() {
        val presenter = source("java/sp/phone/mvp/presenter/ArticleListPresenter.java")
        val resume = presenter.substringAfter("@Override protected void onResume()")
            .substringBefore("@Override protected void onDestroy()")
        assertTrue(resume.contains("requestForegroundLoad(false)"))
        val ready = presenter.substringAfter("SHOW_READY_DATA && mThreadData != null) {")
            .substringBefore("if (decision ==")
        assertTrue(ready.contains("showData(mThreadData)"))
        val show = presenter.substringAfter("private void showData(ThreadData data)")
            .substringBefore("private void finishError")
        assertTrue(show.contains("mBaseView.setData(data)"))

        val fragment = source("java/sp/phone/ui/fragment/ArticleListFragment.java")
        val delivery = fragment.substringAfter("public void setData(ThreadData data)")
            .substringBefore("private void renderData")
        assertTrue(delivery.contains("mAuthorLocations.deliver(data, !mRequestParam.loadCache)"))

        val service = source("java/sp/phone/profile/AuthorLocationService.java")
        val pageDelivery = service.substringAfter("public void deliver(ThreadData data, boolean online)")
            .substringBefore("@Override")
        // The executable AuthorLocationPageTest now owns replay/handoff behavior; this pins
        // that Android actually calls that production controller, without a second algorithm.
        assertTrue(pageDelivery.contains("controller.deliver(data, online)"))
        assertFalse(pageDelivery.contains("repository.subscribe"))
        assertTrue(service.contains("new AuthorLocationPage(service.repository, service::whenSessionSettled,"))
        assertTrue(service.contains("() -> service.sessionSignal, updates::setValue"))
    }

    @Test
    fun viewLifecycleClosesTheTestedControllerAndReplaysCurrentOutputAfterSessionCheck() {
        val service = source("java/sp/phone/profile/AuthorLocationService.java")
        val page = service.substringAfter("public static final class Page")
        val start = page.substringAfter("public void onStart(@NonNull LifecycleOwner owner)")
            .substringBefore("public void onDestroy")
        assertTrue(start.indexOf("service.repository.synchronizeSession()") >= 0)
        assertTrue(start.indexOf("service.repository.synchronizeSession()") < start.indexOf("controller.replay()"))
        assertTrue(page.substringAfter("public void onDestroy").substringBefore("public void close()")
            .contains("close()"))
        assertTrue(page.substringAfter("public void close()").contains("controller.close()"))
        assertTrue(service.contains("page.controller.isCurrent(delivery)"))
        val controller = source("java/sp/phone/profile/AuthorLocationPage.java")
        assertTrue(controller.contains("private final WeakReference<AuthorLocationPage> owner"))
        assertFalse(controller.contains("android."))
    }

    @Test
    fun normalAndPrefetchedCompletionsDeliverToOffscreenPages() {
        val presenter = source("java/sp/phone/mvp/presenter/ArticleListPresenter.java")
        val normal = presenter.substringAfter("private void showData(ThreadData data)")
            .substringBefore("private void finishError")
        val prefetch = presenter.substringAfter("private class PrefetchCallback")
            .substringBefore("@Override public void onError")
        for (completion in listOf(normal, prefetch)) {
            assertTrue(completion.contains("mBaseView.setData(data)"))
            val beforeDelivery = completion.substringBefore("mBaseView.setData(data)")
            // Foreground still controls source adoption and failure UI, not accepted deliveries.
            assertFalse(Regex("if\\s*\\([^)]*mForeground").containsMatchIn(beforeDelivery))
        }
    }

    @Test
    fun staleReaderDataCannotBeRetainedRenderedOrUsedForAuthorRequests() {
        val fragment = source("java/sp/phone/ui/fragment/ArticleListFragment.java")
        val delivery = fragment.substringAfter("public void setData(ThreadData data)")
            .substringBefore("private void renderData")
        val acceptance = delivery.indexOf("if (!isCurrentData(data)) return;")
        assertTrue(acceptance >= 0)
        assertTrue(acceptance < delivery.indexOf("mDeliveredData = data"))
        assertTrue(acceptance < delivery.indexOf("renderData(data)"))
        assertTrue(acceptance < delivery.indexOf("mAuthorLocations.deliver(data,"))

        val validation = fragment.substringAfter("private boolean isCurrentData(ThreadData data)")
            .substringBefore("public void setData(ThreadData data)")
        assertTrue(validation.contains("paging.generation == mRequestParam.readerGeneration"))
        assertTrue(validation.contains("paging.generation == reader.state().generation"))
        assertTrue(validation.contains("paging.effectivePage == mRequestParam.page"))
        assertTrue(validation.contains("reader.environmentMatches(ArticleAccounts.fingerprint(), enabled)"))
        assertTrue(validation.contains("mRequestParam.cacheOwner.equals(ArticleAccounts.currentOwner())"))

        val rebind = fragment.substringAfter("public void onViewCreated(View view, Bundle savedInstanceState)")
            .substringBefore("public void onDestroyView()")
        assertTrue(rebind.contains("if (isCurrentData(mDeliveredData))"))
        assertTrue(rebind.indexOf("isCurrentData(mDeliveredData)") < rebind.indexOf("renderData(mDeliveredData)"))
        assertTrue(rebind.indexOf("isCurrentData(mDeliveredData)") < rebind.indexOf("mAuthorLocations.deliver(mDeliveredData, false)"))
        val invalidation = fragment.substringAfter("viewModel.getReaderState().observe(this, state ->")
            .substringBefore("consumePendingAnchor();")
        assertTrue(invalidation.contains("mDeliveredData = null"))
        assertTrue(invalidation.contains("mAuthorLocations.deliver(null, false)"))
        assertTrue(invalidation.contains("mArticleAdapter.setData(null)"))
    }

    @Test
    fun metadataPayloadNeverEntersBodyBindingAndChecksGenerationAuthorAndRecycledHolder() {
        val adapter = source("java/sp/phone/ui/adapter/ArticleListAdapter.java")
        val payload = adapter.substringAfter("@NonNull List<Object> payloads)")
            .substringBefore("public void onBindViewHolder(@NonNull final ArticleViewHolder")
        val metadataBranch = payload.substringBefore("onBindViewHolder(holder, position)")
        assertTrue(metadataBranch.contains("payload.generation == mDataGeneration"))
        assertTrue(metadataBranch.contains("payload.author == row.getAuthorid()"))
        assertTrue(metadataBranch.contains("holder.nickNameTV.getTag() == row"))
        assertTrue(metadataBranch.contains("onBindAuthorDetail(holder, row)"))
        assertTrue(metadataBranch.contains("return;"))
        assertFalse(metadataBranch.contains("onBindContentView"))
        assertFalse(metadataBranch.contains("loadDataWithBaseURL"))
        val update = adapter.substringAfter("public void setAuthorLocations(")
            .substringBefore("public void setSupportListener")
        assertTrue(update.contains("notifyItemChanged(position, new AuthorMetadataPayload"))
        assertFalse(update.contains("notifyDataSetChanged"))
        assertFalse(adapter.contains("级别："))
        assertFalse(adapter.contains("威望："))
        assertTrue(adapter.contains("row.getISANONYMOUS() ? null"))
        assertTrue(adapter.contains("R.string.article_author_posts"))
        assertTrue(adapter.contains("R.string.article_author_location_posts"))
        val bindDetail = adapter.substringAfter("private void onBindAuthorDetail")
            .substringBefore("private LocalWebView createLocalWebView")
        assertTrue(bindDetail.contains("if (!TextUtils.equals(holder.detailTv.getText(), detail))"))
        assertTrue(bindDetail.substringAfter("if (!TextUtils.equals(holder.detailTv.getText(), detail))")
            .contains("holder.detailTv.setText(detail)"))
        val setData = adapter.substringAfter("public void setData(ThreadData data)")
            .substringBefore("public void setAuthorLocations")
        assertTrue(setData.contains("if (data == null) mAuthorLocations ="))
    }

    @Test
    fun accountSignalsInvalidateBeforeDeferredCaptureAndUseSettledListIndex() {
        val service = source("java/sp/phone/profile/AuthorLocationService.java")
        assertTrue(service.contains("getActiveIndexLiveData().observe(owner"))
        assertTrue(service.contains("getUserListLiveData().observe(owner"))
        assertFalse(service.contains("observeForever"))
        val signal = service.substringAfter("private void invalidateAccountSignal()")
            .substringBefore("private ProfileSession captureSession()")
        assertTrue(signal.indexOf("repository.invalidateSession()") < signal.indexOf("main.post("))
        val capture = service.substringAfter("private ProfileSession captureSession()")
            .substringBefore("private void whenSessionSettled")
        assertTrue(capture.contains("UserManager.INSTANCE.getUserList()"))
        assertTrue(capture.contains("UserManager.INSTANCE.getActiveIndex()"))
        assertTrue(capture.contains("User user = users.get(index)"))
        assertTrue(capture.contains("user.getCid()"))
        assertFalse(capture.contains("UserManager.INSTANCE.getActiveUser("))
        assertTrue(service.contains("page.controller.isCurrent(delivery)"))
        assertTrue(service.contains("service::whenSessionSettled"))
    }

    @Test
    fun supplementalTransportDoesNotReuseTheUiTaskOrItsLoggingSideEffects() {
        val transport = source("java/sp/phone/profile/ProfileLocationTransport.java")
        assertFalse(transport.contains("JsonProfileLoadTask"))
        assertFalse(transport.contains("RetrofitHelper"))
        assertFalse(transport.contains("NLog"))
        assertFalse(transport.contains("Logger"))
        assertFalse(transport.contains("ActivityUtils"))
    }
}
