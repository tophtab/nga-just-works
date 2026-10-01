package sp.phone.json;

import com.justwen.androidnga.module.message.MessageConvertFactory;
import com.justwen.androidnga.core.data.MessageListInfo;
import com.justwen.androidnga.core.data.MessageDetailInfo;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the active module parser through the app test runtime; all text is synthetic. */
public class MessageParserGoldenTest {
    @Test(timeout = 3000) public void listWrapperCoordinatesAndOrderRemainStable() {
        MessageListInfo result = new MessageConvertFactory().getMessageListInfo(
            "window.script_muti_get_var_store={\"data\":{\"0\":{\"nextPage\":3,\"currentPage\":2,\"rowsPerPage\":20,"
            + "\"0\":{\"mid\":11,\"posts\":2,\"subject\":\"合成消息一\",\"from_username\":\"甲\",\"last_from_username\":\"乙\",\"time\":0,\"last_modify\":0},"
            + "\"1\":{\"mid\":12,\"posts\":1,\"subject\":\"合成消息二\",\"from_username\":\"丙\",\"time\":0}}}}");
        assertNotNull(result); assertEquals(2, result.get__currentPage());
        assertEquals(3, result.get__nextPage()); assertEquals(20, result.get__rowsPerPage());
        assertEquals(2, result.getMessageEntryList().size());
        assertEquals(11, result.getMessageEntryList().get(0).getMid());
        assertEquals("合成消息二", result.getMessageEntryList().get(1).getSubject());
        assertEquals("乙", result.getMessageEntryList().get(0).getLast_from_username());
    }

    @Test public void detailAllMessagesUsersAndRequestedFloorRemainStable() {
        MessageDetailInfo result = new MessageConvertFactory().parseJsonThreadPage(
            "{\"data\":{\"0\":{\"currentPage\":2,\"nextPage\":3,\"allUsers\":\"42 甲 43 乙\","
            + "\"userInfo\":{\"42\":{\"username\":\"甲\",\"avatar\":\"avatar\",\"yz\":\"-1\",\"mute_time\":\"0\",\"signature\":\"合成签名\"}},"
            + "\"allmsgs\":{\"0\":{\"content\":\"合成<br/>正文\",\"subject\":\"合成题\",\"time\":0,\"from\":\"42\"},"
            + "\"1\":{\"content\":\"第二条\",\"subject\":\"\",\"time\":0,\"from\":\"43\"}}}}}", 2);
        assertNotNull(result); assertEquals("甲,乙", result.get_Alluser());
        assertEquals("合成题", result.get_Title()); assertEquals(2, result.get__currentPage());
        assertEquals(3, result.get__nextPage()); assertEquals(2, result.getMessageEntryList().size());
        assertEquals(21, result.getMessageEntryList().get(0).getLou());
        assertEquals("合成\n正文", result.getMessageEntryList().get(0).getContent());
        assertEquals("甲", result.getMessageEntryList().get(0).getAuthor());
        assertEquals("合成签名", result.getMessageEntryList().get(0).getSignature());
        assertEquals("第二条", result.getMessageEntryList().get(1).getContent());
    }

    @Test public void businessRejectionRetainsMessageAndNoResult() {
        MessageConvertFactory parser = new MessageConvertFactory();
        assertNull(parser.getMessageListInfo("{\"error\":{\"0\":\"synthetic rejection\"}}"));
        assertEquals("synthetic rejection", parser.getErrorMsg());
        assertNull(parser.getMessageListInfo(""));
    }
}
