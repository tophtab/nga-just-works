package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import sp.phone.http.bean.ThreadData
import sp.phone.mvp.model.convert.ArticleConvertFactory
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.param.ArticleListParam
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.Supplier

/** A validated cache handle, never an arbitrary caller-supplied filesystem path. */
data class ArticleCacheEntry(@JvmField val tid: Int, @JvmField val owner: String?, @JvmField val layoutId: String?) {
    init {
        require(tid > 0)
        require((owner == null) == (layoutId == null))
        if (owner != null) {
            require(validOwner(owner))
            require(layoutId != null && layoutPattern.matches(layoutId))
            if (!layoutId.contains("-window-")) require(layoutId.substringAfterLast('-').toIntOrNull()?.let { it > 0 } == true)
        }
    }
    fun isLegacy() = owner == null
    fun source(): ArticleSource? = when {
        isLegacy() -> null
        layoutId!!.startsWith("read_php-") -> ArticleSource.READ_PHP
        else -> ArticleSource.APP_API
    }
    fun pageSize(): Int? = layoutId?.substringAfterLast('-')?.toIntOrNull()
    fun applyTo(param: ArticleListParam) { param.tid = tid; param.cacheOwner = owner; param.cacheLayoutId = layoutId }

    companion object {
        private val layoutPattern = Regex("(read_php|app_api)-([1-9][0-9]{0,9}|window-[0-9a-f]{32})")
        @JvmStatic fun validOwner(owner: String?) = owner == "guest" || ArticleAccount.owner(owner) != null
        @JvmStatic fun from(param: ArticleListParam) = ArticleCacheEntry(param.tid, param.cacheOwner, param.cacheLayoutId)
        @JvmStatic fun forPaging(paging: ArticlePagingInfo): ArticleCacheEntry {
            require(paging.query.kind == ArticleQueryKind.FULL && paging.owner != null)
            val suffix = paging.pageSize?.toString() ?: "window-${UUID.randomUUID().toString().replace("-", "")}"
            return ArticleCacheEntry(paging.resolvedTid, paging.owner, "${paging.source.format}-$suffix")
        }
    }
}

/** Immutable write snapshot: only original source and prepared topic metadata are persisted. */
class ArticleCacheWrite(
    @JvmField val entry: ArticleCacheEntry,
    @JvmField val page: Int,
    @JvmField val topicInfo: String,
    @JvmField val raw: String,
    @JvmField val paging: ArticlePagingInfo?,
) {
    init {
        require(page > 0 && raw.isNotBlank() && topicInfo.isNotBlank())
        if (paging != null) {
            require(paging.query == ArticleQuery(entry.tid, 0, 0, 0) && paging.resolvedTid == entry.tid)
            require(paging.effectivePage == page && paging.owner == entry.owner)
        }
        if (entry.isLegacy()) require(paging == null || paging.source == ArticleSource.READ_PHP)
        else require(paging != null && paging.source == entry.source() && paging.pageSize == entry.pageSize())
    }
}

class ArticleStoredPage(
    @JvmField val entry: ArticleCacheEntry,
    @JvmField val page: Int,
    @JvmField val requestedPage: Int,
    @JvmField val raw: String,
    @JvmField val source: ArticleSource?,
    @JvmField val pageSize: Int?,
    @JvmField val pageBasis: ArticlePageBasis?,
)

class ArticleCacheRecord(
    @JvmField val entry: ArticleCacheEntry,
    @JvmField val topicInfo: String,
    @JvmField val pages: List<Int>,
) {
    fun asThreadInfo(): ThreadPageInfo = JSON.parseObject(topicInfo, ThreadPageInfo::class.java).apply {
        cacheEntry = entry
        val mode = when (entry.source()) { null -> "旧格式"; ArticleSource.READ_PHP -> "普通显示"; ArticleSource.APP_API -> "兼容显示" }
        val size = if (entry.isLegacy()) "" else entry.pageSize()?.let { " · 每页 $it 条" } ?: " · 单次窗口"
        cacheSummary = "$mode$size · 已存 ${pages.size} 页"
    }
}

/** Explicit version/format dispatch. Invalid owned data never falls through to the legacy parser. */
object ArticleCacheCodec {
    @JvmStatic fun encode(write: ArticleCacheWrite): String {
        val paging = requireNotNull(write.paging)
        require(!write.entry.isLegacy())
        val envelope = JSONObject(true).apply {
            put("schema", "thread-page"); put("version", 1); put("format", paging.source.format)
            put("owner", write.entry.owner); put("tid", write.entry.tid); put("queryKind", "FULL")
            put("layoutId", write.entry.layoutId); put("page", write.page); put("requestedPage", paging.requestedPage)
            put("pageSize", paging.pageSize); put("pageBasis", paging.pageBasis.name); put("raw", write.raw)
        }
        return JSON.toJSONString(envelope, SerializerFeature.WriteMapNullValue)
    }

