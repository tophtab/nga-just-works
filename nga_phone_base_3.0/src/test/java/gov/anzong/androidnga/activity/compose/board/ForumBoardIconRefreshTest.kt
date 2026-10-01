package gov.anzong.androidnga.activity.compose.board

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.alibaba.fastjson2.JSON
import gov.anzong.androidnga.core.board.data.BoardEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.TimeUnit

class ForumBoardIconRefreshTest {
    @get:Rule val files = TemporaryFolder()
    private val prefix = "https://icons.cdn.test/ngabbs/nga_classic/f/app/"
    private fun board(id: String, fid: Int = 0, stid: Int = 0, vararg children: BoardEntity) =
        BoardEntity().apply {
            this.id = id; name = id; this.fid = fid; this.stid = stid
            this.children = children.toMutableList()
        }
    private fun response(result: String = "[]", metadata: String = JSON.toJSONString(prefix)) =
        requireNotNull(ForumBoardRepository.decodeRemoteBoardList(
            """{"forum_icon_pre":$metadata,"result":$result}"""))

    @Test fun prefixOnlyRefreshUpdatesAllActualObjectsWithoutListWritesOrStructuralRevision() {
        val first = board("7", 7)
        val duplicate = board("7", 7)
        val favorite = board("7", 7)
        val collection = board("12", 0, 12)
        val root = board("other", 0, 0, board("group", 0, 0, first, duplicate), collection)
        val bookmarks = board("bookmark", 0, 0, favorite)
        val state = BoardIconState { null }
        state.hydrate(listOf(root, bookmarks))
        var prefixWrites = 0
        var treeWrites = 0
        val before = root.children!!.toList()
        assertFalse(state.applyRemote(response(), listOf(root), bookmarks, mutableMapOf(),
            { prefixWrites++ }, { treeWrites++ }))
        listOf(first, duplicate, favorite).forEach { assertEquals(prefix + "7.png", it.iconUrl) }
        assertEquals("https://icons.cdn.test/proxy/cache_attach/ficon/12v.png", collection.iconUrl)
        assertSame(first, root.children!![0].children!![0])
        assertEquals(before, root.children)
        assertEquals(1, prefixWrites)
        assertEquals(0, treeWrites)
        state.applyRemote(response(), listOf(root), bookmarks, mutableMapOf(), { prefixWrites++ }, { treeWrites++ })
        state.applyRemote(response(metadata = "{}"), listOf(root), bookmarks, mutableMapOf(), { prefixWrites++ }, { treeWrites++ })
        assertEquals(1, prefixWrites)
        assertEquals(0, treeWrites)
        assertEquals(prefix + "7.png", favorite.iconUrl)
    }

    @Test fun actualSnapshotObserverSeesIconChangesButNotEqualPrefixAssignments() {
        val item = board("7", 7)
        val state = BoardIconState { null }
        state.hydrate(listOf(item))
        Snapshot.sendApplyNotifications()
        val observer = SnapshotStateObserver { it() }
        var notifications = 0
        observer.start()
        try {
            val scope = Any()
            val changed: (Any) -> Unit = { notifications++ }
            observer.observeReads(scope, changed) { assertTrue(item.iconUrl.contains("img4.nga.cn")) }
            assertTrue(state.acceptRemote(prefix))
            state.hydrate(listOf(item))
            Snapshot.sendApplyNotifications()
            assertEquals(1, notifications)
            observer.observeReads(scope, changed) { assertEquals(prefix + "7.png", item.iconUrl) }
            assertFalse(state.acceptRemote(prefix))
            state.hydrate(listOf(item))
            Snapshot.sendApplyNotifications()
            assertEquals(1, notifications)
        } finally { observer.stop(); observer.clear() }
    }

