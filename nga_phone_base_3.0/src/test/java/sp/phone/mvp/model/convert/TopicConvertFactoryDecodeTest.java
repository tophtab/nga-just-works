package sp.phone.mvp.model.convert;

import org.junit.Test;
import sp.phone.http.bean.TopicListBean;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class TopicConvertFactoryDecodeTest {
    @Test public void allSixArchivedJdataShapesRetainForumAndTopicFields() throws Exception {
        for (String name : new String[]{"escaped_quote", "hex_spaced_field", "hex_unknown_first",
                "hex_unknown_last", "nested_hex", "valid_control"}) {
            String raw;
            try (InputStream input = getClass().getResourceAsStream("/json-topic-jdata/" + name + ".json")) {
                assertNotNull(name, input);
                raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            TopicListBean bean = TopicConvertFactory.decodeTopicList(raw);
            assertNotNull(name, bean);
            assertEquals(name, "synthetic", bean.getData().get__F().name);
            assertEquals(name, 1, bean.getData().get__F().getFid());
            assertEquals(name, 1, bean.getTime());
            assertTrue(name, bean.getData().get__T().isEmpty());
        }
    }

    @Test public void nestedBeanAliasesAndStringifiedParentKeepTheOldValues() {
        TopicListBean bean = TopicConvertFactory.decodeTopicList("{\"encode\":\"GBK\",\"time\":1700000000,\"data\":{"
            + "\"__CU\":{\"uid\":42,\"group_bit\":7,\"admincheck\":\"none\",\"rvrc\":15},"
            + "\"__F\":{\"fid\":-7,\"name\":\"合成版块\",\"sub_forums\":{\"1\":{\"0\":12,\"1\":\"子版\"}}},"
            + "\"__ROWS\":83,\"__T__ROWS\":1,\"__T__ROWS_PAGE\":20,\"__R__ROWS_PAGE\":30,"
            + "\"__T\":{\"0\":{\"tid\":120002,\"fid\":-7,\"authorid\":42,\"author\":\"甲\","
            + "\"lastposter\":\"乙\",\"subject\":\"合成题\",\"titlefont\":\"b red\",\"topic_misc\":\"0,1\","
            + "\"postdate\":1700000000,\"replies\":83,\"type\":16,\"parent\":{\"2\":\"版面镜像\"},"
            + "\"topic_misc_var\":{\"3\":7},\"__P\":{\"pid\":50012,\"authorid\":43,\"content\":\"合成回复\",\"postdate\":1700000001}}}}}");
        assertEquals("GBK", bean.getEncode());
        assertEquals(7, bean.getData().get__CU().getGroup_bit());
        assertEquals("83", bean.getData().get__ROWS());
        assertEquals(1, bean.getData().get__T__ROWS());
        assertEquals(20, bean.getData().get__T__ROWS_PAGE());
        assertEquals(30, bean.getData().get__R__ROWS_PAGE());
        assertTrue(bean.getData().get__F().getSub_forums().contains("子版"));
        TopicListBean.DataBean.TBean row = bean.getData().get__T().get("0");
        assertEquals("42", row.getAuthorid()); assertEquals("乙", row.getLastposter());
        assertEquals("b red", row.getTitlefont()); assertEquals("0,1", row.getTopic_misc());
        assertEquals(1700000000, row.getPostdate()); assertEquals(83, row.getReplies());
        assertEquals(16, row.getType()); assertTrue(row.parent.contains("版面镜像"));
        assertEquals(50012, row.get__P().getPid()); assertEquals("43", row.get__P().getAuthorid());
        assertEquals("合成回复", row.get__P().getContent());
    }

    @Test public void missingEnvelopeIsNotReplacedWithAValidEmptyDataBean() {
        assertNull(TopicConvertFactory.decodeTopicList("null"));
        assertNull(TopicConvertFactory.decodeTopicList("{\"error\":{\"0\":\"synthetic rejection\"}}").getData());
        assertNull(TopicConvertFactory.decodeTopicList("{}").getData());
        assertNull(TopicConvertFactory.decodeTopicList("{\"data\":{}}").getData().get__T());
    }

    @Test public void invalidDocumentsAreNotRepairedAtTheBeanBoundary() {
        for (String payload : new String[]{"{", "{}{}", "{\"data\":{\"__T\":", "<html>synthetic</html>",
                "window.script_muti_get_var_store={}"}) {
            assertThrows(payload, RuntimeException.class, () -> TopicConvertFactory.decodeTopicList(payload));
        }
    }

    @Test public void oldTypedReaderRejectsAnUnknownTypeInAnUnusedExtension() {
        // B1 characterization only. B2's approved local tree decoder must treat these as
        // ordinary extension data; SafeJsonParser/ProfileWebUserParser already do so.
        assertThrows(RuntimeException.class, () -> TopicConvertFactory.decodeTopicList(
            "{\"extension\":{\"@type\":\"not.a.LoadableClass\",\"$ref\":\"$\"},"
            + "\"data\":{\"__F\":{\"name\":\"synthetic\",\"fid\":1},\"__T\":{}},\"time\":1}"));
    }
}