    @JvmStatic fun decode(text: String, entry: ArticleCacheEntry, page: Int): ArticleStoredPage {
        require(!entry.isLegacy())
        val value = JSON.parse(text) as? JSONObject ?: throw IOException("Invalid cache envelope")
        require(value["schema"] == "thread-page" && value["version"] == 1)
        require(value["owner"] == entry.owner && value["tid"] == entry.tid && value["queryKind"] == "FULL")
        require(value["layoutId"] == entry.layoutId && value["page"] == page)
        val source = ArticleSource.entries.firstOrNull { it.format == value["format"] }
            ?: throw IOException("Unknown cache source")
        require(source == entry.source())
        require(value.containsKey("pageSize"))
        val size = value["pageSize"]?.let { require(it is Int && it > 0); it as Int }
        require(size == entry.pageSize())
        val requestedPage = value["requestedPage"] as? Int ?: throw IOException("Invalid cached request page")
        require(requestedPage > 0)
        val basis = ArticlePageBasis.entries.firstOrNull { it.name == value["pageBasis"] }
            ?: throw IOException("Invalid cached page basis")
        val raw = value["raw"] as? String ?: throw IOException("Missing cached source")
        require(raw.isNotBlank())
        return ArticleStoredPage(entry, page, requestedPage, raw, source, size, basis)
    }
}

fun interface ArticleCacheDecoder {
    fun decode(source: ArticleSource?, raw: String, query: ArticleQuery, page: Int): ThreadData?
}

class ArticleCacheReplay @JvmOverloads constructor(private val decoder: ArticleCacheDecoder = ArticleCacheDecoder { source, raw, query, page ->
    when (source) {
        null -> ArticleConvertFactory.getArticleInfo(raw)
        ArticleSource.READ_PHP -> NormalArticleParser().parse(raw, query, page)
        ArticleSource.APP_API -> AppArticleParser().parse(raw, query, page)
    }
}) {
    fun parse(stored: ArticleStoredPage): ThreadData {
        val query = ArticleQuery(stored.entry.tid, 0, 0, 0)
        val data = decoder.decode(stored.source, stored.raw, query, stored.requestedPage)
            ?: throw IOException("Unreadable cache page")
        val paging = if (stored.entry.isLegacy()) ArticlePagingInfo.normal(query, stored.page, data)
            else requireNotNull(data.pagingInfo)
        require(paging.query == query && paging.resolvedTid == stored.entry.tid && paging.effectivePage == stored.page)
        if (!stored.entry.isLegacy()) {
            require(paging.source == stored.source && paging.pageSize == stored.pageSize && paging.pageBasis == stored.pageBasis)
            require(data.isContentComplete && data.rowList?.isNotEmpty() == true)
        }
        data.pagingInfo = paging.withRequest(stored.entry.owner, 0)
        return data
    }
}

/** Shared list/open/read/write/delete resolver. Call on a worker; recheck identity at delivery too. */
class ArticleCacheStore(filesDir: File) {
    private val root = filesDir.canonicalFile

