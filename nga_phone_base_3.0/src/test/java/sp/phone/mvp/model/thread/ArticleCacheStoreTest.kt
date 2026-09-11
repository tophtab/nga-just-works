package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.entity.ThreadPageInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class ArticleCacheStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val tid = 120002
    private val topic = "{ \"tid\": 120002, \"subject\": \"offline topic\", \"extra\": [true] }"
    private val query = ArticleQuery(tid, 0, 0, 0)

    private fun paging(source: ArticleSource = ArticleSource.APP_API, size: Int? = 10, page: Int = 2, owner: String? = "42") =
        ArticlePagingInfo(query, source, tid, page, page, size, 40, 400,
            ArticlePageBasis.REQUESTED, false, owner = owner, generation = 7)
    private fun save(store: ArticleCacheStore, paging: ArticlePagingInfo, raw: String = "raw source\n正文 \\ \""): ArticleCacheEntry {
        val entry = if (paging.owner == null) ArticleCacheEntry(tid, null, null) else ArticleCacheEntry.forPaging(paging)
        return store.write(ArticleCacheWrite(entry, paging.effectivePage, topic, raw, paging)) { paging.owner }
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected invalid cache operation") }
        catch (_: IllegalArgumentException) { }
        catch (_: java.io.IOException) { }
        catch (_: com.alibaba.fastjson.JSONException) { }
    }

    @Test fun knownLayoutsMergeNumericSparsePagesAndPreserveOriginalMetadata() {
        val store = ArticleCacheStore(temporary.root)
        val app10 = save(store, paging(page = 10))
        assertEquals(app10, save(store, paging(page = 2)))
        val app20 = save(store, paging(size = 20, page = 7))
        save(store, paging(size = 20, page = 2))
        val normal = save(store, paging(ArticleSource.READ_PHP, 20, 2))
        val legacy = save(store, paging(ArticleSource.READ_PHP, 20, 2, null))
        val records = store.list("42")
        assertEquals(4, records.size)
        assertEquals(listOf(2, 10), store.pages(app10, "42"))
        assertEquals(listOf(2, 7), store.pages(app20, "42"))
        assertTrue(records.all { it.topicInfo == topic })
        assertEquals("raw source\n正文 \\ \"", store.read(app10, 10, "42").raw)
        assertEquals(ArticleSource.APP_API, store.read(app10, 10, "42").source)
        assertEquals(ArticleSource.READ_PHP, store.read(normal, 2, "42").source)
        assertNull(store.read(legacy, 2, null).source)
        assertNotEquals(records.first { it.entry == app10 }.asThreadInfo(), records.first { it.entry == app20 }.asThreadInfo())
        val description = JSON.parseObject(JSON.toJSONString(records.first().asThreadInfo()))
        assertFalse(description.containsKey("cacheEntry"))
        assertFalse(description.containsKey("cacheSummary"))
    }

    @Test fun ownershipAppliesToTitleListReadWriteAndDelete() {
        val store = ArticleCacheStore(temporary.root)
        val first = save(store, paging(owner = "42"))
        val second = save(store, paging(owner = "43"))
        assertEquals(listOf(first), store.list("42").map { it.entry })
        assertEquals(listOf(second), store.list("43").map { it.entry })
        assertTrue(store.list("guest").isEmpty())
        rejected { store.read(first, 2, "43") }
        rejected { store.delete(first, "43") }
        rejected { store.write(ArticleCacheWrite(first, 2, topic, "changed", paging())) { "43" } }
        store.delete(first, "42")
        assertTrue(store.list("42").isEmpty())
        assertEquals("raw source\n正文 \\ \"", store.read(second, 2, "43").raw)
    }

    @Test fun unknownPageSizeSnapshotsNeverUnionWindowsOrDropNullMetadata() {
        val store = ArticleCacheStore(temporary.root)
        val first = save(store, paging(size = null, page = 2))
        val second = save(store, paging(size = null, page = 7))
        assertNotEquals(first, second)
        assertEquals(listOf(2), store.pages(first, "42"))
        assertEquals(listOf(7), store.pages(second, "42"))
        rejected { store.write(ArticleCacheWrite(first, 7, topic, "another window", paging(size = null, page = 7))) { "42" } }
        assertNull(store.read(first, 2, "42").pageSize)
        assertEquals(2, store.list("42").size)
    }

    @Test fun corruptOwnedEnvelopesNeverFallBackToLegacyOrAnotherSource() {
        val store = ArticleCacheStore(temporary.root)
        val entry = save(store, paging())
        save(store, paging(ArticleSource.READ_PHP, 20, 2, null), "legacy source")
        val file = File(temporary.root, "thread-cache-v1/42/$tid/app_api-10/pages/2.json")
        val valid = file.readText()
        for ((key, value) in listOf("version" to 2, "format" to "unknown", "owner" to "43", "tid" to 2,
            "queryKind" to "AUTHOR", "layoutId" to "app_api-20", "page" to 7, "pageSize" to 20,
            "pageBasis" to "invented", "requestedPage" to 0, "raw" to null)) {
            val envelope = JSON.parseObject(valid)
            envelope[key] = value
            file.writeText(envelope.toJSONString())
            rejected { store.read(entry, 2, "42") }
        }
        file.writeText("{broken")
        rejected { store.read(entry, 2, "42") }
        assertEquals("legacy source", store.read(ArticleCacheEntry(tid, null, null), 2, "42").raw)
    }

    @Test fun replayDispatchIsExplicitAndChecksStoredLayoutAgainstParsedResponse() {
        val store = ArticleCacheStore(temporary.root)
        val observed = mutableListOf<ArticleSource?>()
        val replay = ArticleCacheReplay { source, _, requested, page ->
            observed.add(source)
            ThreadData().apply {
                rowList = listOf(ThreadRowInfo().apply { this.tid = requested.tid })
                rowNum = 1
                threadInfo = ThreadPageInfo().apply { this.tid = requested.tid }
                pagingInfo = paging(source ?: ArticleSource.READ_PHP, if (source == ArticleSource.APP_API) 10 else 20, page)
            }
        }
        for (source in listOf(null, ArticleSource.READ_PHP, ArticleSource.APP_API)) {
            val entry = save(store, paging(source ?: ArticleSource.READ_PHP, if (source == ArticleSource.APP_API) 10 else 20,
                owner = if (source == null) null else "42"))
            replay.parse(store.read(entry, 2, "42"))
        }
        assertEquals(listOf(null, ArticleSource.READ_PHP, ArticleSource.APP_API), observed)
        val wrongLayout = save(store, paging(size = 30))
        rejected { replay.parse(store.read(wrongLayout, 2, "42")) }
    }

    @Test fun pathsCannotTraverseOrUseSymlinksAndInterruptedOwnerChangeDoesNotCommit() {
        rejected { ArticleCacheEntry(tid, "../42", "app_api-10") }
        rejected { ArticleCacheEntry(tid, "42", "../read_php-20") }
        rejected { ArticleCacheEntry(tid, "42", "app_api-9999999999") }
        rejected { ArticleCacheEntry(tid, null, "app_api-10") }
        val store = ArticleCacheStore(temporary.root)
        val entry = save(store, paging())
        var checks = 0
        rejected { store.write(ArticleCacheWrite(entry, 2, topic, "later", paging())) { if (++checks == 1) "42" else "43" } }
        assertEquals("raw source\n正文 \\ \"", store.read(entry, 2, "42").raw)
        val pages = File(temporary.root, "thread-cache-v1/42/$tid/app_api-10/pages")
        assertFalse(pages.listFiles()!!.any { it.name.endsWith(".tmp") })
        Files.createSymbolicLink(File(pages, "7.json").toPath(), File(pages, "2.json").toPath())
        rejected { store.read(entry, 7, "42") }
        assertEquals(listOf(2), store.pages(entry, "42"))
    }

    private fun archive(vararg values: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            values.forEach { (path, content) -> zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    @Test fun legacyZipImportsOnlyValidatedLegacyPathsAndDoesNotTouchOwnedRoot() {
        val store = ArticleCacheStore(temporary.root)
        val owned = save(store, paging())
        val zip = archive("cache/" to "", "cache/$tid/" to "", "cache/$tid/$tid.json" to topic, "cache/$tid/2.json" to "legacy")
        assertEquals(2, LegacyArticleCacheArchive.importArchive(ByteArrayInputStream(zip), temporary.root))
        assertEquals("legacy", store.read(ArticleCacheEntry(tid, null, null), 2, "42").raw)
        for (path in listOf("../escape", "cache/../escape", "cache/$tid/../../../escape", "/cache/$tid/2.json",
            "thread-cache-v1/42/$tid/app_api-10/pages/2.json", "cache/0/2.json", "cache/$tid/0.json")) {
            rejected { LegacyArticleCacheArchive.importArchive(ByteArrayInputStream(archive("cache/$tid/2.json" to "changed", path to "wrong")), temporary.root) }
            assertEquals("legacy", store.read(ArticleCacheEntry(tid, null, null), 2, "42").raw)
        }
        assertEquals("raw source\n正文 \\ \"", store.read(owned, 2, "42").raw)
        File(temporary.root, "cache/$tid/.write-pending.tmp").writeText("temporary source")
        val output = ByteArrayOutputStream()
        assertEquals(1, LegacyArticleCacheArchive.exportArchive(temporary.root, output))
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names.add(entry.name); zip.closeEntry() }
        }
        assertEquals(setOf("cache/$tid/$tid.json", "cache/$tid/2.json"), names.toSet())
        val restoredRoot = temporary.newFolder("restored")
        LegacyArticleCacheArchive.importArchive(ByteArrayInputStream(output.toByteArray()), restoredRoot)
        assertEquals("legacy", ArticleCacheStore(restoredRoot).read(ArticleCacheEntry(tid, null, null), 2, null).raw)
    }

    @Test fun actualAppAndNormalParsersRoundTripStoredSourceAndReportedCoordinates() {
        val renderer = ArticleRowRenderer { _, _ -> }
        val blacklist = ArticleBlacklist { false }
        val app = AppArticleParser(renderer, blacklist)
        val normal = NormalArticleParser(renderer, blacklist)
        val replay = ArticleCacheReplay { source, raw, query, page ->
            when (source) {
                ArticleSource.APP_API -> app.parse(raw, query, page)
                ArticleSource.READ_PHP -> normal.parse(raw, query, page)
                null -> sp.phone.mvp.model.convert.ArticleConvertFactory.getArticleInfo(raw, renderer, blacklist)
            }
        }
        val store = ArticleCacheStore(temporary.root)
        val appRaw = "\uFEFF" + """{ "currentPage":13,"perPage":10,"totalPage":30,"vrows":300,"tsubject":"topic","result":[{"tid":$tid,"pid":50120,"lou":120,"content":"<b>Reply to [pid=2,3,4]Reply[/pid] source</b>正文"}]}"""
        val normalRaw = "\uFEFF" + """{"data":{"__ROWS":166,"__R__ROWS":1,"__T":{"tid":$tid,"subject":"topic"},"__R":{"0":{"tid":$tid,"pid":50120,"lou":120,"content":"正文"}}}}"""
        val parsedApp = app.parse(appRaw, query, 7)
        val parsedNormal = normal.parse(normalRaw, query, 7)
        for (data in listOf(parsedApp, parsedNormal)) {
            data.pagingInfo = data.pagingInfo.withRequest("42", 1)
            val entry = ArticleCacheEntry.forPaging(data.pagingInfo)
            store.write(ArticleCacheWrite(entry, data.pagingInfo.effectivePage, topic, data.rawData, data.pagingInfo)) { "42" }
            val restored = replay.parse(store.read(entry, data.pagingInfo.effectivePage, "42"))
            assertEquals(data.rawData, restored.rawData)
            assertEquals(data.rowList[0].content, restored.rowList[0].content)
            assertEquals(7, restored.pagingInfo.requestedPage)
            assertEquals(data.pagingInfo.effectivePage, restored.pagingInfo.effectivePage)
            assertEquals(data.pagingInfo.pageSize, restored.pagingInfo.pageSize)
            assertEquals(data.pagingInfo.source, restored.pagingInfo.source)
            assertEquals("42", restored.pagingInfo.owner)
        }
        val legacy = ArticleCacheEntry(tid, null, null)
        store.write(ArticleCacheWrite(legacy, 7, topic, normalRaw, null)) { null }
        assertEquals(normalRaw, replay.parse(store.read(legacy, 7, null)).rawData)
    }
}