    @Test fun validatesEnvelopeAndIsolatesBadIconMetadataFromUsableCategories() {
        listOf("null", "{}", "[]", "42", "\"bad\"", "").forEach {
            assertNull(ForumBoardRepository.decodeRemoteBoardList(it))
        }
        listOf("null", "{}", "42", "\"array\"").forEach {
            assertNull(ForumBoardRepository.decodeRemoteBoardList("""{"result":$it,"forum_icon_pre":"$prefix"}"""))
        }
        assertNull(ForumBoardRepository.decodeRemoteBoardList("""{"forum_icon_pre":"$prefix"}"""))
        listOf("null", "{}", "[]", "42", "\"https://img4.nga.cn/unknown/\"").forEach {
            val decoded = response("""[{"id":"other","groups":[]}]""", it)
            assertNull(decoded.forum_icon_pre)
            assertEquals("other", decoded.result!![0].id)
        }
    }

    @Test fun malformedMembersAreRejectedBeforeAnyLiveTreeOrPrefixMutation() = runBlocking {
        val validForum = """{"id":8,"name":"new"}"""
        val validGroup = """{"id":"group","forums":[$validForum]}"""
        val validCategory = """{"id":"other","groups":[$validGroup]}"""
        val malformedResults = mutableListOf<String>()
        listOf("null", "42", "[]", "\"bad\"").forEach { invalid ->
            malformedResults += "[$validCategory,$invalid]"
            malformedResults += """[{"id":"other","groups":[$validGroup,$invalid]}]"""
            malformedResults += """[{"id":"other","groups":[{"id":"group","forums":[$validForum,$invalid]}]}]"""
        }
        malformedResults += """[{"id":"other","groups":{}}]"""
        malformedResults += """[{"id":"other","groups":[{"id":"group","forums":{}}]}]"""
        malformedResults.forEach { result ->
            val existing = board("7", 7)
            val group = board("group", 0, 0, existing)
            val root = board("other", 0, 0, group)
            val bookmark = board("bookmark")
            val state = BoardIconState { null }
            state.hydrate(listOf(root))
            val initialIcon = existing.iconUrl
            var prefixWrites = 0
            var treeWrites = 0
            var revision = 0
            val gate = BoardRefreshGate({ 0L }, {}, { TimeUnit.DAYS.toMillis(1) })
            val raw = """{"result":$result,"forum_icon_pre":"$prefix"}"""
            assertNull(ForumBoardRepository.decodeRemoteBoardList(raw))
            gate.refresh({ ForumBoardRepository.decodeRemoteBoardList(raw) }) { decoded ->
                if (state.applyRemote(decoded, listOf(root), bookmark,
                        mutableMapOf("group" to group, "7" to existing),
                        { prefixWrites++ }, { treeWrites++ })) revision++
            }
            assertEquals(listOf("7"), group.children!!.map { it.id })
            assertEquals(BoardIconUrlResolver.defaultPrefix, state.prefix)
            assertEquals(initialIcon, existing.iconUrl)
            assertEquals(0, prefixWrites)
            assertEquals(0, treeWrites)
            assertEquals(0, revision)
        }
        // Missing or null optional lists retain their prior empty-list semantics.
        listOf("""[{"id":"other"}]""", """[{"id":"other","groups":null}]""",
            """[{"id":"other","groups":[{"id":"group"},{"id":"empty","forums":null}]}]""")
            .forEach { assertEquals(prefix, response(it).forum_icon_pre) }
    }

    @Test fun preferenceCacheDefaultPrecedenceAndDelayedCacheCannotOverrideRemote() {
        val cache = files.newFolder()
        assertNull(ForumBoardRepository.loadRemoteBoardList(cache))
        val raw = cache.resolve("board_list_remote.json")
        raw.writeText("broken")
        assertNull(ForumBoardRepository.loadRemoteBoardList(cache))
        assertEquals("broken", raw.readText())
        raw.writeText("""{"result":[],"forum_icon_pre":"$prefix"}""")
        val cached = ForumBoardRepository.loadRemoteBoardList(cache)!!.forum_icon_pre
        listOf<() -> Any?>({ null }, { 42 }, { throw ClassCastException() }, { throw IOException() }).forEach { read ->
            val state = BoardIconState(read)
            assertEquals(BoardIconUrlResolver.defaultPrefix, state.prefix)
            assertTrue(state.acceptCached(cached))
            assertEquals(prefix, state.prefix)
        }
        val saved = BoardIconState { prefix }
        assertFalse(saved.needsCachedPrefix)
        assertFalse(saved.acceptCached(BoardIconUrlResolver.defaultPrefix))
        val remote = BoardIconState { null }
        assertFalse(remote.acceptRemote(BoardIconUrlResolver.defaultPrefix))
        assertFalse(remote.acceptCached(prefix))
        assertEquals(BoardIconUrlResolver.defaultPrefix, remote.prefix)
    }

