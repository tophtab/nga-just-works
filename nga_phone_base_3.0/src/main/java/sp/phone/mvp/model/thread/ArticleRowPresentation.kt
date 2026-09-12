package sp.phone.mvp.model.thread

import sp.phone.http.bean.ThreadRowInfo

/** Explicit App/known-comment facts override the legacy sparse-row heuristic. */
enum class ArticleRowKind { POST, COMMENT, UNKNOWN }

data class ArticleRowPresentation(
    @JvmField val kind: ArticleRowKind,
    @JvmField val floorKnown: Boolean,
    @JvmField val userKnown: Boolean,
    @JvmField val scoreKnown: Boolean,
    @JvmField val sourceAvailable: Boolean,
    @JvmField val threadAuthor: Boolean?,
) {
    companion object {
        @JvmStatic fun hasFloor(row: ThreadRowInfo) =
            row.presentation?.let { it.floorKnown && it.kind != ArticleRowKind.COMMENT } ?: (row.lou >= 0)
        @JvmStatic fun hasUser(row: ThreadRowInfo) = row.authorid > 0 && !row.isanonymous &&
            (row.presentation?.userKnown != false)
        @JvmStatic fun hasSource(row: ThreadRowInfo) = row.presentation?.sourceAvailable != false && row.content != null
        @JvmStatic fun canReply(row: ThreadRowInfo) = hasSource(row) && row.tid > 0 &&
            (row.pid > 0 || hasFloor(row) && row.lou == 0)
        @JvmStatic fun isPost(row: ThreadRowInfo) = row.presentation?.kind?.let { it == ArticleRowKind.POST } ?: true
        @JvmStatic fun isThreadAuthor(row: ThreadRowInfo, oldOwnerName: String?): Boolean {
            if (row.isanonymous || row.authorid <= 0) return false
            row.presentation?.threadAuthor?.let { return it }
            return row.presentation?.userKnown != false && !oldOwnerName.isNullOrBlank() && oldOwnerName != "未知用户" &&
                !row.author.isNullOrBlank() && row.author == oldOwnerName
        }
    }
}

fun interface ArticleRowRenderer { fun render(row: ThreadRowInfo, attachmentsPrefix: String) }
fun interface ArticleBlacklist { fun contains(uid: String): Boolean }

/** Justwen 2becba2a's added reply dialect, normalized without interpreting unrelated HTML. */
object ArticleSourceText {
    private val replyHeader = Regex("<b>(Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\][^<]*?)</b>")
    @JvmStatic fun normalizeReplyHeader(source: String): String =
        replyHeader.replace(source) { "[b]${it.groupValues[1]}[/b]" }

    /** Display input only: never overwrite the row's editable source with a notice or rendered HTML. */
    @JvmStatic fun renderBody(row: ThreadRowInfo): String? {
        val source = row.content?.let(::normalizeReplyHeader)
        return if (row.presentation?.sourceAvailable == false) {
            "此条内容暂无法完整显示，可尝试使用内置浏览器打开。<br/>" + source.orEmpty()
        } else source
    }
}

/** Quote attribution keeps readable source without inventing a navigable user identity. */
object ArticleQuote {
    @JvmStatic fun authorMarkup(row: ThreadRowInfo): String {
        val name = row.author?.takeIf { it.isNotBlank() } ?: "未知用户"
        val uid = if (row.isanonymous) -1 else row.authorid.takeIf { ArticleRowPresentation.hasUser(row) }
        val label = if (uid == null) name else "[uid=$uid]$name[/uid]"
        return if (row.isanonymous && ArticleRowPresentation.hasFloor(row)) "$label[color=gray](${row.lou}楼)[/color]" else label
    }
    @JvmStatic fun mention(row: ThreadRowInfo): String? = row.author?.takeIf {
        it.isNotBlank() && (row.isanonymous || ArticleRowPresentation.hasUser(row))
    }
}
