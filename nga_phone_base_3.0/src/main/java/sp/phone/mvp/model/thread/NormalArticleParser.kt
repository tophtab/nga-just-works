package sp.phone.mvp.model.thread

import sp.phone.common.UserManagerImpl
import sp.phone.http.bean.ThreadData
import sp.phone.mvp.model.convert.ArticleConvertFactory

/** Scoped normal reads reuse the existing wrapper, user, attachment, comment and WP pipeline. */
class NormalArticleParser @JvmOverloads constructor(
    private val renderer: ArticleRowRenderer = ArticleRowRenderer(ArticleConvertFactory::renderRow),
    private val blacklist: ArticleBlacklist = ArticleBlacklist { UserManagerImpl.getInstance().checkBlackList(it) },
) {
    fun parse(raw: String, query: ArticleQuery, page: Int): ThreadData {
        val data = ArticleConvertFactory.getScopedArticleInfo(raw, renderer, blacklist)
            ?: throw ArticleFailure(ArticleFailureKind.FORMAT)
        if (data.rowList.isNullOrEmpty()) throw ArticleFailure(ArticleFailureKind.EMPTY)
        val target = if (query.pid > 0) data.rowList.firstOrNull { it.pid == query.pid }
            ?: throw ArticleFailure(ArticleFailureKind.CONTENT) else data.rowList.first()
        val resolvedTid = target.tid.takeIf { it > 0 } ?: query.tid
        if (!query.isValid() || resolvedTid <= 0 || (query.tid > 0 && query.tid != resolvedTid) ||
            (data.threadInfo != null && data.threadInfo.tid != resolvedTid)) throw ArticleFailure(ArticleFailureKind.CONTENT)
        for (row in data.rowList) {
            if (row.tid == 0) row.tid = resolvedTid // The existing normal query supplies this missing context.
            if (row.tid != resolvedTid || row.pid < 0 || (row.pid == 0 && row.lou != 0) ||
                (query.authorId != 0 && row.authorid != 0 && row.authorid != query.authorId)) {
                throw ArticleFailure(ArticleFailureKind.CONTENT)
            }
        }
        data.pagingInfo = ArticlePagingInfo.normal(query, page, data)
        return data
    }
}
