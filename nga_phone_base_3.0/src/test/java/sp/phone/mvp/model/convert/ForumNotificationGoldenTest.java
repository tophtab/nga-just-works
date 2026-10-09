package sp.phone.mvp.model.convert;

import org.junit.Test;
import java.util.List;
import sp.phone.mvp.model.entity.NotificationInfo;
import sp.phone.mvp.model.entity.RecentReplyInfo;
import static org.junit.Assert.*;

public class ForumNotificationGoldenTest {
    @Test public void wrapperIndexesOrderAndUnreadKeepTheirOldMeaning() {
        String raw = "window.script_muti_get_var_store={\"data\":{\"0\":{\"unread\":2,\"0\":["
            + "{\"0\":1,\"1\":42,\"2\":\"合成甲\",\"5\":\"题一\",\"6\":120002,\"7\":50012,\"9\":1700000000},"
            + "{\"0\":2,\"1\":43,\"2\":\"合成乙\",\"5\":\"题二\",\"6\":120003,\"8\":50013,\"9\":1700000001}],"
            + "\"1\":[{\"0\":3,\"2\":\"合成消息\"}]}}}";
        assertNotifications(raw, true);
    }

    @Test public void unquotedNumericKeysKeepRepliesMessagesAndReadState() {
        // Preserve the legacy JS object spelling: quoting numeric keys hides this regression.
        String payload = "{\"data\":{0:{\"unread\":2,0:["
            + "{0:1,1:42,2:\"合成甲\",5:\"题一\",6:120002,7:50012,9:1700000000},"
            + "{0:2,1:43,2:\"合成乙\",5:\"题二\",6:120003,8:50013,9:1700000001}],"
            + "1:[{0:3,2:\"合成消息\"}]}}}";
        assertNotNull(com.alibaba.fastjson.JSONObject.parseObject(payload)
            .getJSONObject("data").getJSONObject("0"));
        assertNotNull(ForumNotificationFactory.decodeNotificationData(payload));
        assertNotifications("window.script_muti_get_var_store=" + payload, true);
        assertNotifications(payload.replace("\"unread\":2", "\"unread\":0"), false);
    }

    private static void assertNotifications(String raw, boolean unread) {
        List<NotificationInfo> values = ForumNotificationFactory.buildNotificationList(raw);
        assertEquals(3, values.size());
        RecentReplyInfo first = (RecentReplyInfo) values.get(0);
        assertEquals("50012", first.getPidStr()); assertEquals("120002", first.getTidStr());
        assertEquals("42", first.getUserId()); assertEquals("1700000000", first.getTimeStamp());
        assertEquals("题一", first.getTitle()); assertEquals("合成甲", first.getUserName());
        assertEquals("50013", ((RecentReplyInfo) values.get(1)).getPidStr());
        assertEquals("合成消息", values.get(2).getUserName());
        for (NotificationInfo value : values) assertEquals(unread, value.isUnread());
        List<RecentReplyInfo> replies = ForumNotificationFactory.buildRecentReplyList(raw);
        assertEquals(2, replies.size());
        assertEquals("50012", replies.get(0).getPidStr());
        assertEquals("50013", replies.get(1).getPidStr());
        for (RecentReplyInfo reply : replies) assertEquals(unread, reply.isUnread());
    }
    @Test public void emptyArraysRemainEmptyLists() {
        assertTrue(ForumNotificationFactory.buildRecentReplyList("{\"data\":{\"0\":{\"unread\":0,\"0\":[]}}}").isEmpty());
    }
    @Test public void malformedAndBusinessErrorsStayAtTheExistingCatchBoundary() {
        assertThrows(RuntimeException.class, () -> ForumNotificationFactory.decodeNotificationData("{"));
        assertThrows(RuntimeException.class, () -> ForumNotificationFactory.decodeNotificationData("{}{}"));
        assertThrows(RuntimeException.class, () -> ForumNotificationFactory.decodeNotificationData("{\"error\":{\"0\":\"denied\"}}"));
        assertNull(ForumNotificationFactory.decodeNotificationData("{\"data\":{}}"));
    }
}
