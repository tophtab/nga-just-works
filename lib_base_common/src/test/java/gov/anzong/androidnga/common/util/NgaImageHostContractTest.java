package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 守住图床地址的四条前提：
 *
 * <ol>
 *   <li>无 Android 环境时取值不炸——{@code lib_core} 的解码器在纯 JVM 单测里会走到这里；</li>
 *   <li>自动模式只使用当前页面的合法服务端值，缺失或非法时固定安全兜底；</li>
 *   <li>归一化按路径族分流，且不误伤主站域名；</li>
 *   <li>下拉选项与 MODE_* 存储值对齐，两处不漂移。</li>
 * </ol>
 *
 * <p>若官方再次迁移图床导致本测试失败，应回到规划阶段重新实测各域名，而不是就地改期望值。
 */
public class NgaImageHostContractTest {

    // ---------- 无 Android 环境下的兜底（design §1.3） ----------

    @Test
    public void attachmentBaseUrlFallsBackToDefaultWithoutAndroid() {
        NgaImageHost.invalidate();
        assertEquals(NgaImageHost.DEFAULT_BASE_URL, NgaImageHost.attachmentBaseUrl());
    }

    @Test
    public void attachmentsPrefixHasNoTrailingSlash() {
        NgaImageHost.invalidate();
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX, NgaImageHost.attachmentsPrefix());
    }

    @Test
    public void autoModeUsesPageValueWithoutAndroidContext() {
        NgaImageHost.invalidate();
        assertEquals("https://page-a.example/attachments",
                NgaImageHost.attachmentsPrefix("https://page-a.example/attachments"));
    }

    @Test
    public void pageValuesAreNeverCachedAcrossCalls() {
        NgaImageHost.invalidate();
        assertEquals("https://page-a.example/attachments",
                NgaImageHost.attachmentsPrefix("page-a.example"));
        assertEquals("https://page-b.example/attachments",
                NgaImageHost.attachmentsPrefix("page-b.example/attachments/"));
    }

    // ---------- 选项表 ----------

    @Test
    public void modeValuesMatchUiOrder() {
        assertEquals(0, NgaImageHost.MODE_AUTO);
        assertEquals(1, NgaImageHost.MODE_DEFAULT);
        assertEquals(2, NgaImageHost.MODE_IMG9);
        assertEquals(3, NgaImageHost.MODE_CUSTOM);
    }

    /**
     * img9.nga.cn 的 https 稳定返回 NGA 自己的 404 页，只有 http 可用。
     * 这条断言是为了拦住「顺手把协议统一成 https」的整理式改动。
     */
    @Test
    public void img9OptionStaysOnHttp() {
        assertEquals("http://img9.nga.cn", NgaImageHost.IMG9_BASE_URL);
    }

    @Test
    public void modesAlignWithDropdownArrays() throws Exception {
        List<String> entries = readStringArray("image_domain");
        List<String> values = readStringArray("image_domain_value");

        assertEquals(4, entries.size());
        assertEquals(4, values.size());
        for (int i = 0; i < values.size(); i++) {
            assertEquals(String.valueOf(i), values.get(i));
        }
        assertEquals("自动", entries.get(NgaImageHost.MODE_AUTO));
        assertEquals(NgaImageHost.DEFAULT_BASE_URL, entries.get(NgaImageHost.MODE_DEFAULT));
        assertEquals(NgaImageHost.IMG9_BASE_URL, entries.get(NgaImageHost.MODE_IMG9));
        assertEquals("自定义", entries.get(NgaImageHost.MODE_CUSTOM));
    }

    // ---------- 自动/手动模式决策 ----------

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

        String retiredServerValue = "https://img.nga.178.com/attachments";
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_DEFAULT, null, retiredServerValue));
        assertEquals("http://img9.nga.cn/attachments",
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_IMG9, null, retiredServerValue));
        assertEquals("https://custom.example/attachments",
                NgaImageHost.resolveAttachmentsPrefix(
                        NgaImageHost.MODE_CUSTOM, "custom.example", retiredServerValue));
    }

    // ---------- sanitizeBaseUrlInput ----------

    @Test
    public void blankInputFallsBackToDefault() {
        assertNull(NgaImageHost.sanitizeBaseUrlInput(null));
        assertNull(NgaImageHost.sanitizeBaseUrlInput(""));
        assertNull(NgaImageHost.sanitizeBaseUrlInput("   "));
    }

    @Test
    public void bareHostGetsHttps() {
        assertEquals("https://img.nga.cn", NgaImageHost.sanitizeBaseUrlInput("img.nga.cn"));
    }

    @Test
    public void declaredSchemeIsRespected() {
        assertEquals("https://img.nga.cn", NgaImageHost.sanitizeBaseUrlInput("https://img.nga.cn"));
        assertEquals("http://img9.nga.cn", NgaImageHost.sanitizeBaseUrlInput("http://img9.nga.cn/"));
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

    // ---------- normalizeLegacyHosts：附件族 ----------

    @Test
    public void legacyAttachmentHostIsRewrittenToCurrentHost() {
        NgaImageHost.invalidate();
        assertEquals("https://img.nga.cn/attachments/mon_202608/01/-7Q46-48cvZeT1kShs-12h.jpg",
                NgaImageHost.normalizeLegacyHosts(
                        "http://img.nga.178.com/attachments/mon_202608/01/-7Q46-48cvZeT1kShs-12h.jpg"));
    }

    @Test
    public void numberedLegacyAttachmentHostCollapsesToCurrentHost() {
        NgaImageHost.invalidate();
        // img6 曾经供过附件，但如今 img1-img8.nga.cn 对 /attachments/ 全部 404，
        // 所以附件族必须落到当前主机，不能像表情那样保号。
        assertEquals("https://img.nga.cn/attachments/mon_202601/01/x.jpg",
                NgaImageHost.normalizeLegacyHosts(
                        "http://img6.nga.178.com/attachments/mon_202601/01/x.jpg"));
    }

    @Test
    public void legacyNgacnAttachmentHostIsRewritten() {
        NgaImageHost.invalidate();
        assertEquals("https://img.nga.cn/attachments/mon_202601/01/x.jpg",
                NgaImageHost.normalizeLegacyHosts(
                        "http://img.ngacn.cc/attachments/mon_202601/01/x.jpg"));
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

    @Test
    public void qualitySuffixAndQuerySurviveNormalization() {
        NgaImageHost.invalidate();
        assertEquals("https://img.nga.cn/attachments/mon_202601/01/x.jpg.thumb.jpg",
                NgaImageHost.normalizeLegacyHosts(
                        "http://img.nga.178.com/attachments/mon_202601/01/x.jpg.thumb.jpg"));
        assertEquals("https://img.nga.cn/attachments/mon_202601/01/a.mp3?duration=3&filename=nga_audio.mp3",
                NgaImageHost.normalizeLegacyHosts(
                        "http://img.ngacn.cc/attachments/mon_202601/01/a.mp3?duration=3&filename=nga_audio.mp3"));
    }

    // ---------- normalizeLegacyHosts：表情族 ----------

    /**
     * 表情只有 img4.nga.cn 提供，img.nga.cn 对该路径 404。
     * 一刀切归一化会把它从「域名已死」变成「404」，等于没修。
     */
    @Test
    public void emoticonPathKeepsItsHostNumber() {
        NgaImageHost.invalidate();
        assertEquals("https://img4.nga.cn/ngabbs/post/smile/ac0.png",
                NgaImageHost.normalizeLegacyHosts(
                        "https://img4.nga.178.com/ngabbs/post/smile/ac0.png"));
    }

    @Test
    public void unnumberedNonAttachmentHostBecomesUnnumberedNgaCn() {
        NgaImageHost.invalidate();
        assertEquals("https://img.nga.cn/ngabbs/other/x.png",
                NgaImageHost.normalizeLegacyHosts("http://img.ngacn.cc/ngabbs/other/x.png"));
    }

    // ---------- normalizeLegacyHosts：不误伤 ----------

    @Test
    public void forumDomainsAreUntouched() {
        NgaImageHost.invalidate();
        String forum = "<a href='https://nga.178.com/read.php?tid=1'>x</a>"
                + "<a href='https://bbs.ngacn.cc/thread.php?fid=7'>y</a>"
                + "<a href='https://bbs.nga.cn/read.php?tid=2'>z</a>";
        assertEquals(forum, NgaImageHost.normalizeLegacyHosts(forum));
    }

    @Test
    public void currentHostIsUntouched() {
        NgaImageHost.invalidate();
        String current = "https://img.nga.cn/attachments/mon_202601/01/x.jpg";
        assertEquals(current, NgaImageHost.normalizeLegacyHosts(current));
    }

    @Test
    public void localEmoticonAssetsAreUntouched() {
        NgaImageHost.invalidate();
        String local = "<img class='emoticon' src='file:///android_asset/ac/ac0.png'>";
        assertEquals(local, NgaImageHost.normalizeLegacyHosts(local));
    }

    @Test
    public void nullAndEmptyContentPassThrough() {
        assertNull(NgaImageHost.normalizeLegacyHosts(null));
        assertEquals("", NgaImageHost.normalizeLegacyHosts(""));
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

    // ---------- helpers ----------

    private static List<String> readStringArray(String name) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/values/arrays.xml"));
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (!name.equals(array.getAttribute("name"))) {
                continue;
            }
            List<String> items = new ArrayList<>();
            NodeList children = array.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "item".equals(child.getNodeName())) {
                    items.add(child.getTextContent().trim());
                }
            }
            assertTrue("empty string-array: " + name, !items.isEmpty());
            return items;
        }
        assertNotNull("missing string-array: " + name, null);
        return null;
    }
}
