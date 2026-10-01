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
class ReadThreadFallbackParityTest {
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

    @Test fun blacklistBeanNamesSurviveBothFacadeModes() {
        for (name in listOf("_IsInBlackList", "_isInBlackList", "isInBlackList")) {
            val row = JSONObject().apply { put("content", "body"); put(name, true) }
            val oldBean = row.toJavaObject(ThreadRowInfo::class.java, JSONReader.Feature.SupportSmartMatch)
            assertTrue(name, oldBean.isInBlackList)
            val data = JSON.parseObject("""{"__ROWS":1,"__R__ROWS":1,"__R":{}}""")
            data.getJSONObject("__R")["0"] = row
            val raw = JSONObject().apply { put("data", data) }.toJSONString()
            for (strict in listOf(false, true)) {
                val renderer = ArticleRowRenderer { _, _ -> }
                val blacklist = ArticleBlacklist { false }
                val result = if (strict) ArticleConvertFactory.getScopedArticleInfo(raw, renderer, blacklist)
                    else ArticleConvertFactory.getArticleInfo(raw, renderer, blacklist)
                assertNotNull("$name strict=$strict", result)
                assertEquals("$name strict=$strict", oldBean.isInBlackList, result.rowList.single().isInBlackList)
            }
        }
    }

    @Test fun coercibleCountsRetainBeanFallbacksAndSparseTraversalIsBounded() {
        for (count in listOf<Any>("1", 1L, 1)) {
            val data = JSON.parseObject("""{"__R":{"0":{"hotReplies":["fallback"],"isInBlackList":true}}}""")
            data["__R__ROWS"] = count
            for (strict in listOf(false, true)) {
                val fallback = ReadThreadBeanFallbacks.decode(data, strict).row("__R[0]")
                assertEquals(listOf("fallback"), fallback.hotReplies)
                assertTrue(fallback.isInBlackList)
            }
        }
        val sparse = JSON.parseObject("""{"__R__ROWS":2147483647,"__R":{"2147483646":{"hotReplies":["last"]},"01":{"hotReplies":["ignored"]}}}""")
        val fallbacks = ReadThreadBeanFallbacks.decode(sparse, true)
        assertEquals(listOf("last"), fallbacks.row("__R[2147483646]").hotReplies)
        assertNull(fallbacks.row("__R[1]").hotReplies)
    }

    @Test fun actualFacadeMatchesFrozenPreWiringOutputs() {
        val inputs = JSON.parseArray(javaClass.getResource("/read-wire-fallback-parity/inputs.json")!!.readText())
        val actual = JSONObject()
        for (item in inputs) {
            val input = item as JSONObject
            for (scoped in listOf(false, true)) {
                actual["${input.getString("name")}:$scoped"] = snapshot(input.getString("raw"), scoped)
            }
        }
        val expected = JSON.parseObject(javaClass.getResource("/read-wire-fallback-parity/baseline.json")!!.readText())
        val rawAttachmentArtifacts = setOf("attachment-size:false", "attachment-size:true", "attachment-subid:false", "attachment-subid:true")
        for (name in expected.keys) {
            if (name in rawAttachmentArtifacts) {
                // Matched separately against actual old production-rendering outcomes in ContainerParityTest.
                assertEquals("success", expected.getJSONObject(name).getString("outcome"))
                assertEquals("null", actual.getJSONObject(name).getString("outcome"))
                assertTrue(actual.getJSONObject(name).getJSONArray("renders").isEmpty())
            } else assertEquals(name, expected[name], actual[name])
        }
    }
}
