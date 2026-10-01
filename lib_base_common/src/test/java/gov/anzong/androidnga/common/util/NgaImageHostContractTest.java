package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Page-scoped image host selection, validation and legacy path-family normalization. */
public class NgaImageHostContractTest {

    @Test
    public void pageValuesAreNeverCachedAcrossCalls() {
        NgaImageHost.invalidate();
        assertEquals("https://page-a.example/attachments",
                NgaImageHost.attachmentsPrefix("page-a.example"));
        assertEquals("https://page-b.example/attachments",
                NgaImageHost.attachmentsPrefix("page-b.example/attachments/"));
    }

    @Test
    public void validServerFormsBecomeCompleteAttachmentPrefixes() {
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView("img.nga.cn"));
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView("img.nga.cn/"));
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView("img.nga.cn/attachments"));
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView("https://img.nga.cn"));
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView(
                        "https://img.nga.cn/attachments/"));
        assertEquals("https://img.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView(
                        "//img.nga.cn/attachments"));
        assertEquals("http://img9.nga.cn/attachments",
                NgaImageHost.sanitizeServerAttachmentBaseView("http://img9.nga.cn"));
    }

    @Test
    public void invalidServerFormsAreRejected() {
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(null));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(""));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("   "));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("ftp://img.nga.cn"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(
                "https://user@img.nga.cn/attachments"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(
                "https://img.nga.cn/other"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(
                "https://img.nga.cn/attachments?x=1"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView(
                "https://img.nga.cn/attachments#x"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("https://空格 域名"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("null"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("null:8080"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("https://undefined/attachments"));
        assertNull(NgaImageHost.sanitizeServerAttachmentBaseView("https://undefined:443/attachments"));
    }

    @Test
    public void autoModeFallsBackForMissingOrInvalidServerValue() {
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_AUTO, null, null));
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_AUTO, null, "https://img.nga.cn/other"));
        assertEquals("https://server.example/attachments",
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_AUTO, null, "server.example"));
        assertEquals("https://server.example/attachments",
                NgaImageHost.resolveAttachmentsPrefix(99, null, "server.example"));
    }

    @Test
    public void autoModeFallsBackForRetiredImageHosts() {
        String[] retiredValues = {
                "img.nga.178.com",
                "http://img7.nga.178.com/",
                "https://IMG2.NGACN.CC/attachments/",
                "//img9.ngacn.cc/attachments"
        };

        for (String retiredValue : retiredValues) {
            assertEquals("retired page host: " + retiredValue,
                    NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                    NgaImageHost.resolveAttachmentsPrefix(
                            NgaImageHost.MODE_AUTO, null, retiredValue));
        }
    }

    @Test
    public void manualModesIgnoreServerValue() {
        String serverValue = "https://server.example/attachments";
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_DEFAULT, null, serverValue));
        assertEquals("http://img9.nga.cn/attachments",
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_IMG9, null, serverValue));
        assertEquals("https://custom.example/attachments",
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_CUSTOM, "custom.example", serverValue));
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_CUSTOM, "", serverValue));

    }

    @Test
    public void blankInputFallsBackToDefault() {
        assertNull(NgaImageHost.sanitizeBaseUrlInput(null));
        assertNull(NgaImageHost.sanitizeBaseUrlInput(""));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("   "));
    }

    @Test
    public void surroundingSpaceAndPathAreStripped() {
        assertEquals("https://img.nga.cn",
                NgaImageHost.sanitizeBaseUrlInput("  img.nga.cn/attachments  "));
        assertEquals("https://img.nga.cn", NgaImageHost.sanitizeBaseUrlInput("img.nga.cn?a=1"));
        assertEquals("https://img.nga.cn", NgaImageHost.sanitizeBaseUrlInput("img.nga.cn#x"));
    }

    @Test
    public void portIsPreserved() {
        assertEquals("https://img.nga.cn:8080", NgaImageHost.sanitizeBaseUrlInput("img.nga.cn:8080"));
    }

    @Test
    public void malformedInputIsRejected() {
        assertNull(NgaImageHost.sanitizeBaseUrlInput("乱码//"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("//"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("ftp://img.nga.cn"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("https://"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("http://空格 域名"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("null:8080"));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("https://undefined:443"));
    }

    @Test
    public void pagePrefixOverridesOnlyLegacyAttachmentFamily() {
        String content = "<img src='http://img6.nga.178.com/attachments/a.jpg.thumb.jpg?x=1'>"
                + "<img src='https://img4.nga.178.com/ngabbs/post/smile/ac0.png'>";
        String expected = "<img src='https://page.example/attachments/a.jpg.thumb.jpg?x=1'>"
                + "<img src='https://img4.nga.cn/ngabbs/post/smile/ac0.png'>";
        assertEquals(expected, NgaImageHost.normalizeLegacyHosts(
                content, "https://page.example/attachments"));
    }

    /**
     * 表情只有 img4.nga.cn 提供，img.nga.cn 对该路径 404。
     * 一刀切归一化会把它从「域名已死」变成「404」，等于没修。
     */
    @Test
    public void unnumberedNonAttachmentHostBecomesUnnumberedNgaCn() {
        NgaImageHost.invalidate();
        assertEquals("https://img.nga.cn/ngabbs/other/x.png",
                NgaImageHost.normalizeLegacyHosts("http://img.ngacn.cc/ngabbs/other/x.png"));
    }

    @Test
    public void forumDomainsAreUntouched() {
        NgaImageHost.invalidate();
        String forum = "<a href='https://nga.178.com/read.php?tid=1'>x</a>"
                + "<a href='https://bbs.ngacn.cc/thread.php?fid=7'>y</a>"
                + "<a href='https://bbs.nga.cn/read.php?tid=2'>z</a>";
        assertEquals(forum, NgaImageHost.normalizeLegacyHosts(forum));
    }

    @Test
    public void multipleOccurrencesAreAllRewritten() {
        NgaImageHost.invalidate();
        String content = "<img src='http://img.nga.178.com/attachments/a.jpg'>"
                + "<img src='http://img6.nga.178.com/attachments/b.jpg'>"
                + "<img src='https://img4.nga.178.com/ngabbs/post/smile/ac0.png'>";
        String expected = "<img src='https://img.nga.cn/attachments/a.jpg'>"
                + "<img src='https://img.nga.cn/attachments/b.jpg'>"
                + "<img src='https://img4.nga.cn/ngabbs/post/smile/ac0.png'>";
        assertEquals(expected, NgaImageHost.normalizeLegacyHosts(content));
    }

}
