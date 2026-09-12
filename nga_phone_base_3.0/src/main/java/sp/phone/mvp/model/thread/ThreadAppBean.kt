package sp.phone.mvp.model.thread

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject

/**
 * Adapted from Justwen/NGA-CLIENT-VER-OPEN-SOURCE, GPL-3.0,
 * 2becba2acc3f6c85340424cd09bb03fa7d759db0, ThreadAppBean.kt.
 * Field names and the consumed row/author projection are retained. Nullable numeric fields expose
 * absence; DOM extraction deliberately leaves unused attches/hot_post/comment_to_id/html_head_extra
 * (and other extensions) opaque in ThreadData.rawData. No reflective sidecar protocol is introduced.
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

    class Result(row: JSONObject) {
        val tid = row.integer("tid") ?: throw ArticleFailure(ArticleFailureKind.CONTENT)
        val pid = row.integer("pid") ?: throw ArticleFailure(ArticleFailureKind.CONTENT)
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
        val isTieTiao = row["isTieTiao"] as? Boolean
        val invalidCommentMarker = row["isTieTiao"] != null && isTieTiao == null
        val author = (row["author"] as? JSONObject)?.let(::Author)
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
