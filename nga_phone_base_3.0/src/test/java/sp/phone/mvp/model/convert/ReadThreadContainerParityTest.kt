package sp.phone.mvp.model.convert

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONReader
import com.alibaba.fastjson2.JSONWriter
import sp.phone.http.bean.ThreadRowInfo
import gov.anzong.androidnga.common.util.NgaImageHost
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sp.phone.mvp.model.thread.ArticleBlacklist
import sp.phone.mvp.model.thread.ArticleFailure
import sp.phone.mvp.model.thread.ArticleRowRenderer

/** Fixed snapshots came from the actual pre-wiring facade, not a duplicate mapper. */
class ReadThreadContainerParityTest {
    @Before fun resetHost() { NgaImageHost.invalidate() }

    private fun snapshot(raw: String, scoped: Boolean): JSONObject {
        val output = JSONObject()
        val renders = JSONArray()
        val lookups = JSONArray()
        val renderer = ArticleRowRenderer { row, prefix ->
            if (row.attachs != null) ArticleConvertFactory.buildAttachmentData(row.attachs)
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
        val inputs = JSON.parseArray(javaClass.getResource("/read-wire-container-parity/inputs.json")!!.readText())
        val actual = JSONObject()
        for (item in inputs) {
            val input = item as JSONObject
            for (scoped in listOf(false, true)) {
                actual["${input.getString("name")}:$scoped"] = snapshot(input.getString("raw"), scoped)
            }
        }
        val expected = JSON.parseObject(javaClass.getResource("/read-wire-container-parity/baseline.json")!!.readText())
        for (name in expected.keys) assertEquals(name, expected[name], actual[name])
    }
}
