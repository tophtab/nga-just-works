package sp.phone.mvp.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class TopicPostUploadDecodeTest {
    private static final String DATA = "{attachments:\"synthetic-a\",attachments_check:\"synthetic-check\",url:\"https://example.invalid/合成.png?x=1&y=2\"}";

    @Test public void unquotedSuccessKeepsAllAttachmentFields() {
        TopicPostModel.UploadResponse response = TopicPostModel.decodeUploadResponse("{data:" + DATA + "}", false);
        assertFalse(response.compressRequired);
        assertEquals("synthetic-a", response.attachments);
        assertEquals("synthetic-check", response.attachmentsCheck);
        assertEquals("https://example.invalid/合成.png?x=1&y=2", response.url);
    }

    @Test public void firstCodeNineSelectsCompressionBeforeReadingData() {
        for (String payload : new String[]{"{error_code:9}", "{error_code:\"9\",data:[]}", "{error_code:9,data:" + DATA + "}"}) {
            TopicPostModel.UploadResponse response = TopicPostModel.decodeUploadResponse(payload, false);
            assertTrue(response.compressRequired);
            assertNull(response.attachments); assertNull(response.attachmentsCheck); assertNull(response.url);
        }
    }

    @Test public void compressedCodeNineAndOtherCodesKeepTheirOriginalDataSelection() {
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodeUploadResponse("{error_code:9}", true));
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodeUploadResponse("{error_code:8,error:\"synthetic rejection\"}", false));
        for (String payload : new String[]{"{error_code:9,data:" + DATA + "}", "{error_code:8,data:" + DATA + "}"}) {
            TopicPostModel.UploadResponse response = TopicPostModel.decodeUploadResponse(payload, true);
            assertFalse(response.compressRequired);
            assertEquals("synthetic-a", response.attachments);
        }
    }

    @Test public void missingFieldsAndNumericScalarsKeepLegacyCoercion() {
        TopicPostModel.UploadResponse empty = TopicPostModel.decodeUploadResponse("{data:{}}", false);
        assertFalse(empty.compressRequired); assertNull(empty.attachments);
        assertNull(empty.attachmentsCheck); assertNull(empty.url);
        TopicPostModel.UploadResponse numbers = TopicPostModel.decodeUploadResponse("{data:{attachments:12,attachments_check:34,url:56}}", false);
        assertEquals("12", numbers.attachments); assertEquals("34", numbers.attachmentsCheck); assertEquals("56", numbers.url);
    }

    @Test public void businessAndMalformedPayloadsStillFailForTheExistingCallbackCatch() {
        for (String payload : new String[]{"{error:{\"0\":\"synthetic rejection\"}}", "{}", "null", "[]", "{", "{}{}",
                "{error_code:null,data:" + DATA + "}", "{error_code:\"wrong\",data:" + DATA + "}",
                "{data:[]}", "window.script_muti_get_var_store={data:" + DATA + "}"}) {
            assertThrows(payload, RuntimeException.class, () -> TopicPostModel.decodeUploadResponse(payload, false));
        }
    }
}
