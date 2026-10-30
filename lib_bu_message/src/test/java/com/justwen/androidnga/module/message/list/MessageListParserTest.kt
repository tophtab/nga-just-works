package com.justwen.androidnga.module.message.list

import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.core.data.MessageListInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden assertions migrated from `sp.phone.json.MessageParserGoldenTest`, plus the
 * empty/invalid/`error.0` cases the old factory expressed through a mutable error field.
 */
class MessageListParserTest {

    private val golden = "window.script_muti_get_var_store={\"data\":{\"0\":{\"nextPage\":3," +
        "\"currentPage\":2,\"rowsPerPage\":20," +
        "\"0\":{\"mid\":11,\"posts\":2,\"subject\":\"合成消息一\",\"from_username\":\"甲\"," +
        "\"last_from_username\":\"乙\",\"time\":0,\"last_modify\":0}," +
        "\"1\":{\"mid\":12,\"posts\":1,\"subject\":\"合成消息二\",\"from_username\":\"丙\",\"time\":0}}}}"

    @Test(timeout = 3000)
    fun listWrapperCoordinatesAndOrderRemainStable() {
        val result = MessageListParser.parse(golden)
        assertTrue(result is ApiResult.Success)
        val info = (result as ApiResult.Success<MessageListInfo>).data

        assertEquals(2, info.get__currentPage())
        assertEquals(3, info.get__nextPage())
        assertEquals(20, info.get__rowsPerPage())
        assertEquals(2, info.messageEntryList.size)
        assertEquals(11, info.messageEntryList[0].mid)
        assertEquals("合成消息二", info.messageEntryList[1].subject)
        assertEquals("乙", info.messageEntryList[0].last_from_username)
    }

    @Test
    fun businessRejectionRetainsMessageAndNoResult() {
        val result = MessageListParser.parse("""{"error":{"0":"synthetic rejection"}}""")
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.Business)
        assertEquals("synthetic rejection", (error as ApiError.Business).message)
    }

    @Test
    fun emptyErrorTextFallsBackToRelogin() {
        val result = MessageListParser.parse("""{"error":{"0":""}}""")
        assertEquals("请重新登录", ((result as ApiResult.Failure).error as ApiError.Business).message)
    }

    @Test
    fun nullAndEmptyResponsesAreUnexpected() {
        assertTrue((MessageListParser.parse(null) as ApiResult.Failure).error is ApiError.Unexpected)
        assertTrue((MessageListParser.parse("") as ApiResult.Failure).error is ApiError.Unexpected)
    }

    @Test
    fun invalidJsonIsClassifiedAsJsonParse() {
        val result = MessageListParser.parse("{")
        assertTrue((result as ApiResult.Failure).error is ApiError.JsonParse)
    }
}