    fun list(currentOwner: String?): List<ArticleCacheRecord> = synchronized(lock) {
        val entries = mutableListOf<ArticleCacheEntry>()
        children(safe("cache")).filter { positiveName(it.name) != null && it.isDirectory }.forEach {
            entries.add(ArticleCacheEntry(it.name.toInt(), null, null))
        }
        if (ArticleCacheEntry.validOwner(currentOwner)) {
            children(safe("thread-cache-v1/$currentOwner")).filter { positiveName(it.name) != null && it.isDirectory }.forEach { topic ->
                children(topic).filter { it.isDirectory }.forEach { layout ->
                    try { entries.add(ArticleCacheEntry(topic.name.toInt(), currentOwner, layout.name)) }
                    catch (_: IllegalArgumentException) { /* Only validated handles enter the index. */ }
                }
            }
        }
        entries.mapNotNull { entry ->
            try {
                val directory = directory(entry, currentOwner)
                val metadata = readBounded(File(directory, if (entry.isLegacy()) "${entry.tid}.json" else "topic.json"), MAX_TOPIC_BYTES)
                validateTopic(metadata, entry.tid)
                val pages = pages(entry, currentOwner)
                if (pages.isEmpty()) null else ArticleCacheRecord(entry, metadata, pages)
            } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null } catch (_: com.alibaba.fastjson.JSONException) { null }
        }
    }

    fun pages(entry: ArticleCacheEntry, currentOwner: String?): List<Int> = synchronized(lock) {
        val directory = directory(entry, currentOwner)
        children(if (entry.isLegacy()) directory else File(directory, "pages")).mapNotNull { file ->
            if (!file.isFile || !file.name.endsWith(".json") || entry.isLegacy() && file.name == "${entry.tid}.json") null
            else positiveName(file.name.removeSuffix(".json"))
        }.distinct().sorted()
    }

    fun read(entry: ArticleCacheEntry, page: Int, currentOwner: String?): ArticleStoredPage = synchronized(lock) {
        require(page > 0)
        val directory = directory(entry, currentOwner)
        val file = File(directory, if (entry.isLegacy()) "$page.json" else "pages/$page.json")
        val text = readBounded(file, MAX_PAGE_BYTES)
        if (entry.isLegacy()) ArticleStoredPage(entry, page, page, text, null, 20, null)
        else ArticleCacheCodec.decode(text, entry, page)
    }

    fun write(write: ArticleCacheWrite, ownerNow: Supplier<String?>): ArticleCacheEntry = synchronized(lock) {
        validateTopic(write.topicInfo, write.entry.tid)
        val directory = directory(write.entry, ownerNow.get())
        if (!write.entry.isLegacy() && write.entry.pageSize() == null &&
            pages(write.entry, ownerNow.get()).any { it != write.page }) throw IOException("A cache window cannot merge pages")
        val topic = File(directory, if (write.entry.isLegacy()) "${write.entry.tid}.json" else "topic.json")
        val page = File(directory, if (write.entry.isLegacy()) "${write.page}.json" else "pages/${write.page}.json")
        val pageText = if (write.entry.isLegacy()) write.raw else ArticleCacheCodec.encode(write)
        val temporaryTopic = stage(topic, write.topicInfo, MAX_TOPIC_BYTES)
        var temporaryPage: File? = null
        try {
            temporaryPage = stage(page, pageText, MAX_PAGE_BYTES)
            directory(write.entry, ownerNow.get())
            replace(temporaryPage, page)
            replace(temporaryTopic, topic)
        } finally {
            temporaryTopic.delete(); temporaryPage?.delete()
        }
        write.entry
    }

    fun delete(entry: ArticleCacheEntry, currentOwner: String?) = synchronized(lock) {
        val directory = directory(entry, currentOwner)
        if (!directory.exists()) throw IOException("Cache entry missing")
        deleteTree(directory)
    }

    private fun directory(entry: ArticleCacheEntry, currentOwner: String?): File {
        if (!entry.isLegacy()) require(entry.owner == currentOwner) { "Cache account changed" }
        return safe(if (entry.isLegacy()) "cache/${entry.tid}" else "thread-cache-v1/${entry.owner}/${entry.tid}/${entry.layoutId}")
    }

    private fun safe(relative: String): File = checked(File(root, relative))
    private fun checked(file: File): File {
        if (file.canonicalFile != file.absoluteFile || !file.path.startsWith(root.path + File.separator)) {
            throw IOException("Invalid cache path")
        }
        return file
    }
    private fun children(directory: File): List<File> {
        checked(directory)
        val files = directory.listFiles()?.toList().orEmpty()
        if (files.size > MAX_ENTRIES) throw IOException("Too many cache entries")
        return files.filter { try { checked(it); true } catch (_: IOException) { false } }
    }
    private fun stage(target: File, text: String, limit: Int): File {
        checked(target)
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > limit) throw IOException("Cache entry too large")
        val parent = target.parentFile ?: throw IOException("Invalid cache path")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create cache directory")
        val temporary = File.createTempFile(".write-", ".tmp", parent)
        try { FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() } }
        catch (error: IOException) { temporary.delete(); throw error }
        return temporary
    }
    private fun replace(temporary: File, target: File) {
        checked(target)
        if (!temporary.renameTo(target)) throw IOException("Cannot replace cache file")
    }
    private fun deleteTree(file: File) {
        checked(file)
        if (file.isDirectory) children(file).forEach { deleteTree(it) }
        if (!file.delete()) throw IOException("Cannot delete cache entry")
    }
    private fun readBounded(file: File, limit: Int): String {
        checked(file)
        if (!file.isFile || file.length() > limit) throw IOException("Invalid cache file")
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size().toLong() + count > limit) throw IOException("Cache entry too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun validateTopic(raw: String, tid: Int) {
        val topic = JSON.parse(raw) as? JSONObject ?: throw IOException("Invalid topic description")
        require(topic["tid"] == tid && (topic["subject"] as? String)?.isNotBlank() == true)
    }
    private fun positiveName(name: String): Int? = name.takeIf { it.matches(Regex("[1-9][0-9]{0,9}")) }?.toIntOrNull()?.takeIf { it > 0 }

    companion object {
        private val lock = Any()
        const val MAX_PAGE_BYTES = 32 * 1024 * 1024
        const val MAX_TOPIC_BYTES = 512 * 1024
        private const val MAX_ENTRIES = 10000
    }
}
