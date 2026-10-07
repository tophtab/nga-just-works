package sp.phone.mvp.model.thread

import gov.anzong.androidnga.common.util.NgaImageHost
import sp.phone.common.ForumConstants
import sp.phone.common.UserManagerImpl
import sp.phone.http.bean.Attachment
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
        val tid = target.tid ?: throw ArticleFailure(ArticleFailureKind.CONTENT)
        if (tid <= 0 || (query.tid > 0 && query.tid != tid)) throw ArticleFailure(ArticleFailureKind.CONTENT)
        val identities = HashSet<Int>()
        for (result in bean.result) {
            if (result.tid != tid || result.pid == null || result.pid < 0 ||
                (result.pid == 0 && result.lou != 0) || !identities.add(result.pid)) {
                throw ArticleFailure(ArticleFailureKind.CONTENT)
            }
            if (query.authorId != 0 && result.author?.uid != null && result.author.uid != query.authorId) {
                throw ArticleFailure(ArticleFailureKind.CONTENT)
            }
        }
        val prefix = NgaImageHost.attachmentsPrefix(bean.attachPrefix)
        val rows = bean.result.map { result ->
            post(result, bean.tauthorid, tid).also { row ->
                // App responses may carry the main-post title only in page-level tsubject.
                // Reply lookup windows may report floor zero for a positive-PID reply.
                // Keep subject-as-body fallback and source validity owned by post().
                if (result.pid == 0 && result.lou == 0 && result.subject.isNullOrEmpty()
                    && !bean.tsubject.isNullOrEmpty()) {
                    row.subject = bean.tsubject
                }
                renderer.render(row, prefix)
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
            isContentComplete = complete(rows)
            pagingInfo = ArticlePagingInfo(query, ArticleSource.APP_API, tid, requestedPage,
                effectivePage, size, total, totalRows, basis,
                !bean.invalidCurrentPage && !badBounds && ArticlePagingInfo.floorWindow(query, effectivePage, size, rows),
                bean.invalidCurrentPage || bean.invalidPerPage || bean.invalidTotalPage || bean.invalidVrows || badBounds,
                bean.currentPage)
        }
    }

    private fun post(result: ThreadAppBean.Result, owner: Int?, topic: Int,
                     comment: Boolean = false, uniqueIdentity: Boolean = true): ThreadRowInfo = ThreadRowInfo().apply {
        pid = result.pid ?: -1
        tid = result.tid ?: 0
        fid = result.fid
        lou = result.lou ?: -1
        alterinfo = result.alterinfo
        vote = result.vote
        score = result.voteGood ?: 0
        postdate = result.postdate
        // Explicit empty content is supported; absent/unusable source needs a visible placeholder.
        val useSubject = result.content.isNullOrEmpty() && !result.subject.isNullOrEmpty()
        val source = if (useSubject) result.subject else result.content
        content = source ?: ""
        subject = if (useSubject) null else result.subject
        val identityAvailable = !comment || tid == topic && pid > 0 && uniqueIdentity
        val sourceAvailable = identityAvailable && !result.invalidContent &&
            (source != null || !result.alterinfo.isNullOrEmpty())
        fromClient = result.from_client
        fromClientModel = ArticleAuthorSupport.clientModel(result.from_client)
        mapAuthor(this, result.author)
        val op = if (authorid > 0 && !isanonymous && (owner ?: 0) > 0) authorid == owner else null
        attachs = result.attachments?.mapIndexedNotNull { index, file ->
            file?.attachurl?.let { url -> index.toString() to Attachment().apply {
                attachurl = url
                thumb = file.thumb
            } }
        }?.toMap(LinkedHashMap())
        val childIdentities = HashSet<Int>()
        comments = result.comments?.mapNotNull { child -> child?.let {
            post(it, owner, topic, comment = true,
                uniqueIdentity = it.pid != null && childIdentities.add(it.pid))
        } }
        // isTieTiao marks a parent with comments, not the parent's row kind.
        presentation = ArticleRowPresentation(
            if (comment) ArticleRowKind.COMMENT else ArticleRowKind.POST,
            result.lou != null, authorid > 0 && !isanonymous,
            scoreKnown = result.voteGood != null, sourceAvailable = sourceAvailable, threadAuthor = op,
            supplementalContentAvailable = !result.invalidAttachments && !result.invalidComments &&
                !(comment && !result.attachments.isNullOrEmpty()))
    }

    private fun complete(rows: List<ThreadRowInfo>): Boolean = rows.all {
        it.presentation.sourceAvailable && it.presentation.supplementalContentAvailable &&
            (it.comments == null || complete(it.comments))
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
