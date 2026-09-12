package sp.phone.mvp.model.thread

import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.param.ArticleListParam

/** Query coordinates are independent of the source's page size and a row's original floor. */
data class ArticleQuery(
    @JvmField val tid: Int,
    @JvmField val pid: Int,
    @JvmField val authorId: Int,
    @JvmField val searchPost: Int,
) {
    @JvmField val kind: ArticleQueryKind = when {
        pid != 0 || searchPost != 0 -> ArticleQueryKind.LOOKUP
        authorId != 0 -> ArticleQueryKind.AUTHOR
        else -> ArticleQueryKind.FULL
    }

    fun isValid() = tid >= 0 && pid >= 0 && (tid > 0 || pid > 0)

    fun browserUrl(origin: String, page: Int): String = buildString {
        append(origin.trimEnd('/')).append("/read.php?page=").append(page.coerceAtLeast(1))
        if (tid != 0) append("&tid=").append(tid)
        if (pid != 0) append("&pid=").append(pid)
        if (authorId != 0) append("&authorid=").append(authorId)
        if (searchPost != 0) append("&searchpost=").append(searchPost)
    }

    companion object {
        @JvmStatic fun from(param: ArticleListParam) =
            ArticleQuery(param.tid, param.pid, param.authorId, param.searchPost)
    }
}

enum class ArticleQueryKind { FULL, AUTHOR, LOOKUP }
enum class ArticleSource(@JvmField val format: String) { READ_PHP("read_php"), APP_API("app_api") }
enum class ArticlePageBasis { REQUESTED, REPORTED, LOOKUP_WINDOW, UNKNOWN_WINDOW }

/** No credentials or rendered HTML belong in this response context. */
data class ArticlePagingInfo(
    @JvmField val query: ArticleQuery,
    @JvmField val source: ArticleSource,
    @JvmField val resolvedTid: Int,
    @JvmField val requestedPage: Int,
    @JvmField val effectivePage: Int,
    @JvmField val pageSize: Int?,
    @JvmField val totalPages: Int?,
    @JvmField val totalRows: Int?,
    @JvmField val pageBasis: ArticlePageBasis,
    @JvmField val canEstimateFloorPage: Boolean,
    @JvmField val invalidMetadata: Boolean = false,
    @JvmField val reportedCurrentPage: Int? = null,
    @JvmField val owner: String? = null,
    @JvmField val generation: Long = 0,
) {
    fun withRequest(owner: String?, generation: Long) = copy(owner = owner, generation = generation)
    fun candidatePage(floor: Int): Int? {
        if (!canEstimateFloorPage || floor < 0 || pageSize == null) return null
        val candidate = floor.toLong() / pageSize + 1
        return candidate.takeIf { it <= Int.MAX_VALUE && (totalPages == null || it <= totalPages) }?.toInt()
    }

    fun sameLayout(other: ArticlePagingInfo) = source == other.source && pageSize == other.pageSize

    companion object {
        @JvmStatic fun normal(query: ArticleQuery, page: Int, data: ThreadData): ArticlePagingInfo {
            val rows = data.rowList.orEmpty()
            val tid = data.threadInfo?.tid?.takeIf { it > 0 }
                ?: rows.firstOrNull { it.tid > 0 }?.tid ?: query.tid
            val total = if (query.kind == ArticleQueryKind.LOOKUP) null else pages(data.__ROWS, 20)
            val badBounds = total != null && page > total
            return ArticlePagingInfo(query, ArticleSource.READ_PHP, tid, page, page, 20, if (badBounds) null else total,
                data.__ROWS.takeIf { it > 0 },
                when { query.kind == ArticleQueryKind.LOOKUP -> ArticlePageBasis.LOOKUP_WINDOW
                    badBounds -> ArticlePageBasis.UNKNOWN_WINDOW; else -> ArticlePageBasis.REQUESTED },
                !badBounds && floorWindow(query, page, 20, rows), invalidMetadata = badBounds)
        }

        internal fun pages(rows: Int?, size: Int?): Int? =
            if (rows == null || rows <= 0 || size == null || size <= 0) null
            else ((rows.toLong() + size - 1) / size).takeIf { it <= Int.MAX_VALUE }?.toInt()

        internal fun floorWindow(query: ArticleQuery, page: Int, size: Int?, rows: List<ThreadRowInfo>): Boolean {
            if (query.kind != ArticleQueryKind.FULL || page <= 0 || size == null || size <= 0) return false
            val start = (page.toLong() - 1) * size
            val posts = rows.filter { it.presentation?.kind != ArticleRowKind.COMMENT }
            return posts.isNotEmpty() && posts.all {
                ArticleRowPresentation.hasFloor(it) && it.lou.toLong() in start until start + size
            } && posts.map { it.lou }.distinct().size == posts.size
        }
    }
}

data class ArticleRequestKey(
    @JvmField val query: ArticleQuery,
    @JvmField val generation: Long,
    @JvmField val source: ArticleSource,
    @JvmField val pageSize: Int?,
    @JvmField val owner: String?,
    @JvmField val page: Int,
)

data class ArticleAnchor(
    @JvmField val generation: Long,
    @JvmField val page: Int,
    @JvmField val pid: Int?,
    @JvmField val floor: Int?,
) {
    fun find(rows: List<ThreadRowInfo>): Int = if (pid != null && pid > 0) {
        rows.indexOfFirst { it.pid == pid }
    } else if (floor != null) {
        rows.indexOfFirst { ArticleRowPresentation.hasFloor(it) && it.lou == floor }
    } else -1
}

/** Pure navigation calculations; all candidates still require an actual row match after loading. */
object ArticleNavigation {
    @JvmStatic fun quoteAddress(row: ThreadRowInfo): String = if (ArticleRowPresentation.hasFloor(row)) {
        "${row.pid},${row.tid},${row.lou.toLong() / 20 + 1}"
    } else row.pid.toString()

    @JvmStatic fun showAll(param: ArticleListParam, data: ThreadData?): ArticleListParam? {
        val tid = data?.pagingInfo?.resolvedTid ?: data?.threadInfo?.tid ?: param.tid
        if (tid <= 0) return null
        return ArticleListParam().apply {
            this.tid = tid
            page = 1
            title = data?.threadInfo?.subject ?: param.title
            // Keep the launch description only after checking its identity. The save path fills gaps.
            topicInfo = param.topicInfo?.takeIf {
                try { com.alibaba.fastjson.JSON.parseObject(it).getIntValue("tid") == tid }
                catch (_: RuntimeException) { false }
            }
        }
    }

    @JvmStatic fun alignmentPage(anchor: ArticleAnchor?, result: ThreadData): Int? {
        val paging = result.pagingInfo ?: return null
        if (anchor == null || anchor.find(result.rowList.orEmpty()) >= 0) return null
        return anchor.floor?.let { paging.candidatePage(it) }?.takeIf { it != paging.effectivePage }
    }

    @JvmStatic fun handoffNotice(data: ThreadData, anchor: ArticleAnchor?): String? {
        if (data.pagingInfo?.source != ArticleSource.APP_API) {
            return if (data.isContentComplete) null else "部分内容暂无法完整显示"
        }
        val prefix = if (data.isContentComplete) "已使用兼容模式显示" else "部分内容暂无法完整显示"
        return if (anchor == null || anchor.find(data.rowList.orEmpty()) < 0) "$prefix，阅读位置未能保留" else prefix
    }
}
