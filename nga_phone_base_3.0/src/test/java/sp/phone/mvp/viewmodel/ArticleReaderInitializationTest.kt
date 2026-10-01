package sp.phone.mvp.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sp.phone.mvp.model.thread.ArticleAnchor
import sp.phone.mvp.model.thread.ArticleNavigation
import sp.phone.mvp.model.thread.ArticleQuery
import sp.phone.param.ArticleListParam

class ArticleReaderInitializationTest {
    @Before fun mainThread() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }
    @After fun resetExecutor() { ArchTaskExecutor.getInstance().setDelegate(null) }

    private fun launch() = ArticleListParam().apply {
        tid = 100001; page = 9
    }

    @Test fun retainedViewModelAndPageClonesPreserveReaderAndDoNotCreateAnchors() {
        val vm = ArticleShareViewModel()
        val param = launch()
        val reader = vm.initializeReader(param)
        reader.ensureEnvironment("account", false, null, "https://bbs.nga.cn", 9)
        assertEquals(9, reader.state().currentPage)
        assertNull(reader.state().pendingAnchor)
        assertSame(reader, vm.initializeReader(param.clone() as ArticleListParam))
        val offscreen = (param.clone() as ArticleListParam).apply { page = 10 }
        assertSame(reader, vm.initializeReader(offscreen))
        assertNull(reader.state().pendingAnchor)
        assertSame(reader, vm.initializeReader(param))
        assertNull(reader.state().pendingAnchor)
    }

    @Test fun newIntentToSameQueryRetiresOldKeyAndPendingJump() {
        val vm = ArticleShareViewModel()
        val first = vm.initializeReader(launch())
        val oldKey = first.key(9)
        val pendingJump = ArticleAnchor(oldKey.generation, 9, null, 173)
        vm.setPendingAnchor(pendingJump)
        vm.setRefreshPage(9)
        vm.setCachePage(9)
        val next = launch().apply { page = 1 }
        vm.resetReader(next)
        val current = vm.readerSession
        assertNotSame(first, current)
        assertFalse(current.accepts(oldKey))
        assertTrue(current.state().generation > oldKey.generation)
        assertEquals(1, current.state().currentPage)
        assertNull(current.state().pendingAnchor)
        assertNull(vm.refreshPage.value); assertNull(vm.cachePage.value)
        assertFalse(current.consumeAnchor(pendingJump))
    }

    @Test fun showAllStartsFullThreadAtFirstPageWithoutReplyAnchor() {
        val vm = ArticleShareViewModel()
        val lookup = launch().apply { pid = 50173; authorId = 42; searchPost = 1 }
        vm.initializeReader(lookup)
        val full = ArticleNavigation.showAll(lookup, null)!!
        vm.resetReader(full)
        val reader = vm.readerSession
        assertEquals(ArticleQuery(lookup.tid, 0, 0, 0), reader.state().query)
        assertEquals(1, reader.state().currentPage)
        assertNull(reader.state().pendingAnchor)
    }
}
