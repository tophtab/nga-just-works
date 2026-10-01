package sp.phone.task;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarUploadDecodeTest {
    @Test public void unquotedExternalSuccessUsesStringDataInsteadOfNgaAttachmentEnvelope() {
        AvatarFileUploadTask.NonameUploadResponse result = AvatarFileUploadTask.decodeUploadResponse(
                "{error:false,data:\"https://example.invalid/合成.png\"}");
        assertFalse(result.error); assertNull(result.errorinfo);
        assertEquals("https://example.invalid/合成.png", result.data);
    }

    @Test public void externalErrorKeepsItsOwnFlagAndMessageEvenWithData() {
        AvatarFileUploadTask.NonameUploadResponse result = AvatarFileUploadTask.decodeUploadResponse(
                "{error:true,errorinfo:\"synthetic rejection\",data:\"unused\"}");
        assertTrue(result.error); assertEquals("synthetic rejection", result.errorinfo);
        assertEquals("unused", result.data);
    }

    @Test public void absentFieldsRetainBeanDefaultsWithoutInventingNGAErrorMeaning() {
        AvatarFileUploadTask.NonameUploadResponse result = AvatarFileUploadTask.decodeUploadResponse("{error_code:9}");
        assertFalse(result.error); assertNull(result.data); assertNull(result.errorinfo);
        assertNull(AvatarFileUploadTask.decodeUploadResponse("null"));
    }

    @Test public void malformedInputStillThrowsAtTheOriginalUnguardedDecodeBoundary() {
        for (String payload : new String[]{"{", "{}{}", "<html>synthetic</html>", "window.script_muti_get_var_store={}"}) {
            assertThrows(payload, RuntimeException.class, () -> AvatarFileUploadTask.decodeUploadResponse(payload));
        }
    }
}
