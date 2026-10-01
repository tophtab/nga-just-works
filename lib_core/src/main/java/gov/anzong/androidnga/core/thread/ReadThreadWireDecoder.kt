package gov.anzong.androidnga.core.thread

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.util.Fnv
import com.justwen.androidnga.core.data.thread.*

/** Decode policy only. Neither mode chooses a network source or retries a request. */
enum class ReadDecodeMode { SCOPED, LEGACY }

/** The app maps these to its existing FORMAT, CONTENT, and outer-null branches respectively. */
enum class ReadShapeProblem { ROW_MAP_OR_COUNT, INDEXED_ROW, UNREADABLE_VALUE }

sealed class ReadThreadDecodeResult {
    data class Success(val thread: ReadThreadWire) : ReadThreadDecodeResult()
    data class Failure(val problem: ReadShapeProblem, val field: String) : ReadThreadDecodeResult()
}

/**
 * Decodes an already-parsed normal-read `data` object. No raw parsing, wrapper repair,
 * display models, rendering, host selection, account state or cross-call user cache.
 */
object ReadThreadWireDecoder {
    @JvmStatic
    @JvmOverloads
    fun decode(data: JSONObject, mode: ReadDecodeMode, invalidBeanRows: Set<String> = emptySet()): ReadThreadDecodeResult = try {
        ReadThreadDecodeResult.Success(Decode(data, mode, invalidBeanRows).thread())
    } catch (failure: ShapeFailure) {
        ReadThreadDecodeResult.Failure(failure.problem, failure.field)
    }

    private class ShapeFailure(val problem: ReadShapeProblem, val field: String) : RuntimeException()

    private class Decode(private val data: JSONObject, private val mode: ReadDecodeMode, private val invalidBeanRows: Set<String>) {
        private val users = objectField(data, "__U")
        private val groups = users.value?.let { objectGetter(it, "__GROUPS") } ?: missing()
        private val userCache = linkedMapOf<String, ReadField<ReadUserWire>>()
        private val owner = objectField(data, "__T").value?.let { integer(it, "authorid") } ?: missing()

        fun thread(): ReadThreadWire {
            val rows = objectField(data, "__R")
            val count = integer(data, "__R__ROWS")
            val rowMap = rows.value
            val rowCount = count.value
            if (mode == ReadDecodeMode.SCOPED) {
                // The original getter executes before the shape condition and may throw.
                requireValid(count, "__R__ROWS")
                if (rowMap == null || !rows.valid || !count.valid || rowCount == null ||
                    rowCount < 0 || rowCount > rowMap.size) {
                    fail(ReadShapeProblem.ROW_MAP_OR_COUNT, "__R/__R__ROWS")
                }
                // Validate declared slots before other fields, as the scoped facade does.
                for (index in 0 until rowCount) if (rowMap[index.toString()] !is JSONObject) {
                    fail(ReadShapeProblem.INDEXED_ROW, "__R[$index]")
                }
            }
            // These old outer casts are deliberately distinct from the scoped shape check.
            val total = exactInt(data, "__ROWS")
            requireValue(total, "__ROWS")
            val exactCount = exactInt(data, "__R__ROWS")
            requireValue(exactCount, "__R__ROWS")
            requireValid(rows, "__R")
            requireValid(users, "__U")
            // The __T cast is outside the original optional bean-conversion catch.
            requireValid(objectField(data, "__T"), "__T")
            val topic = topic(data)
            val decodedRows = rows.value?.let { posts(it, exactCount.value!!, "__R") }
            // Retain typed table entries, but do not reject unreferenced malformed users.
            users.value?.keys?.filter { it != "__GROUPS" }?.forEach { user(it) }
            val decodedGroups = groups.value?.let { table ->
                table.keys.associateWithTo(linkedMapOf()) { groupLabel(table, it) }
            }
            val global = objectField(data, "__GLOBAL")
            val prefix = global.value?.let { stringOnly(it, "_ATTACH_BASE_VIEW") } ?: missing()
            return ReadThreadWire(
                topic = topic,
                topicAuthorId = owner,
                users = users.map(userCache.toMap()),
                groups = groups.map(decodedGroups),
                rows = rows.map(decodedRows),
                currentRowCount = exactCount,
                totalRowCount = total,
                global = global.map(if (global.value != null) Unit else null),
                attachmentBaseView = prefix,
                page = integer(data, "__PAGE"),
                rowsPerPage = integer(data, "__R__ROWS_PAGE"),
            )
        }

