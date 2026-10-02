package sp.phone.mvp.model.thread

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject

/**
 * Adapted from Justwen/NGA-CLIENT-VER-OPEN-SOURCE, GPL-3.0,
 * 2becba2acc3f6c85340424cd09bb03fa7d759db0, ThreadAppBean.kt.
 * Field names and the consumed row/author projection are retained. Nullable numeric fields expose
 * absence; DOM extraction consumes observed attachments/comments/support counts and leaves
 * hot_post/comment_to_id/html_head_extra (and other extensions) opaque in ThreadData.rawData.
 * Regular classes avoid emitting source bodies through generated data-class toString().
 */
internal class ThreadAppBean(root: JSONObject) {
    val attachPrefix = root.text("attachPrefix")
    val currentPage = root.integer("currentPage")
    val perPage = root.integer("perPage")
    val totalPage = root.integer("totalPage")
    val vrows = root.integer("vrows")
    val invalidCurrentPage = root.badPositive("currentPage")
    val invalidPerPage = root.badPositive("perPage")
    val invalidTotalPage = root.badPositive("totalPage")
    val invalidVrows = root.badPositive("vrows")
    val fid = root.integer("fid") ?: 0
    val forum_name = root.text("forum_name")
    val tauthor = root.text("tauthor")
    val tauthorid = root.integer("tauthorid")
    val tsubject = root.text("tsubject")
    val result: List<Result> = (root["result"] as? JSONArray
        ?: throw ArticleFailure(ArticleFailureKind.FORMAT)).map {
        Result(it as? JSONObject ?: throw ArticleFailure(ArticleFailureKind.CONTENT))
    }

    class Result(row: JSONObject, nested: Boolean = false) {
        val tid = row.integer("tid")
        val pid = row.integer("pid")
        val lou = row.integer("lou")?.takeIf { it >= 0 }
        val fid = row.integer("fid") ?: 0
        val alterinfo = row.text("alterinfo")
        val content = row.text("content")
        val invalidContent = row["content"] != null && content == null
        val subject = row.text("subject")
        val from_client = row.text("from_client")
        val postdate = row.text("postdate")
        val postdatetimestamp = row.integer("postdatetimestamp")
        val vote = row.text("vote")
        // Default score is the support count; vote_bad does not contribute to it.
        val voteGood = row.integer("vote_good")?.takeIf { it >= 0 }
        val attachments = (row["attches"] as? JSONArray)?.map { (it as? JSONObject)?.let(::AttachedFile) }
        val invalidAttachments = row["attches"] != null && attachments == null ||
            attachments?.any { it == null || it.attachurl == null || it.invalidThumb } == true
        // The default comment UI has one level. Bound projection there and signal deeper content.
        val comments = if (nested) null else (row["comments"] as? JSONArray)?.map {
            (it as? JSONObject)?.let { child -> Result(child, nested = true) }
        }
        val invalidComments = if (nested) row["comments"] != null &&
            (row["comments"] as? JSONArray)?.isEmpty() != true
        else row["comments"] != null && comments == null || comments?.any { it == null } == true
        val author = (row["author"] as? JSONObject)?.let(::Author)
    }

    class AttachedFile(attachment: JSONObject) {
        val attachurl = attachment.text("attachurl")?.takeIf { it.isNotBlank() }
        val thumb = attachment.text("thumb")
        val invalidThumb = attachment["thumb"] != null && thumb == null
    }

    class Author(author: JSONObject) {
        val uid = author.integer("uid")
        val username = author.text("username")
        val nickname = author.text("nickname")
        val annoy = author.text("annoy")
        val avatar = author.text("avatar")
        val yz = author.integer("yz")
        val mute_time = author.integer("mute_time")
        val rvrc = author.numberText("rvrc")
        val signature = author.text("signature")
        val postnum = author.numberText("postnum")
        val member = author.text("member")
        val buffs = author["buffs"] as? JSONObject
    }
}

internal fun JSONObject.text(key: String) = this[key] as? String
internal fun JSONObject.numberText(key: String): String? = when (val value = this[key]) {
    is String -> value
    is Number -> value.toString()
    else -> null
}
internal fun JSONObject.integer(key: String): Int? = when (val value = this[key]) {
    is Byte, is Short, is Int, is Long, is java.math.BigInteger -> value.toString().toIntOrNull()
    is String -> value.takeIf { it.matches(Regex("-?[0-9]+")) }?.toIntOrNull()
    else -> null
}
internal fun JSONObject.badPositive(key: String) = this[key] != null && (integer(key)?.let { it > 0 } != true)
