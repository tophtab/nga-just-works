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
        assertTrue(delivery.contains("mAuthorLocations.deliver(data, !mRequestParam.loadCache)"))
        assertFalse(delivery.contains("RESUMED"))
        assertFalse(delivery.contains("getCurrentFragment"))
        assertFalse(delivery.contains("getPrefetchPages"))
        assertTrue(fragment.contains("mAuthorLocations.deliver(mDeliveredData, false)"))
        assertTrue(fragment.contains("AuthorLocationService.bind(getContext(), getViewLifecycleOwner()"))
        assertTrue(fragment.substringAfter("public void onDestroyView()")
            .substringBefore("private void applyReplyFabClearance").contains("mAuthorLocations.close()"))
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
    fun supplementalTransportDoesNotReuseTheUiTaskAndBothProfileReadersShareWrappers() {
        val transport = source("java/sp/phone/profile/ProfileLocationTransport.java")
        val task = source("java/sp/phone/task/JsonProfileLoadTask.java")
        val parser = source("java/sp/phone/profile/ProfileLocationParser.java")
        assertTrue(task.contains("ProfileEnvelopeParser.parse(js)"))
        assertTrue(parser.contains("ProfileEnvelopeParser.parse(source)"))
        assertFalse(transport.contains("JsonProfileLoadTask"))
        assertFalse(transport.contains("RetrofitHelper"))
        assertFalse(transport.contains("NLog"))
        assertFalse(transport.contains("Logger"))
        assertFalse(transport.contains("ActivityUtils"))
    }
}
