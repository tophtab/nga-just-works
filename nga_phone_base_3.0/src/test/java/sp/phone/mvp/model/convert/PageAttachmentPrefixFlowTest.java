package sp.phone.mvp.model.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.anzong.androidnga.core.corebuild.HtmlAttachmentBuilder;
import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.HtmlData;
import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.decode.ForumImageDecoder;
import gov.anzong.androidnga.core.decode.ForumBasicDecoder;

public class PageAttachmentPrefixFlowTest {

    private static final String PAGE_PREFIX = "https://page.example/attachments";

    @Test
    public void attachmentBuilderAndImageListUseOnePagePrefix() {
        HtmlData htmlData = createHtmlData();
        AttachmentData image = attachment("mon_202608/a.jpg", "1");
        AttachmentData audio = attachment("mon_202608/a.mp3", "0");
        AttachmentData video = attachment("mon_202608/v.mp4", "0");
        htmlData.setAttachmentList(Arrays.asList(image, audio, video));
        List<String> images = new ArrayList<>();

        String html = new HtmlAttachmentBuilder().build(htmlData, images).toString();

        assertTrue(html.contains(PAGE_PREFIX + "/mon_202608/a.jpg"));
        assertTrue(html.contains(PAGE_PREFIX + "/mon_202608/a.mp3"));
        assertTrue(html.contains(PAGE_PREFIX + "/mon_202608/v.mp4"));
        assertEquals(Arrays.asList(PAGE_PREFIX + "/mon_202608/a.jpg"), images);
    }

    @Test
    public void retiredPagePrefixFeedsBodyAndAttachmentConsumers() {
        String attachmentsPrefix = NgaImageHost.attachmentsPrefix(
                "img.nga.178.com/attachments");
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX, attachmentsPrefix);

        HtmlData htmlData = HtmlData.create("[img]./mon_test/body.jpg[/img]", "https://bbs.nga.cn/");
        htmlData.setAttachmentsPrefix(attachmentsPrefix);
        List<String> images = new ArrayList<>();

        String body = new ForumImageDecoder().decode(htmlData.getRawData(), htmlData);
        assertTrue(body.contains(attachmentsPrefix + "/mon_test/body.jpg"));

        AttachmentData attachment = attachment("mon_test/attachment.jpg", "1");
        htmlData.setAttachmentList(Arrays.asList(attachment));
        String attachmentHtml = new HtmlAttachmentBuilder().build(htmlData, images).toString();
        assertTrue(attachmentHtml.contains(attachmentsPrefix + "/mon_test/attachment.jpg"));
        assertEquals(Arrays.asList(attachmentsPrefix + "/mon_test/attachment.jpg"), images);
    }

    @Test
    public void mediaConsumesResolvedPrefixesWithoutChangingAttachmentLinks() {
        String source = "[flash]./v.mp4[/flash][flash=video]./typed.mp4[/flash][flash=audio]./a.mp3[/flash]";
        for (String server : new String[]{"page.example", "http://img9.nga.cn/attachments/",
                "//second.example/attachments", "img.nga.178.com", ""}) {
            String prefix = NgaImageHost.attachmentsPrefix(server);
            HtmlData data = HtmlData.create(source, "https://forum.example/");
            data.setAttachmentsPrefix(prefix);
            assertEquals("<video src='" + prefix + "/v.mp4' controls='controls'></video>"
                    + "<video src='" + prefix + "/typed.mp4' controls='controls'></video>"
                    + "<audio src='" + prefix + "/a.mp3&filename=nga_audio.mp3' controls='controls'></audio>",
                    new ForumBasicDecoder().decode(source, data));
            assertEquals(source, data.getRawData());
            data.setAttachmentList(Arrays.asList(attachment("v.mp4", "0"), attachment("a.mp3", "0")));
            String links = new HtmlAttachmentBuilder().build(data, new ArrayList<>()).toString();
            assertTrue(links.contains("href='" + prefix + "/v.mp4'"));
            assertTrue(links.contains("href='" + prefix + "/a.mp3'"));
            org.junit.Assert.assertFalse(links.contains("<video"));
            org.junit.Assert.assertFalse(links.contains("<audio"));
        }
    }

    private static HtmlData createHtmlData() {
        HtmlData htmlData = HtmlData.create("", "https://bbs.nga.cn/");
        htmlData.setAttachmentsPrefix(PAGE_PREFIX);
        return htmlData;
    }

    private static AttachmentData attachment(String url, String thumb) {
        AttachmentData data = new AttachmentData();
        data.setAttachUrl(url);
        data.setThumb(thumb);
        return data;
    }
}