        private fun posts(map: JSONObject, count: Int, path: String): List<ReadPostWire> {
            val result = ArrayList<ReadPostWire>()
            for (index in 0 until count) {
                val value = map[index.toString()]
                if (value !is JSONObject) {
                    if (mode == ReadDecodeMode.SCOPED) fail(ReadShapeProblem.INDEXED_ROW, "$path[$index]")
                    continue
                }
                result.add(post(value, "$path[$index]"))
            }
            return result
        }

        private fun post(row: JSONObject, path: String): ReadPostWire {
            val scoped = JSONObject(HashMap(row))
            for (name in arrayOf("content", "subject", "alterinfo")) {
                if (!text(row, name).isSourceScalar) scoped.remove(name)
            }
            val projected = if (mode == ReadDecodeMode.SCOPED) scoped else row
            val tid = beanInteger(projected, "tid").checked("$path.tid")
            val fid = beanInteger(projected, "fid").checked("$path.fid")
            val authorId = beanInteger(projected, "authorid", "authorId").checked("$path.authorid")
            val pid = beanInteger(projected, "pid").checked("$path.pid")
            val lou = beanInteger(projected, "lou").checked("$path.lou")
            // Bean conversions precede nested comment validation in the existing facade.
            val score = beanInteger(projected, "score").checked("$path.score")
            val anonymous = beanBoolean(projected, "isanonymous", "ISANONYMOUS").checked("$path.isanonymous")
            val aurvrc = beanInteger(projected, "aurvrc").checked("$path.aurvrc")
            val muted = beanBoolean(projected, "muted", "mMuted").checked("$path.muted")
            val reputation = beanFloat(projected, "reputation", "mReputation").checked("$path.reputation")
            val attachments = beanField(projected, arrayOf("attachs")) { projected[it] as JSONObject }.checked("$path.attachs")
            val decodedAttachments = attachments.value?.mapValuesTo(linkedMapOf()) { (_, value) ->
                if (value == null) ReadField(ReadValueKind.NULL, null)
                else if (value is JSONObject || value is JSONArray) {
                    val decoded = attachment(if (value is JSONObject) value else arrayBean(value as JSONArray,
                        arrayOf("aid", "attachurl", "dscp", "ext", "name", "size", "subid", "thumb", "type", "url_utf8_org_name")))
                    ReadField(ReadValueKind.OBJECT, decoded, decoded.size.valid && decoded.subid.valid)
                }
                else ReadField(kind(value), null, false)
            }
            if (path in invalidBeanRows) fail(ReadShapeProblem.UNREADABLE_VALUE, path)
            val comments = objectField(row, "comment").checked("$path.comment")
            val decodedComments = comments.value?.let { posts(it, it.size, "$path.comment") }
            val associatedUser = if ((authorId.value ?: 0) != 0 && users.value != null) {
                // Group-container conversion happens even when this user's entry is absent.
                requireValid(groups, "__U.__GROUPS")
                user(authorId.value.toString()).also {
                    requireValid(it, "__U[user]")
                    it.value?.let { info -> requireValid(info.buffIds, "__U[user].buffs") }
                }
            } else missing()
            requireValid(owner, "__T.authorid")
            return ReadPostWire(
                tid, fid, authorId, pid, lou,
                sourceText(row, "subject"), sourceText(row, "content"), sourceText(row, "alterinfo"),
                text(row, "vote").takeIf { !it.value.isNullOrEmpty() } ?: beanText(projected, "vote"), beanText(projected, "postdate"), beanText(projected, "level"),
                // The original explicit client step overwrites the bean's fromClient alias.
                text(row, "from_client"), score,
                beanText(projected, "author"), anonymous,
                beanText(projected, "yz"), beanText(projected, "js_escap_avatar"), beanText(projected, "muteTime", "mute_time"),
                aurvrc, beanText(projected, "signature"),
                muted,
                beanText(projected, "postCount", "mPostCount"),
                reputation,
                beanText(projected, "memberGroup", "mMemberGroup"),
                attachments.map(decodedAttachments), comments.map(decodedComments),
                text(row, "17"), associatedUser,
                ReadScopedSourceWire(beanText(scoped, "content").value, beanText(scoped, "subject").value,
                    beanText(scoped, "alterinfo").value), row["lou"] != null, path,
            )
        }

