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
        val replayGuard = "if (data != null && data == lastDeliveredData) {"
        val replayIndex = pageDelivery.indexOf(replayGuard)
        assertTrue(replayIndex >= 0)
        assertTrue(pageDelivery.indexOf("if (closed)") in 0 until replayIndex)
        assertTrue(pageDelivery.substringBefore(replayGuard).contains("return;"))
        assertTrue(replayIndex < pageDelivery.indexOf("lastDeliveredData = data"))
        val replay = pageDelivery.substringAfter(replayGuard)
            .substringBefore("lastDeliveredData = data")
        assertTrue(replay.contains("Delivery previous = updates.getValue()"))
        assertTrue(replay.contains("previous != null && previous.generation == generation"))
        assertTrue(replay.contains("updates.setValue(previous)"))
        assertTrue(replay.contains("return;"))
        // No new epoch/consumer: pending first delivery and cache-only intent survive READY replay.
        for (sideEffect in listOf("++generation", "subscription.close()", "whenSessionSettled(",
            "repository.", "new Delivery(", "ArticleAuthorIds.fromPage(")) {
            assertFalse(replay.contains(sideEffect))
            val effectIndex = pageDelivery.indexOf(sideEffect)
            assertTrue(effectIndex > replayIndex)
        }
    }

    @Test
    fun nullDeliveryAlwaysClearsReplayIdentityAndCloseReleasesTheResponse() {
        val service = source("java/sp/phone/profile/AuthorLocationService.java")
        val page = service.substringAfter("public static final class Page")
        val delivery = page.substringAfter("public void deliver(ThreadData data, boolean online)")
            .substringBefore("@Override")
        assertTrue(page.contains("private ThreadData lastDeliveredData;"))
        assertTrue(delivery.contains("if (data != null && data == lastDeliveredData)"))
        val replacement = delivery.substringAfter("lastDeliveredData = data;")
        assertTrue(replacement.contains("long version = ++generation"))
        assertTrue(replacement.contains("subscription.close()"))
        assertTrue(replacement.contains("new Delivery(version, AuthorLocationRepository.Snapshot.empty())"))
        assertTrue(replacement.contains("service.repository.subscribe(authors, online,"))
        val close = page.substringAfter("public void close()")
        assertTrue(close.contains("lastDeliveredData = null"))
        assertTrue(close.indexOf("lastDeliveredData = null") < close.indexOf("subscription.close()"))
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
        assertTrue(service.contains("delivery.generation == page.generation"))
        assertTrue(service.contains("generation == version && service.sessionSignal == signal"))
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
