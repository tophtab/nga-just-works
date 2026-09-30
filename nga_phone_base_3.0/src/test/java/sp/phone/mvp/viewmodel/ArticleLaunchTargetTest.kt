package sp.phone.mvp.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sp.phone.param.ArticleListParam

class ArticleLaunchTargetTest {
    @Before fun mainThread() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }
    @After fun resetExecutor() { ArchTaskExecutor.getInstance().setDelegate(null) }

    private fun launch() = ArticleListParam().apply {
        tid = 100001; page = 9; targetPid = 50173; targetFloor = 173
    }

    @Test fun retainedViewModelAndPageClonesNeverReseedConsumedTarget() {
        val vm = ArticleShareViewModel()
        val param = launch()
        val reader = vm.initializeReader(param)
        val target = reader.state().pendingAnchor!!
        reader.ensureEnvironment("account", false, null, "https://bbs.nga.cn", 9)
        assertSame(target, reader.state().pendingAnchor)
        assertSame(reader, vm.initializeReader(param.clone() as ArticleListParam))
        assertTrue(reader.consumeAnchor(target))
        val offscreen = (param.clone() as ArticleListParam).apply { page = 10 }
        assertSame(reader, vm.initializeReader(offscreen))
        assertNull(reader.state().pendingAnchor)
        assertSame(reader, vm.initializeReader(param))
        assertNull(reader.state().pendingAnchor)
    }

    @Test fun newIntentToSameQueryRetiresOldKeyAndSeedsOnlyNewLaunch() {
        val vm = ArticleShareViewModel()
        val first = vm.initializeReader(launch())
        val oldKey = first.key(9)
        vm.setRefreshPage(9)
        vm.setCachePage(9)
        val next = launch().apply { targetPid = 50003; targetFloor = 3; page = 1 }
        vm.resetReader(next)
        val current = vm.readerSession
        assertNotSame(first, current)
        assertFalse(current.accepts(oldKey))
        assertTrue(current.state().generation > oldKey.generation)
        assertEquals(50003, current.state().pendingAnchor!!.pid)
        assertNull(vm.refreshPage.value); assertNull(vm.cachePage.value)
        val anchor = current.state().pendingAnchor!!
        assertFalse(current.consumeAnchor(first.state().pendingAnchor!!))
        assertTrue(current.consumeAnchor(anchor))
        vm.resetReader(ArticleListParam().apply { tid = next.tid; page = 1 })
        assertNull(vm.readerSession.state().pendingAnchor)
    }

    @Test fun environmentRetirementDoesNotReplayLaunchTargetOnRotation() {
        val vm = ArticleShareViewModel()
        val param = launch()
        val reader = vm.initializeReader(param)
        reader.ensureEnvironment("first", true, "42", "https://bbs.nga.cn", 9)
        reader.ensureEnvironment("second", true, "43", "https://bbs.nga.cn", 9)
        assertNull(vm.initializeReader(param).state().pendingAnchor)
    }
}
