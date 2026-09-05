package sp.phone.mvp.model.convert;

import static org.junit.Assert.assertEquals;

import com.alibaba.fastjson.JSONObject;

import org.junit.Before;
import org.junit.Test;

import gov.anzong.androidnga.common.util.NgaImageHost;

public class ArticleConvertFactoryTest {

    @Before
    public void resetImageHostPreferenceCache() {
        NgaImageHost.invalidate();
    }

    @Test
    public void resolvesValidPageAttachmentBaseView() {
        JSONObject data = pageDataWithAttachmentBaseView(
                "https://page.example/attachments/");

        assertEquals("https://page.example/attachments",
                ArticleConvertFactory.resolveAttachmentsPrefix(data));
    }

    @Test
    public void resolvesRetiredPageAttachmentBaseViewToCurrentDefault() {
        JSONObject data = pageDataWithAttachmentBaseView(
                "img.nga.178.com/attachments");

        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(data));
    }

    @Test
    public void missingGlobalFallsBackWithoutChangingOtherPageData() {
        JSONObject data = new JSONObject();
        data.put("__ROWS", 3);

        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(data));
        assertEquals(3, data.getIntValue("__ROWS"));
    }

    @Test
    public void missingMalformedOrNonStringFieldFallsBack() {
        JSONObject missingField = new JSONObject();
        missingField.put("__GLOBAL", new JSONObject());
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(missingField));

        JSONObject malformedGlobal = new JSONObject();
        malformedGlobal.put("__GLOBAL", "bad");
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(malformedGlobal));

        JSONObject nonStringField = pageDataWithAttachmentBaseView(123);
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(nonStringField));

        JSONObject invalidField = pageDataWithAttachmentBaseView(
                "https://img.nga.cn/not-attachments");
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(invalidField));
    }

    private static JSONObject pageDataWithAttachmentBaseView(Object value) {
        JSONObject global = new JSONObject();
        global.put("_ATTACH_BASE_VIEW", value);
        JSONObject data = new JSONObject();
        data.put("__GLOBAL", global);
        return data;
    }
}