    @Test fun oldJsonAndInjectedDerivedFieldsRoundTripWithoutPersistingIcons() {
        val old = """[{"id":"7","fid":"7","name":"kept","head":"header","iconUrl":"https://injected.test/x","children":[{"id":"8","name":"nested","fid":8}]}]"""
        val roots = JSON.parseArray(old, BoardEntity::class.java)
        assertEquals("", roots[0].iconUrl)
        val state = BoardIconState { prefix }
        state.hydrate(roots)
        val encoded = ForumBoardRepository.encodeBookmarkBoards(roots)
        assertFalse(encoded.contains("iconUrl"))
        assertFalse(encoded.contains("delegate"))
        val restored = ForumBoardRepository.decodeBookmarkBoards(encoded)
        state.hydrate(restored)
        assertEquals(prefix + "7.png", restored[0].iconUrl)
        assertEquals(prefix + "8.png", restored[0].children!![0].iconUrl)
        assertEquals("header", restored[0].head)
        assertEquals("kept", restored[0].name)
    }

    @Test fun additionsStayInOriginalGroupDeduplicateAndFreezeCanonicalTreeBeforeDelayedSave() {
        val existing = board("7", 7)
        val group = board("group", 0, 0, existing)
        val root = board("other", 0, 0, group)
        val secondRoot = board("wow")
        val favorite = board("9", 9)
        val bookmark = board("bookmark", 0, 0, favorite)
        val index = mutableMapOf("group" to group, "7" to existing, "9" to favorite)
        val renderBefore = boardRenderSnapshot(root)
        val state = BoardIconState { prefix }
        var saved: List<BoardEntity>? = null
        val incoming = response("""[{"id":"other","groups":[{"id":"group","forums":[{"id":8,"name":"new"},{"id":8,"name":"duplicate"},{"id":9,"name":"favorite only"},{"id":10},{"id":0,"name":"invalid"}]},{"id":"absent","forums":[{"id":11,"name":"missing parent"}]}]},{"id":"games","groups":[{"id":"group","forums":[{"id":12,"name":"unsupported category"}]}]}]""")
        assertTrue(state.applyRemote(incoming, listOf(root, secondRoot), bookmark, index, {}, { saved = it }))
        assertEquals(listOf("7", "8"), group.children!!.map { it.id })
        assertEquals(prefix + "8.png", group.children!![1].iconUrl)
        assertEquals(1, renderBefore[0].children.size)
        assertEquals(2, boardRenderSnapshot(root)[0].children.size)
        group.children!!.add(board("13", 13))
        existing.name = "later change"
        val persisted = JSON.parseArray(JSON.toJSONString(saved), BoardEntity::class.java)
        assertEquals(listOf("other", "wow"), persisted.map { it.id })
        assertEquals(listOf("7", "8"), persisted[0].children!![0].children!!.map { it.id })
        assertEquals("7", persisted[0].children!![0].children!![0].name)
        assertEquals(listOf("9"), bookmark.children!!.map { it.id })
    }