        private fun user(id: String): ReadField<ReadUserWire> = userCache.getOrPut(id) {
            val table = users.value ?: return@getOrPut missing()
            val entry = objectField(table, id)
            val obj = entry.value ?: return@getOrPut entry.map(null)
            val member = text(obj, "memberid")
            val groupMap = groups.value
            val memberId = member.value
            val group = if (groupMap != null && memberId != null) {
                if (groupMap.containsKey(memberId)) groupLabel(groupMap, memberId) else missing()
            } else missing()
            val resolvedGroup = groupMap != null && memberId != null && objectGetter(groupMap, memberId).value != null
            val buffs = objectGetter(obj, "buffs")
            entry.map(ReadUserWire(
                text(obj, "username"), text(obj, "avatar"), text(obj, "yz"), text(obj, "mute_time"),
                text(obj, "rvrc"), text(obj, "signature"), text(obj, "postnum"), member,
                buffs.map(buffs.value?.keys?.toList()), group, resolvedGroup,
            ))
        }

        private fun groupLabel(table: JSONObject, id: String): ReadField<String> {
            val group = objectGetter(table, id)
            val label = group.value?.let { text(it, "0") }
            return ReadField(group.kind, label?.value, group.valid && (label?.valid ?: true))
        }

        private fun attachment(obj: JSONObject) = ReadAttachmentWire(
            beanText(obj, "aid"), beanText(obj, "url_utf8_org_name"), beanText(obj, "dscp"),
            beanInteger(obj, "size"), beanText(obj, "ext"), beanText(obj, "name"),
            beanText(obj, "thumb"), beanText(obj, "attachurl"), beanText(obj, "type"),
            beanInteger(obj, "subid"),
        )

        private fun topic(data: JSONObject): ReadField<ReadTopicWire> {
            val field = objectField(data, "__T")
            val obj = field.value ?: return field.map(null)
            val topic = ReadTopicWire(
                beanInteger(obj, "tid"), beanInteger(obj, "fid"), beanText(obj, "author"),
                beanInteger(obj, "authorId", "authorid"), beanText(obj, "lastPoster", "lastposter"),
                beanInteger(obj, "replies"), beanText(obj, "subject"), beanText(obj, "titleFont", "titlefont"),
                beanInteger(obj, "type"), beanText(obj, "topicMisc", "topic_misc"),
                beanInteger(obj, "postDate", "postdate"), beanInteger(obj, "page"), beanInteger(obj, "pid"),
                beanInteger(obj, "position"), beanBoolean(obj, "anonymity"), topicReply(obj), beanText(obj, "board"),
            )
            val valid = listOf(topic.tid, topic.fid, topic.authorId, topic.replies, topic.type,
                topic.postDate, topic.page, topic.pid, topic.position, topic.anonymity, topic.replyInfo).all { it.valid }
            return if (valid) field.map(topic) else ReadField(field.kind, null, false)
        }

        private fun topicReply(obj: JSONObject): ReadField<ReadTopicReplyWire> {
            val reply = beanField(obj, arrayOf("replyInfo")) { name ->
                when (val value = obj[name]) {
                    is JSONArray -> arrayBean(value, arrayOf("authorId", "content", "pidStr", "postDate", "subject", "tidStr"))
                    else -> value as JSONObject
                }
            }
            return reply.map(reply.value?.let {
                ReadTopicReplyWire(beanText(it, "pidStr"), beanText(it, "tidStr"), beanText(it, "authorId"),
                    beanText(it, "content"), beanText(it, "subject"), beanText(it, "postDate"))
            })
        }
    }

    // Existing JSON2 bean-array assignment order is alphabetical by bean property.
    private fun arrayBean(values: JSONArray, names: Array<String>): JSONObject = JSONObject().apply {
        for (index in names.indices) if (index < values.size) put(names[index], values[index])
    }

