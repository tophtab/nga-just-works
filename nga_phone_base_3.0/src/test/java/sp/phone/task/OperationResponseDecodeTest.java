package sp.phone.task;

import gov.anzong.androidnga.http.OnHttpCallBack;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OperationResponseDecodeTest {
    @Test public void likeUsesDataAndLeavesMalformedAndBusinessErrorsToWrapper() {
        assertEquals("12", LikeTask.decodeResult("{\"data\":{\"0\":12},\"error\":{\"0\":\"denied\"}}"));
        assertNull(LikeTask.decodeResult("{\"data\":{}}"));
        assertThrows(RuntimeException.class, () -> LikeTask.decodeResult("{\"error\":{\"0\":\"denied\"}}"));
        assertThrows(RuntimeException.class, () -> LikeTask.decodeResult("{"));
    }
    @Test public void reportUsesErrorFirstAndPreservesNoCallbackForEmptyEnvelope() {
        List<String> calls = new ArrayList<>();
        OnHttpCallBack<String> callback = new OnHttpCallBack<String>() {
            @Override public void onSuccess(String value) { calls.add("success:" + value); }
            @Override public void onError(String value) { calls.add("error:" + value); }
        };
        ReportTask.deliverResult("{\"data\":{\"0\":\"done\"}}", callback);
        ReportTask.deliverResult("{\"data\":{\"0\":\"done\"},\"error\":{\"0\":\"wait\"}}", callback);
        ReportTask.deliverResult("{}", callback);
        assertEquals(java.util.Arrays.asList("success:done", "error:wait"), calls);
        assertThrows(RuntimeException.class, () -> ReportTask.deliverResult("{", callback));
    }
    @Test public void boardDecodePreservesNullableNameBeforeExistingBoardLookup() {
        SearchBoardTask.SearchResult value = SearchBoardTask.decodeBoard("{\"data\":{\"0\":{\"fid\":\"42\",\"name\":\"Board\"}}}");
        assertEquals(42, value.fid);
        assertEquals("Board", value.title);
        assertNull(SearchBoardTask.decodeBoard("{\"data\":{\"0\":{\"fid\":42}}}").title);
        assertThrows(RuntimeException.class, () -> SearchBoardTask.decodeBoard("{\"error\":{\"0\":\"denied\"}}"));
        assertThrows(RuntimeException.class, () -> SearchBoardTask.decodeBoard("{"));
    }
    private static PostCommentTask.Response comment(String message, int code) {
        return PostCommentTask.Response.decode("<script>window.script_muti_get_var_store={\"data\":{\"__MESSAGE\":{\"1\":\"" + message + "\",\"3\":" + code + "}}}</script>");
    }
    @Test public void commentRetainsWrapperCodeAndExactSuccessMessageSelection() {
        assertEquals("贴条成功", comment(" 发贴完毕 ", 200).message);
        assertTrue(comment(" 发贴完毕 ", 200).success);
        assertFalse(comment("发贴完毕", 403).success);
        assertEquals("denied", comment("denied", 403).message);
        assertFalse(comment("other", 200).success);
        assertEquals("大概没权限,二哥滚粗", comment("", 200).message);
        assertEquals("未知错误", PostCommentTask.Response.decode("{}").message);
        assertEquals("未知错误", PostCommentTask.Response.decode("window.script_muti_get_var_store={</script>").message);
    }
}
