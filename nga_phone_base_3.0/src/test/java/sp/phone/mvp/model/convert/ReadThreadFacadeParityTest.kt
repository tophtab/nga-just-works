package sp.phone.mvp.model.convert

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import gov.anzong.androidnga.common.util.NgaImageHost
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sp.phone.mvp.model.thread.ArticleBlacklist
import sp.phone.mvp.model.thread.ArticleFailure
import sp.phone.mvp.model.thread.ArticleRowRenderer

/** Fixed snapshots came from the actual pre-wiring facade, not a duplicate mapper. */
class ReadThreadFacadeParityTest {
    @Before fun resetHost() { NgaImageHost.invalidate() }

    private fun snapshot(raw: String, scoped: Boolean): JSONObject {
        val output = JSONObject()
        val renders = JSONArray()
        val lookups = JSONArray()
        val renderer = ArticleRowRenderer { row, prefix ->
            val event = JSONObject()
            event["prefix"] = prefix
            event["attachmentKeys"] = row.attachs?.keys?.toList()
            event["row"] = JSON.parseObject(JSON.toJSONString(row, JSONWriter.Feature.WriteNulls))
            renders.add(event)
            row.formattedHtmlData = "fixture-render:${row.pid}"
            row.imageUrls.clear()
            row.imageUrls.add("fixture-image:${row.pid}")
        }
        val blacklist = ArticleBlacklist { uid -> lookups.add(uid); uid == "42" }
        try {
            val result = if (scoped) ArticleConvertFactory.getScopedArticleInfo(raw, renderer, blacklist)
                else ArticleConvertFactory.getArticleInfo(raw, renderer, blacklist)
            output["outcome"] = if (result == null) "null" else "success"
            if (result != null) {
                assertEquals(raw, result.rawData)
                val value = JSON.parseObject(JSON.toJSONString(result, JSONWriter.Feature.WriteNulls))
                value.remove("rawData") // Checked exactly above; avoid copying each fixture twice.
                output["data"] = value
            }
        } catch (failure: ArticleFailure) {
            output["outcome"] = failure.kind.name
        }
        output["renders"] = renders
        output["blacklistLookups"] = lookups
        return output
    }

    @Test fun actualFacadeMatchesFrozenPreWiringOutputs() {
        val inputs = JSON.parseArray(javaClass.getResource("/read-wire-parity/inputs.json")!!.readText())
        val actual = JSONObject()
        for (item in inputs) {
            val input = item as JSONObject
            for (scoped in listOf(false, true)) {
                actual["${input.getString("name")}:$scoped"] = snapshot(input.getString("raw"), scoped)
            }
        }
        val expected = JSON.parseObject(javaClass.getResource("/read-wire-parity/baseline.json")!!.readText())
        for (name in expected.keys) {
            val before = expected.getJSONObject(name)
            val after = actual.getJSONObject(name)
            if (name == "attachment-before-gap:false") {
                // Old injected renderer accepted a raw JSONObject disguised as Attachment.
                // The current facade rejects that page instead of rendering the raw value.
                assertEquals("success", before.getString("outcome"))
                assertEquals("null", after.getString("outcome"))
                assertTrue(after.getJSONArray("renders").isEmpty())
            } else if (name in setOf("wrong-entry:false", "wrong-entry:true", "bad-buffs:false", "bad-buffs:true")) {
                assertEquals(name, before.getString("outcome"), after.getString("outcome"))
                // Decode-first may avoid discarded HTML work/lookup on a rejected page.
                assertTrue(name, after.getJSONArray("renders").isEmpty())
                assertTrue(name, after.getJSONArray("blacklistLookups").isEmpty())
            } else assertEquals(name, before, after)
        }
        assertEquals(expected.keys, actual.keys)
    }
}
