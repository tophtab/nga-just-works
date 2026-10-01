package sp.phone.mvp.model.convert

import com.alibaba.fastjson2.JSONFactory
import com.alibaba.fastjson2.JSONReader
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.util.Fnv
import sp.phone.http.bean.ThreadRowInfo

/** App-only compatibility state accepted by the original display bean, outside protocol DTOs. */
internal class ReadThreadBeanFallbacks private constructor(
    private val rows: Map<String, ThreadRowInfo>,
    val invalidRows: Set<String>,
) {
    fun row(path: String): ThreadRowInfo = rows[path] ?: ThreadRowInfo()

    companion object {
        // Finite inventory of fields omitted from protocol data. Client/presentation are later
        // overwritten, but their original conversion still runs. Nested bean comments remain
        // unprocessed display fallbacks; literal protocol `comment` is decoded by core instead.
        private val fields = setOf("comments", "hotReplies", "isInBlackList", "_IsInBlackList",
            "formattedHtmlData", "mFormattedHtmlData", "imageUrls", "mImageUrlList", "presentation",
            "fromClientModel", "from_client_model")

        @JvmStatic
        fun decode(data: JSONObject, strict: Boolean): ReadThreadBeanFallbacks {
            val rows = linkedMapOf<String, ThreadRowInfo>()
            val invalid = linkedSetOf<String>()
            val reader = JSONFactory.getDefaultObjectReaderProvider().getObjectReader(ThreadRowInfo::class.java)
            fun visit(table: JSONObject, count: Int, parent: String) {
                // Project only existing numeric slots. Core still owns count/gap validation;
                // an invalid huge scoped count must not cause work before that validation.
                val indices = table.keys.mapNotNull { key ->
                    key.toIntOrNull()?.takeIf { it >= 0 && it < count && it.toString() == key }
                }.sorted()
                for (index in indices) {
                    val source = table[index.toString()] as? JSONObject ?: continue
                    val path = "$parent[$index]"
                    val ordered = if (strict) JSONObject(HashMap(source)) else source
                    val selected = JSONObject()
                    for (name in ordered.keys) {
                        val field = reader.getFieldReader(name)
                            ?: reader.getFieldReaderLCase(Fnv.hashCode64LCase(name))
                        if (field != null && field.fieldName in fields) selected[name] = ordered[name]
                    }
                    try {
                        rows[path] = selected.toJavaObject(ThreadRowInfo::class.java, JSONReader.Feature.SupportSmartMatch)
                    } catch (_: RuntimeException) {
                        invalid.add(path)
                    }
                    val comments = source["comment"] as? JSONObject
                    if (comments != null) visit(comments, comments.size, "$path.comment")
                }
            }
            val table = data["__R"] as? JSONObject
            // Match the original getter, but leave failure classification to core.
            val count = try { data.getInteger("__R__ROWS") } catch (_: RuntimeException) { null }
            if (table != null && count != null) visit(table, count, "__R")
            return ReadThreadBeanFallbacks(rows, invalid)
        }
    }
}
