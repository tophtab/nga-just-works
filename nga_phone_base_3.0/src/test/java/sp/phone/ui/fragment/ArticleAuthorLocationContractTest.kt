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
}
