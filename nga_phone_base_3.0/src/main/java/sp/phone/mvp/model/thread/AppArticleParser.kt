package sp.phone.mvp.model.thread

import gov.anzong.androidnga.common.util.NgaImageHost
import sp.phone.common.ForumConstants
import sp.phone.common.UserManagerImpl
import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.convert.ArticleConvertFactory
import sp.phone.mvp.model.entity.ThreadPageInfo

/**
 * Adapts Justwen ThreadInfoAppParse.kt at 2becba2acc3f6c85340424cd09bb03fa7d759db0,
 * GPL-3.0. The original source/author/row mapping is reused with explicit query, paging and
 * completeness checks. Rendering remains the app's existing ArticleConvertFactory seam.
 */
class AppArticleParser @JvmOverloads constructor(
    private val renderer: ArticleRowRenderer = ArticleRowRenderer(ArticleConvertFactory::renderRow),
    private val blacklist: ArticleBlacklist = ArticleBlacklist { UserManagerImpl.getInstance().checkBlackList(it) },
) {
    fun parse(raw: String, query: ArticleQuery, requestedPage: Int): ThreadData {
        if (!query.isValid() || requestedPage <= 0) throw ArticleFailure(ArticleFailureKind.CONTENT)
        val bean = ThreadAppBean(ArticleErrors.json(raw))
        if (bean.result.isEmpty()) throw ArticleFailure(ArticleFailureKind.EMPTY)
        val target = if (query.pid != 0) bean.result.firstOrNull { it.pid == query.pid }
            ?: throw ArticleFailure(ArticleFailureKind.CONTENT) else bean.result.first()
        val tid = target.tid
        if (tid <= 0 || (query.tid > 0 && query.tid != tid)) throw ArticleFailure(ArticleFailureKind.CONTENT)
        val identities = HashSet<Int>()
        for (result in bean.result) {
            if (result.tid != tid || result.pid < 0 ||
                (result.pid == 0 && result.lou != 0) || !identities.add(result.pid)) {
                throw ArticleFailure(ArticleFailureKind.CONTENT)
            }
            if (query.authorId != 0 && result.author?.uid != null && result.author.uid != query.authorId) {
                throw ArticleFailure(ArticleFailureKind.CONTENT)
            }
        }
        val prefix = NgaImageHost.attachmentsPrefix(bean.attachPrefix)
        val rows = bean.result.map { result ->
            ThreadRowInfo().apply {
                pid = result.pid
                this.tid = result.tid
                fid = result.fid
                lou = result.lou ?: -1
                alterinfo = result.alterinfo
                vote = result.vote
                postdate = result.postdate
                // Explicit empty content is supported; absent/unusable source needs a visible placeholder.
                val useSubject = result.content.isNullOrEmpty() && !result.subject.isNullOrEmpty()
                val source = if (useSubject) result.subject else result.content
                content = source ?: ""
                subject = if (useSubject) null else result.subject
                val sourceAvailable = !result.invalidContent && (source != null || !result.alterinfo.isNullOrEmpty())
                fromClient = result.from_client
                fromClientModel = ArticleAuthorSupport.clientModel(result.from_client)
                mapAuthor(this, result.author)
                val op = if (authorid > 0 && !isanonymous && (bean.tauthorid ?: 0) > 0)
                    authorid == bean.tauthorid else null
                presentation = ArticleRowPresentation(
                    when {
                        result.isTieTiao == true -> ArticleRowKind.COMMENT
                        result.invalidCommentMarker -> ArticleRowKind.UNKNOWN
                        else -> ArticleRowKind.POST
                    }, result.lou != null, authorid > 0 && !isanonymous,
                    scoreKnown = false, sourceAvailable = sourceAvailable, threadAuthor = op)
                renderer.render(this, prefix)
            }
        }
        val current = bean.currentPage?.takeIf { it > 0 }
        val size = bean.perPage?.takeIf { it > 0 }
        val effectivePage = current ?: requestedPage
        val totalRows = bean.vrows?.takeIf { it > 0 }
        var total = bean.totalPage?.takeIf { it > 0 }
        if (total == null && !bean.invalidTotalPage && query.kind == ArticleQueryKind.FULL) {
            total = ArticlePagingInfo.pages(totalRows, size)
        }
        val badBounds = total != null && effectivePage > total
        if (badBounds || bean.invalidCurrentPage || query.kind == ArticleQueryKind.LOOKUP) total = null
        val basis = when {
            query.kind == ArticleQueryKind.LOOKUP -> ArticlePageBasis.LOOKUP_WINDOW
            bean.invalidCurrentPage || badBounds -> ArticlePageBasis.UNKNOWN_WINDOW
            current != null -> ArticlePageBasis.REPORTED
            else -> ArticlePageBasis.REQUESTED
        }
        return ThreadData().apply {
            rawData = raw
            threadInfo = ThreadPageInfo().apply {
                this.tid = tid
                authorId = bean.tauthorid ?: 0
                subject = bean.tsubject
                author = bean.tauthor
                fid = bean.fid
                board = bean.forum_name
            }
            rowList = rows
            rowNum = rows.size
            set__ROWS(bean.vrows ?: 0)
            isContentComplete = rows.all { it.presentation.sourceAvailable }
            pagingInfo = ArticlePagingInfo(query, ArticleSource.APP_API, tid, requestedPage,
                effectivePage, size, total, totalRows, basis,
                !bean.invalidCurrentPage && !badBounds && ArticlePagingInfo.floorWindow(query, effectivePage, size, rows),
                bean.invalidCurrentPage || bean.invalidPerPage || bean.invalidTotalPage || bean.invalidVrows || badBounds,
                bean.currentPage)
        }
    }

    private fun mapAuthor(row: ThreadRowInfo, author: ThreadAppBean.Author?) {
        if (author == null) {
            row.author = "未知用户"
            return
        }
        row.authorid = author.uid ?: 0
        row.postCount = author.postnum
        row.yz = author.yz?.toString()
        row.muteTime = author.mute_time?.toString()
        row.isMuted = ForumConstants.BUFF_MUTE_IDS.any { author.buffs?.containsKey(it) == true }
        val anonymous = author.annoy?.takeIf { it.startsWith("#anony_") }
            ?: author.username?.takeIf { it.startsWith("#anony_") }
        row.isanonymous = anonymous != null
        row.author = if (anonymous != null) ArticleAuthorSupport.anonymousName(anonymous)
            else author.username ?: author.nickname ?: "未知用户"
        row.js_escap_avatar = NgaImageHost.normalizeLegacyHosts(author.avatar)
        row.isInBlackList = row.authorid > 0 && blacklist.contains(row.authorid.toString())
        row.signature = author.signature
        row.memberGroup = author.member
        row.aurvrc = author.rvrc?.toIntOrNull() ?: 0
        row.reputation = author.rvrc?.toFloatOrNull()?.takeIf { it.isFinite() }?.div(10f) ?: 0f
    }
}
