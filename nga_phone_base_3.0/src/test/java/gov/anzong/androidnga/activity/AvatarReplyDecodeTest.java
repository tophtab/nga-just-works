package gov.anzong.androidnga.activity;
import org.junit.Test;
import static org.junit.Assert.*;
public class AvatarReplyDecodeTest {
    @Test public void selectedDataAndFallbackKeepTheirDifferentNullBehavior() {
        String payload = "{\"data\":{\"0\":\"done\"},\"error\":{\"0\":\"denied\"}}";
        assertEquals("done", AvatarPostActivity.selectReplyResult(AvatarPostActivity.decodeResultData(payload, "data")));
        assertEquals("denied", AvatarPostActivity.selectReplyResult(AvatarPostActivity.decodeResultData(payload, "error")));
        assertEquals("发送失败", AvatarPostActivity.selectReplyResult(null));
        assertNull(AvatarPostActivity.selectReplyResult(AvatarPostActivity.decodeResultData("{\"data\":{}}", "data")));
        assertThrows(RuntimeException.class, () -> AvatarPostActivity.decodeResultData("{", "data"));
        assertThrows(ClassCastException.class, () -> AvatarPostActivity.decodeResultData("{\"data\":[]}", "data"));
    }
}