    @Test fun dragCancelReloadAndLateFailedSaveKeepLatestIconsAndNewerMembership() {
        val a = board("1", 1); val b = board("2", 2)
        val bookmark = board("bookmark", 0, 0, a, b)
        val state = BoardIconState { null }
        state.hydrate(listOf(bookmark))
        val snapshot = bookmark.children!!.toList()
        assertTrue(BookmarkOrder.move(bookmark.children!!, 0, 1))
        val candidate = bookmark.children!!.toList()
        state.acceptRemote(prefix)
        state.hydrate(listOf(bookmark))
        assertTrue(state.restoreBookmarks(bookmark, snapshot, candidate))
        assertEquals(listOf("1_0", "2_0"), bookmark.children!!.map(::bookmarkStableKey))
        assertEquals(prefix + "1.png", a.iconUrl)
        // External reload replaces objects; restoration rehydrates older snapshot references.
        state.restoreBookmarks(bookmark, listOf(board("1", 1), board("2", 2)))
        state.acceptRemote("https://new.cdn.test/ngabbs/nga_classic/f/app/")
        state.hydrate(listOf(bookmark))
        state.restoreBookmarks(bookmark, snapshot)
        assertTrue(a.iconUrl.startsWith("https://new.cdn.test/"))
        bookmark.children!!.add(board("3", 3))
        assertFalse(state.restoreBookmarks(bookmark, snapshot, candidate))
        assertEquals(listOf("1", "2", "3"), bookmark.children!!.map { it.id })
        val directory = files.newFolder()
        ForumBoardRepository.writeBookmarkBoard(directory, bookmark.children!!)
        val restarted = ForumBoardRepository.readBookmarkBoards(directory)
        state.hydrate(restarted)
        assertTrue(restarted.all { it.iconUrl.startsWith("https://new.cdn.test/") })
    }

    @Test fun viewKeepsLocalPriorityPagerIdentityAndExistingGestureWiring() {
        val root = generateSequence(java.io.File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.first { java.io.File(it, "nga_phone_base_3.0").isDirectory }
        val source = root.resolve("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt").readText()
        assertTrue(source.indexOf("if (resId > 0)") < source.indexOf("val url = child.iconUrl"))
        assertTrue(source.contains("val imageSize = 48.dp"))
        assertTrue(source.contains("placeholder = painterResource(id = R.drawable.default_board_icon)"))
        assertTrue(source.contains("error = painterResource(id = R.drawable.default_board_icon)"))
        assertTrue(source.contains("remember(boardData.id, revision) { boardRenderSnapshot(boardData) }"))
        assertFalse(source.contains("key(revision)"))
        assertTrue(source.contains("forumBoardViewModel.showTopicList(child)"))
        assertTrue(source.contains("bookmarkStableKey"))
        assertTrue(source.contains("customActions"))
    }

    @Test fun dailyGateCoalescesInFlightAndThrottlesSuccessFailureAndCancellation() = runBlocking {
        val day = TimeUnit.DAYS.toMillis(1)
        var now = day
        var last = 1L
        var requests = 0
        var applications = 0
        val gate = BoardRefreshGate({ last }, { last = it }, { now })
        gate.refresh({ requests++; "early" }, { applications++ })
        assertEquals(0, requests)
        now++
        val pending = CompletableDeferred<String>()
        val first = launch { gate.refresh({ requests++; pending.await() }, { applications++ }) }
        yield()
        gate.refresh({ requests++; "duplicate" }, { applications++ })
        assertEquals(1, requests)
        pending.complete("accepted")
        first.join()
        assertEquals(1, applications)
        gate.refresh({ requests++; "too soon" }, { applications++ })
        assertEquals(1, requests)
        now += day
        gate.refresh<String>({ requests++; throw IOException() }, { applications++ })
        assertEquals(now, last)
        gate.refresh({ requests++; "failure retry" }, { applications++ })
        assertEquals(2, requests)
        now += day
        val cancelledRequest = CompletableDeferred<String>()
        val cancelled = launch { gate.refresh({ requests++; cancelledRequest.await() }, { applications++ }) }
        yield()
        cancelled.cancel(); cancelled.join()
        assertEquals(now, last)
        gate.refresh({ requests++; "cancel retry" }, { applications++ })
        assertEquals(3, requests)
        assertEquals(1, applications)
        now += day
        gate.refresh({ requests++; "next normal attempt" }, { applications++ })
        assertEquals(4, requests)
        assertEquals(2, applications)
    }
}
