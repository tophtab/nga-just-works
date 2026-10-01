package sp.phone.proxy;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProxyResultDecodeTest {
    @Test public void dataWinsAndEmptyMessageDoesNotFallBackToError() {
        String payload = "{\"data\":{\"0\":\"done\"},\"error\":{\"0\":\"denied\"}}";
        assertEquals("done", ProxyBridge.selectResult(ProxyBridge.decodeResultData(payload, "data"), ProxyBridge.decodeResultData(payload, "error")));
        assertEquals("denied", ProxyBridge.selectResult(null, ProxyBridge.decodeResultData(payload, "error")));
        assertEquals("请重新登录", ProxyBridge.selectResult(null, null));
        assertEquals("二哥又开始乱搞了", ProxyBridge.selectResult(ProxyBridge.decodeResultData("{\"data\":{}}", "data"), ProxyBridge.decodeResultData(payload, "error")));
        assertThrows(RuntimeException.class, () -> ProxyBridge.decodeResultData("{", "data"));
        assertThrows(ClassCastException.class, () -> ProxyBridge.decodeResultData("{\"data\":[]}", "data"));
    }
}
