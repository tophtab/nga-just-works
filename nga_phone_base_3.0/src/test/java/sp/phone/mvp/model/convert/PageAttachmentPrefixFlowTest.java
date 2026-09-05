package sp.phone.mvp.model.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.anzong.androidnga.core.corebuild.HtmlAttachmentBuilder;
import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.HtmlData;
import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.decode.ForumImageDecoder;

public class PageAttachmentPrefixFlowTest {

    private static final String PAGE_PREFIX = "https://page.example/attachments";

    @Test
    public void allCoreConsumersReadTheHtmlDataPagePrefix() throws Exception {
        String imageDecoder = readCoreSource("decode/ForumImageDecoder.java");
        assertTrue(imageDecoder.contains("htmlData.getAttachmentsPrefix()"));
        assertTrue(imageDecoder.contains(
                "NgaImageHost.normalizeLegacyHosts(content, attachmentsPrefix)"));
        assertTrue(imageDecoder.contains(
                "String.format(REPLACE_IMG_NO_HTTP, attachmentsPrefix, \"$1\")"));

        String basicDecoder = readCoreSource("decode/ForumBasicDecoder.java");
        assertTrue(basicDecoder.contains("htmlData.getAttachmentsPrefix()"));
        assertTrue(basicDecoder.contains("[flash=video]"));
        assertTrue(basicDecoder.contains("[flash=audio]"));

        String voteDecoder = readCoreSource("decode/ForumVoteDecoder.java");
        assertTrue(voteDecoder.contains("htmlData.getAttachmentsPrefix()"));

        String commentBuilder = readCoreSource("corebuild/HtmlCommentBuilder.java");
        assertTrue(commentBuilder.contains("ForumDecoder.decode(content, htmlData, null)"));

        String signatureBuilder = readCoreSource("corebuild/HtmlSignatureBuilder.java");
        assertTrue(signatureBuilder.contains(
                "ForumDecoder.decode(htmlData.getSignature(), htmlData)"));
    }

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

    private static String readCoreSource(String relativePath) throws Exception {
        File file = new File("../lib_core/src/main/java/gov/anzong/androidnga/core/"
                + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
