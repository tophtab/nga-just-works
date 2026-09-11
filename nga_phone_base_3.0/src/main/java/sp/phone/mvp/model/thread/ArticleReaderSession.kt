package sp.phone.mvp.model.thread

import sp.phone.http.bean.ThreadData

/** Activity-local source owner. It never retains an account Cookie or performs I/O/UI effects. */
data class ArticleReaderState(
    @JvmField val query: ArticleQuery,
    @JvmField val generation: Long = 1,
    @JvmField val source: ArticleSource = ArticleSource.READ_PHP,
    @JvmField val pageSize: Int? = 20,
    @JvmField val owner: String? = null,
    @JvmField val currentPage: Int = 1,
    @JvmField val paging: ArticlePagingInfo? = null,
    @JvmField val pendingAnchor: ArticleAnchor? = null,
) {
    fun canPrefetch() = source == ArticleSource.READ_PHP && query.kind == ArticleQueryKind.FULL && paging?.totalPages != null
}

class ArticleReaderSession(query: ArticleQuery, initialPage: Int) {
    private var state = ArticleReaderState(query, currentPage = initialPage.coerceAtLeast(1))
    private var accountFingerprint: String? = null
    private var compatEnabled = false
    private var origin: String? = null
    private var handoff: ThreadData? = null

    fun isUnbound() = accountFingerprint == null
    fun state() = state
    fun origin() = origin

    /** First binding happens before any request. Subsequent changes retire every retained page. */
    fun ensureEnvironment(fingerprint: String, enabled: Boolean, owner: String?, modelOrigin: String, page: Int): Boolean {
        val initial = accountFingerprint == null
        if (!initial && fingerprint == accountFingerprint && enabled == compatEnabled) return false
        accountFingerprint = fingerprint
        compatEnabled = enabled
        if (origin == null) origin = modelOrigin
        handoff = null
        state = ArticleReaderState(state.query, if (initial) state.generation else state.generation + 1,
            owner = if (enabled) owner else null, currentPage = page.coerceAtLeast(1))
        return !initial
    }

    fun environmentMatches(fingerprint: String, enabled: Boolean) =
        accountFingerprint == fingerprint && compatEnabled == enabled

    fun key(page: Int) = ArticleRequestKey(state.query, state.generation, state.source, state.pageSize, state.owner, page)
    fun accepts(key: ArticleRequestKey) = key.query == state.query && key.generation == state.generation &&
        key.source == state.source && key.pageSize == state.pageSize && key.owner == state.owner

    fun select(page: Int) { state = state.copy(currentPage = page) }
    fun setAnchor(anchor: ArticleAnchor) {
        if (anchor.generation == state.generation) state = state.copy(pendingAnchor = anchor)
    }
    fun consumeAnchor(generation: Long, page: Int): ArticleAnchor? {
        val anchor = state.pendingAnchor ?: return null
        if (anchor.generation != generation || anchor.page != page) return null
        state = state.copy(pendingAnchor = null)
        return anchor
    }

    /** 0 rejected; 1 delivered to this page; 2 handed to a recreated page of the new generation. */
    fun adopt(key: ArticleRequestKey, data: ThreadData, foreground: Boolean): Int {
        if (!accepts(key)) return 0
        val paging = data.pagingInfo ?: return 0
        if (paging.query != key.query) return 0
        val coordinateChange = paging.effectivePage != key.page
        val layoutChange = paging.source != state.source || paging.pageSize != state.pageSize ||
            (paging.totalPages == null) != (state.paging?.totalPages == null)
        if (!foreground && (coordinateChange || layoutChange)) return 0
        val transition = coordinateChange || layoutChange
        val generation = state.generation + if (transition) 1 else 0
        val accepted = paging.withRequest(key.owner, generation)
        data.pagingInfo = accepted
        val anchor = state.pendingAnchor?.let {
            if (transition) it.copy(generation = generation, page = paging.effectivePage) else it
        }
        state = state.copy(generation = generation, source = paging.source, pageSize = paging.pageSize,
            currentPage = if (foreground) paging.effectivePage else state.currentPage,
            paging = if (foreground || state.paging == null) accepted else state.paging?.copy(
                totalPages = accepted.totalPages, totalRows = accepted.totalRows), pendingAnchor = anchor)
        if (transition) handoff = data
        return if (transition) 2 else 1
    }

    fun takeHandoff(generation: Long, page: Int): ThreadData? {
        val data = handoff ?: return null
        if (data.pagingInfo?.generation != generation || data.pagingInfo?.effectivePage != page) return null
        handoff = null
        return data
    }
}

/** The fallback and alignment budgets belong to one foreground request, never to a background job. */
class ArticleAttemptPolicy {
    private var appAttempted = false
    private var alignmentAttempted = false
    fun tryApp(failure: ArticleFailure, foreground: Boolean, enabled: Boolean, current: Boolean): Boolean {
        if (appAttempted || !failure.allowsAppFallback() || !foreground || !enabled || !current) return false
        appAttempted = true
        return true
    }
    fun tryAlignment(candidate: Int?, foreground: Boolean, current: Boolean): Boolean {
        if (alignmentAttempted || candidate == null || !foreground || !current) return false
        alignmentAttempted = true
        return true
    }
    companion object {
        @JvmStatic fun retryCookie(accountCount: Int, original: String?, next: java.util.function.Supplier<String?>): String? {
            if (accountCount < 2) return null
            return next.get()?.takeIf { it.isNotBlank() && it != original }
        }
    }
}