    private fun <T> missing(): ReadField<T> = ReadField(ReadValueKind.MISSING, null)
    private fun kind(value: Any?): ReadValueKind = when (value) {
        null -> ReadValueKind.NULL
        is String -> ReadValueKind.STRING
        is Number -> ReadValueKind.NUMBER
        is Boolean -> ReadValueKind.BOOLEAN
        is JSONObject -> ReadValueKind.OBJECT
        is JSONArray -> ReadValueKind.ARRAY
        else -> ReadValueKind.OTHER
    }
    private fun key(obj: JSONObject, names: Array<out String>) = names.firstOrNull { obj.containsKey(it) }
    private fun <T> field(obj: JSONObject, names: Array<out String>, convert: (String) -> T?): ReadField<T> {
        val name = key(obj, names) ?: return missing()
        val value = obj[name] ?: return ReadField(ReadValueKind.NULL, null)
        return try { ReadField(kind(value), convert(name)) }
        catch (_: RuntimeException) { ReadField(kind(value), null, false) }
    }
    /** JSON2's smart-name hash and actual map iteration preserve the old bean's last assignment. */
    private fun beanKey(obj: JSONObject, names: Array<out String>): String? {
        val hashes = names.map(Fnv::hashCode64LCase)
        return obj.keys.lastOrNull { Fnv.hashCode64LCase(it) in hashes }
    }
    private fun <T> beanField(obj: JSONObject, names: Array<out String>, convert: (String) -> T?): ReadField<T> {
        val hashes = names.map(Fnv::hashCode64LCase)
        var result: ReadField<T> = missing()
        for (name in obj.keys) {
            if (Fnv.hashCode64LCase(name) !in hashes) continue
            result = field(obj, arrayOf(name), convert)
            // Every assignment is converted before a later alias may overwrite it.
            if (!result.valid) return result
        }
        return result
    }
    private fun beanText(obj: JSONObject, vararg names: String) = beanField(obj, names) { obj.getString(it) }
    private fun beanInteger(obj: JSONObject, vararg names: String) = beanField(obj, names) { obj.getInteger(it) }
    private fun beanBoolean(obj: JSONObject, vararg names: String) = beanField(obj, names) { obj.getBoolean(it) }
    private fun beanFloat(obj: JSONObject, vararg names: String) = beanField(obj, names) { obj.getFloat(it) }
    private fun sourceText(obj: JSONObject, name: String): ReadField<String> {
        val raw = text(obj, name)
        return raw.copy(value = beanText(obj, name).value)
    }
    private fun text(obj: JSONObject, vararg names: String): ReadField<String> = field(obj, names) { obj.getString(it) }
    private fun integer(obj: JSONObject, vararg names: String): ReadField<Int> = field(obj, names) { obj.getInteger(it) }
    private fun boolean(obj: JSONObject, vararg names: String): ReadField<Boolean> = field(obj, names) { obj.getBoolean(it) }
    private fun float(obj: JSONObject, vararg names: String): ReadField<Float> = field(obj, names) { obj.getFloat(it) }
    private fun exactInt(obj: JSONObject, name: String): ReadField<Int> = field(obj, arrayOf(name)) { obj[it] as Int }
    private fun objectField(obj: JSONObject, name: String): ReadField<JSONObject> = field(obj, arrayOf(name)) { obj[it] as JSONObject }
    // Preserve operation-local getJSONObject coercion, distinct from direct container casts.
    private fun objectGetter(obj: JSONObject, name: String): ReadField<JSONObject> = field(obj, arrayOf(name)) { obj.getJSONObject(it) }
    private fun stringOnly(obj: JSONObject, name: String): ReadField<String> = field(obj, arrayOf(name)) { obj[it] as String }
    private fun <T, R> ReadField<T>.map(value: R?) = ReadField(kind, value, valid)
    private fun fail(problem: ReadShapeProblem, field: String): Nothing = throw ShapeFailure(problem, field)
    private fun requireValid(field: ReadField<*>, name: String) {
        if (!field.valid) fail(ReadShapeProblem.UNREADABLE_VALUE, name)
    }
    private fun requireValue(field: ReadField<*>, name: String) {
        if (!field.valid || field.value == null) fail(ReadShapeProblem.UNREADABLE_VALUE, name)
    }
    private fun <T> ReadField<T>.checked(name: String): ReadField<T> { requireValid(this, name); return this }
}
