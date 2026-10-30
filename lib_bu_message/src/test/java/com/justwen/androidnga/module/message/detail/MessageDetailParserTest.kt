package com.justwen.androidnga.module.message.detail

import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.core.data.MessageDetailInfo
import gov.anzong.androidnga.common.util.NgaImageHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden assertions migrated from `sp.phone.json.MessageParserGoldenTest`, plus the
 * detail-only repairs (`__P` fragment, attachment prefix) and the failure cases.
 */
class MessageDetailParserTest {

    private val golden = "{\"data\":{\"0\":{\"currentPage\":2,\"nextPage\":3,\"allUsers\":\"42 甲 43 乙\"," +
        "\"userInfo\":{\"42\":{\"username\":\"甲\",\"avatar\":\"avatar\",\"yz\":\"-1\"," +
        "\"mute_time\":\"0\",\"signature\":\"合成签名\"}}," +
        "\"allmsgs\":{\"0\":{\"content\":\"合成<br/>正文\",\"subject\":\"合成题\",\"time\":0,\"from\":\"42\"}," +
        "\"1\":{\"content\":\"第二条\",\"subject\":\"\",\"time\":0,\"from\":\"43\"}}}}}"

    @Test
    fun detailAllMessagesUsersAndRequestedFloorRemainStable() {
        val result = MessageDetailParser.parse(golden, 2)
        assertTrue(result is ApiResult.Success)
        val info = (result as ApiResult.Success<MessageDetailInfo>).data

        assertEquals("甲,乙", info.get_Alluser())
        assertEquals("合成题", info.get_Title())
        assertEquals(2, info.get__currentPage())
        assertEquals(3, info.get__nextPage())
        assertEquals(2, info.messageEntryList.size)
        assertEquals(21, info.messageEntryList[0].lou)
        assertEquals("合成\n正文", info.messageEntryList[0].content)
        assertEquals("甲", info.messageEntryList[0].author)
        assertEquals("合成签名", info.messageEntryList[0].signature)
        assertEquals("第二条", info.messageEntryList[1].content)
    }

    @Test
    fun invalidPageFragmentIsSplicedOut() {
        val raw = "{\"data\":{\"0\":{\"currentPage\":1,\"nextPage\":0,\"allUsers\":\"1 甲\"," +
            "\"userInfo\":{},\"allmsgs\":{\"0\":{\"content\":\"hi\",\"subject\":\"s\",\"time\":0,\"from\":\"1\"}}," +
            "\"__P\":{\"aid\":123},\"this_visit_rows\":[]}}}"
        val info = (MessageDetailParser.parse(raw, 1) as ApiResult.Success<MessageDetailInfo>).data
        assertEquals(1, info.messageEntryList.size)
        assertEquals("hi", info.messageEntryList[0].content)
    }

    @Test
    fun relativeAttachmentPrefixIsExpanded() {
        val raw = "{\"data\":{\"0\":{\"currentPage\":1,\"nextPage\":0,\"allUsers\":\"1 甲\"," +
            "\"userInfo\":{},\"allmsgs\":{\"0\":{\"content\":\"[img]./mon_2020/a.jpg\"," +
            "\"subject\":\"s\",\"time\":0,\"from\":\"1\"}}}}}"
        val info = (MessageDetailParser.parse(raw, 1) as ApiResult.Success<MessageDetailInfo>).data
        assertEquals(
            "[img]" + NgaImageHost.attachmentsPrefix() + "/mon_2020/a.jpg",
            info.messageEntryList[0].content,
        )
    }

    @Test
    fun businessRejectionAndReloginStayReadable() {
        val rejection = MessageDetailParser.parse("""{"error":{"0":"synthetic rejection"}}""", 1)
        assertEquals(
            "synthetic rejection",
            ((rejection as ApiResult.Failure).error as ApiError.Business).message,
        )

        val relogin = MessageDetailParser.parse("""{"error":{"0":""}}""", 1)
        assertEquals(
            "请重新登录",
            ((relogin as ApiResult.Failure).error as ApiError.Business).message,
        )
    }

    @Test
    fun nullResponseIsANetworkErrorAndInvalidJsonIsParse() {
        val nil = MessageDetailParser.parse(null, 1)
        assertEquals("网络错误", ((nil as ApiResult.Failure).error as ApiError.Business).message)
        assertTrue((MessageDetailParser.parse("{", 1) as ApiResult.Failure).error is ApiError.JsonParse)
    }
}
