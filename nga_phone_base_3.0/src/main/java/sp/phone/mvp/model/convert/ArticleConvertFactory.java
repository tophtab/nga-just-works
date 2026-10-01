package sp.phone.mvp.model.convert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import gov.anzong.androidnga.core.thread.ReadDecodeMode;
import gov.anzong.androidnga.core.thread.ReadThreadDecodeResult;
import gov.anzong.androidnga.core.thread.ReadThreadWireDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.HtmlConvertFactory;
import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.CommentData;
import gov.anzong.androidnga.core.data.HtmlData;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.Attachment;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.thread.ArticleErrors;
import sp.phone.mvp.model.thread.ArticleFailure;
import sp.phone.mvp.model.thread.ArticleFailureKind;
import sp.phone.mvp.model.thread.ArticleBlacklist;
import sp.phone.mvp.model.thread.ArticleRowRenderer;
import sp.phone.theme.ThemeManager;
import sp.phone.util.FunctionUtils;

/**
 * Created by Justwen on 2017/12/3.
 */

public class ArticleConvertFactory {

    public static ThreadData getArticleInfo(String js) {
        return getArticleInfo(js, ArticleConvertFactory::renderRow,
                uid -> UserManagerImpl.getInstance().checkBlackList(uid));
    }

    public static ThreadData getArticleInfo(String js, ArticleRowRenderer renderer, ArticleBlacklist blacklist) {
        return parseJsonThreadPage(js, renderer, blacklist, false);
    }

    public static ThreadData getScopedArticleInfo(String js, ArticleRowRenderer renderer, ArticleBlacklist blacklist) {
        return parseJsonThreadPage(js, renderer, blacklist, true);
    }

    private static ThreadData parseJsonThreadPage(String js, ArticleRowRenderer renderer, ArticleBlacklist blacklist, boolean strict) {
        String original = js;
        ThreadData data = null;
        try {
            if (strict) js = ArticleErrors.bodyText(js);
            if (js.isEmpty()) {
                return null;
            } else if (js.contains("/*error fill content")) {
                js = js.substring(0, js.indexOf("/*error fill content"));
            }

            js = js.replaceAll("/\\*\\$js\\$\\*/", "")
                    .replaceAll("\"content\":\\+(\\d+),", "\"content\":\"+$1\",")
                    .replaceAll("\"subject\":\\+(\\d+),", "\"subject\":\"+$1\",")
                    .replaceAll("\"content\":(0\\d+),", "\"content\":\"$1\",")
                    .replaceAll("\"subject\":(0\\d+),", "\"subject\":\"$1\",")
                    .replaceAll("\"author\":(0\\d+),", "\"author\":\"$1\",")
                    .replaceAll("\"alterinfo\":\"\\[(\\w|\\s)+\\]\\s+\",", ""); //部分页面打不开的问题
//            NLog.e(js);
            JSONObject root = strict ? ArticleErrors.json(js) : JSON.parseObject(js);
            JSONObject obj = root.getJSONObject("data");
            if (obj == null) {
                return null;
            }
            ReadThreadBeanFallbacks fallbacks = ReadThreadBeanFallbacks.decode(obj, strict);
            ReadThreadDecodeResult decoded = ReadThreadWireDecoder.decode(obj,
                    strict ? ReadDecodeMode.SCOPED : ReadDecodeMode.LEGACY, fallbacks.getInvalidRows());
            if (decoded instanceof ReadThreadDecodeResult.Failure) {
                if (strict) {
                    switch (((ReadThreadDecodeResult.Failure) decoded).getProblem()) {
                        case ROW_MAP_OR_COUNT: throw new ArticleFailure(ArticleFailureKind.FORMAT);
                        case INDEXED_ROW: throw new ArticleFailure(ArticleFailureKind.CONTENT);
                        case UNREADABLE_VALUE: return null;
                    }
                }
                return null;
            }
            data = ReadThreadLegacyMapper.map(((ReadThreadDecodeResult.Success) decoded).getThread(),
                    original, strict, renderer, blacklist, fallbacks);
        } catch (ArticleFailure failure) {
            if (strict) throw failure;
            return null;
        } catch (Exception e) {
            // Parser exceptions can contain source text. Keep them out of diagnostics.
            return null;
        }
        return data;
    }

