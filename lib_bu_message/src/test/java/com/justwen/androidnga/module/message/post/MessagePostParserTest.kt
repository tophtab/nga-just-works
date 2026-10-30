package com.justwen.androidnga.module.message.post

import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePostParserTest {

    @Test
    fun wrapperAndSuccessTagsRetainDataFirstSelection() {
        for (tag in listOf("发送完毕 ...", " @提醒每24小时不能超过50个", "操作成功")) {
            val raw = "window.script_muti_get_var_store={\"data\":{\"0\":\"$tag\"}," +
                "\"error\":{\"0\":\"denied\"}}/*error fill content"
            val result = MessagePostParser.parse(raw)
            assertEquals(tag, (result as ApiResult.Success<String>).data)
        }
    }

    @Test
    fun errorEnvelopeCarriesItsMessage() {
        val result = MessagePostParser.parse("""{"error":{"0":"denied"}}""")
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.Business)
        assertEquals("denied", (error as ApiError.Business).message)
    }

    @Test
    fun missingOrNonSuccessDataIsGenericFailure() {
        assertEquals(
            "发送失败！",
            ((MessagePostParser.parse("""{"data":{},"error":{"0":"denied"}}""") as ApiResult.Failure)
                .error as ApiError.Business).message,
        )
        assertEquals(
            "发送失败！",
            ((MessagePostParser.parse("{}") as ApiResult.Failure).error as ApiError.Business).message,
        )
        assertEquals(
            "发送失败！",
            ((MessagePostParser.parse("""{"error":{"0":""}}""") as ApiResult.Failure)
                .error as ApiError.Business).message,
        )
    }

    @Test
    fun invalidJsonIsClassifiedAsJsonParseInsteadOfThrowing() {
        val result = MessagePostParser.parse("{")
        assertTrue((result as ApiResult.Failure).error is ApiError.JsonParse)
    }
}