    /** 从当前 THREAD.PAGE 的 data 中提取并解析页面级附件前缀。 */
    static String resolveAttachmentsPrefix(JSONObject data) {
        String serverAttachmentBaseView = null;
        if (data != null) {
            Object globalValue = data.get("__GLOBAL");
            if (globalValue instanceof JSONObject) {
                Object rawValue = ((JSONObject) globalValue).get("_ATTACH_BASE_VIEW");
                if (rawValue instanceof String) {
                    serverAttachmentBaseView = (String) rawValue;
                }
            }
        }
        return NgaImageHost.attachmentsPrefix(serverAttachmentBaseView);
    }

    /** Source-neutral rendering seam. Normal-only WP preprocessing stays above this boundary. */
    public static void renderRow(ThreadRowInfo row, String attachmentsPrefix) {
        List<String> imageUrls = new ArrayList<>();
        String ngaHtml = HtmlConvertFactory.convert(
                buildHtmlData(row, attachmentsPrefix), imageUrls);
        row.getImageUrls().clear();
        row.getImageUrls().addAll(imageUrls);
        row.setFormattedHtmlData(ngaHtml);
    }

    private static HtmlData buildHtmlData(ThreadRowInfo row, String attachmentsPrefix) {
        HtmlData htmlData = new HtmlData(sp.phone.mvp.model.thread.ArticleSourceText.renderBody(row));
        htmlData.setAttachmentsPrefix(attachmentsPrefix);
        htmlData.setAlertInfo(row.getAlterinfo());
        htmlData.setDarkMode(ThemeManager.getInstance().isNightMode());
        htmlData.setInBackList(row.get_isInBlackList());
        htmlData.setTextSize(PhoneConfiguration.getInstance().getTopicContentSize());
        htmlData.setEmotionSize(PhoneConfiguration.getInstance().getEmoticonSize());
        htmlData.setSignature(PhoneConfiguration.getInstance().isShowSignature() ? row.getSignature() : null);
        htmlData.setVote(row.getVote());
        htmlData.setSubject(row.getSubject());
        htmlData.setShowImage(PhoneConfiguration.getInstance().isImageLoadEnabled());
        htmlData.setNGAHost(Utils.getNGAHost());
        htmlData.pid = String.valueOf(row.pid);
        htmlData.tid = String.valueOf(row.tid);
        htmlData.uid = String.valueOf(row.getAuthorid());
        if (row.getAttachs() != null) {
            htmlData.setAttachmentList(buildAttachmentData(row.getAttachs()));
        }

        if (row.getComments() != null) {
            List<CommentData> comments = new ArrayList<>();
            for (ThreadRowInfo value : row.getComments()) {
                CommentData comment = new CommentData();
                comment.setAuthor(value.getAuthor());
                String body = sp.phone.mvp.model.thread.ArticleSourceText.renderBody(value);
                comment.setContent(body == null || body.isEmpty() ? value.getAlterinfo() : body);
                comment.setPostTime(value.getPostdate());
                comment.setAvatarUrl(FunctionUtils.parseAvatarUrl(value.getJs_escap_avatar()));
                comments.add(comment);
            }
            htmlData.setCommentList(comments);
        }
        return htmlData;
    }

    /** Exact production rendering projection, exposed for host verification without theme/context setup. */
    static List<AttachmentData> buildAttachmentData(Map<String, Attachment> source) {
        List<AttachmentData> attachments = new ArrayList<>();
        for (Map.Entry<String, Attachment> entry : source.entrySet()) {
            AttachmentData data = new AttachmentData();
            data.setAttachUrl(entry.getValue().getAttachurl());
            data.setThumb(entry.getValue().getThumb());
            attachments.add(data);
        }
        return attachments;
    }

}
