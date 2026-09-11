package sp.phone.mvp.model.convert;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.HtmlConvertFactory;
import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.CommentData;
import gov.anzong.androidnga.core.data.HtmlData;
import sp.phone.common.ForumConstants;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.Attachment;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.thread.ArticleAuthorSupport;
import sp.phone.mvp.model.thread.ArticleErrors;
import sp.phone.mvp.model.thread.ArticleFailure;
import sp.phone.mvp.model.thread.ArticleFailureKind;
import sp.phone.mvp.model.thread.ArticleBlacklist;
import sp.phone.mvp.model.thread.ArticleRowKind;
import sp.phone.mvp.model.thread.ArticleRowPresentation;
import sp.phone.mvp.model.thread.ArticleRowRenderer;
import sp.phone.theme.ThemeManager;
import sp.phone.util.FunctionUtils;
import sp.phone.util.StringUtils;

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
            if (strict) {
                Object rowMap = obj.get("__R");
                Integer count = obj.getInteger("__R__ROWS");
                if (!(rowMap instanceof JSONObject) || count == null || count < 0 || count > ((JSONObject) rowMap).size()) {
                    throw new ArticleFailure(ArticleFailureKind.FORMAT);
                }
                for (int i = 0; i < count; i++) {
                    if (!(((JSONObject) rowMap).get(String.valueOf(i)) instanceof JSONObject)) {
                        throw new ArticleFailure(ArticleFailureKind.CONTENT);
                    }
                }
            }
            int allRows = (Integer) obj.get("__ROWS");
            data = new ThreadData();
            data.setRawData(original);
            data.setThreadInfo(buildThreadPageInfo(obj));
            data.setRowList(buildThreadRowList(obj, renderer, blacklist, strict));
            if (strict) data.setContentComplete(hasCompleteSource(data.getRowList()));
            data.set__ROWS(allRows);
            data.setRowNum(data.getRowList().size());
        } catch (ArticleFailure failure) {
            if (strict) throw failure;
            return null;
        } catch (Exception e) {
            // Parser exceptions can contain source text. Keep them out of diagnostics.
            return null;
        }
        return data;
    }

    private static ThreadPageInfo buildThreadPageInfo(JSONObject obj) {
        JSONObject subObj = (JSONObject) obj.get("__T");
        if (subObj == null) {
            return null;
        }
        try {
            return JSONObject.toJavaObject(subObj, ThreadPageInfo.class);
        } catch (RuntimeException e) {
            // Invalid optional metadata must not leak the response to logs.
        }
        return null;
    }

    private static List<ThreadRowInfo> buildThreadRowList(JSONObject obj, ArticleRowRenderer renderer, ArticleBlacklist blacklist, boolean strict) {
        JSONObject subObj = (JSONObject) obj.get("__R");
        int rows = (Integer) obj.get("__R__ROWS");
        JSONObject userInfoMap = (JSONObject) obj.get("__U");
        if (subObj == null) {
            return new ArrayList<>();
        }
        String attachmentsPrefix = resolveAttachmentsPrefix(obj);
        return convertJsObjToList(subObj, rows, userInfoMap, attachmentsPrefix, renderer, blacklist, false, obj.getJSONObject("__T"), strict);
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

    private static List<ThreadRowInfo> convertJsObjToList(
            JSONObject rowMap,
            int count,
            JSONObject userInfoMap,
            String attachmentsPrefix, ArticleRowRenderer renderer, ArticleBlacklist blacklist,
            boolean comment, JSONObject topic, boolean strict) {
        List<ThreadRowInfo> rowList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Object obj = rowMap.get(String.valueOf(i));
            JSONObject rowObj;
            if (obj instanceof JSONObject) {
                rowObj = (JSONObject) obj;
            } else {
                if (strict) throw new ArticleFailure(ArticleFailureKind.CONTENT);
                continue;
            }
            boolean invalidContent = !isSourceScalar(rowObj.get("content"));
            boolean invalidSubject = !isSourceScalar(rowObj.get("subject"));
            JSONObject projected = rowObj;
            if (strict) {
                // Fastjson otherwise stringifies structured content into a seemingly valid body.
                projected = new JSONObject(new java.util.HashMap<>(rowObj));
                if (invalidContent) projected.remove("content");
                if (invalidSubject) projected.remove("subject");
                if (!isSourceScalar(projected.get("alterinfo"))) projected.remove("alterinfo");
            }
            ThreadRowInfo row = JSONObject.toJavaObject(projected, ThreadRowInfo.class);
            buildRowHotReplay(row, rowObj);
            buildRowComment(row, rowObj, userInfoMap, attachmentsPrefix, renderer, blacklist, topic, strict);
            buildRowClientInfo(row, rowObj);
            buildRowUserInfo(row, userInfoMap, blacklist);
            buildRowVote(row, rowObj);
            boolean sourceAvailable = !strict || !invalidContent && !invalidSubject
                    && (row.getContent() != null || row.getSubject() != null || !StringUtils.isEmpty(row.getAlterinfo()));
            Integer ownerUid = topic == null ? null : topic.getInteger("authorid");
            Boolean isOwner = row.getAuthorid() > 0 && !row.getISANONYMOUS() && ownerUid != null && ownerUid > 0
                    ? row.getAuthorid() == ownerUid : null;
            row.setPresentation(new ArticleRowPresentation(comment ? ArticleRowKind.COMMENT : ArticleRowKind.POST,
                    rowObj.get("lou") != null && row.getLou() >= 0, row.getAuthorid() > 0 && !row.getISANONYMOUS(),
                    true, sourceAvailable, isOwner));
            buildRowContent(row, attachmentsPrefix, renderer);
            rowList.add(row);
        }
        return rowList;
    }

    private static boolean isSourceScalar(Object value) {
        // Normal NGA source has historically allowed numeric content/subject tokens.
        return value == null || value instanceof String || value instanceof Number;
    }

    private static boolean hasCompleteSource(List<ThreadRowInfo> rows) {
        for (ThreadRowInfo row : rows) {
            if (!row.getPresentation().sourceAvailable
                    || row.getComments() != null && !hasCompleteSource(row.getComments())) return false;
        }
        return true;
    }

    private static void buildRowContent(ThreadRowInfo row, String attachmentsPrefix, ArticleRowRenderer renderer) {
        if (row.getContent() == null) {
            row.setContent(row.getSubject());
            row.setSubject(null);
        }
        if (!StringUtils.isEmpty(row.getFromClient())
                && row.getFromClient().startsWith("103 ")
                && !StringUtils.isEmpty(row.getContent())) {
            row.setContent(StringUtils.unescape(row.getContent()));
        }
        renderer.render(row, attachmentsPrefix);
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
            List<AttachmentData> attachments = new ArrayList<>();
            for (Map.Entry<String, Attachment> entry : row.getAttachs().entrySet()) {
                AttachmentData data = new AttachmentData();
                data.setAttachUrl(entry.getValue().getAttachurl());
                data.setThumb(entry.getValue().getThumb());
                attachments.add(data);
            }
            htmlData.setAttachmentList(attachments);
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

    private static void buildRowVote(ThreadRowInfo row, JSONObject rowObj) {
        String vote = rowObj.getString("vote");
        if (!StringUtils.isEmpty(vote)) {
            row.setVote(vote);
        }
    }

    //热门回复
    private static void buildRowHotReplay(ThreadRowInfo row, JSONObject rowObj) {
        String hotObj = rowObj.getString("17");
        if (hotObj != null) {
            row.hotReplies = new ArrayList<>();
            String[] hots = hotObj.split(",");
            for (String hot : hots) {
                if (!TextUtils.isEmpty(hot)) {
                    row.hotReplies.add(hot);
                }
            }
        }
    }

    //解析贴条
    private static void buildRowComment(
            ThreadRowInfo row,
            JSONObject rowObj,
            JSONObject userInfoMap,
            String attachmentsPrefix, ArticleRowRenderer renderer, ArticleBlacklist blacklist, JSONObject topic, boolean strict) {
        JSONObject commObj = (JSONObject) rowObj.get("comment");
        if (commObj != null) {
            row.setComments(convertJsObjToList(
                    commObj, commObj.size(), userInfoMap, attachmentsPrefix, renderer, blacklist, true, topic, strict));
        }
    }

    private static void buildRowClientInfo(ThreadRowInfo row, JSONObject rowObj) {
        String client = rowObj.getString("from_client");
        row.setFromClient(client);
        row.setFromClientModel(ArticleAuthorSupport.clientModel(client));
    }

    private static void buildRowUserInfo(ThreadRowInfo row, JSONObject userInfoMap, ArticleBlacklist blacklist) {
        if (row.getAuthorid() == 0 || userInfoMap == null) {
            return;
        }
        JSONObject userInfo = (JSONObject) userInfoMap.get(String.valueOf(row
                .getAuthorid()));
        JSONObject groupObj = userInfoMap.getJSONObject("__GROUPS");

        if (userInfo == null) {
            return;
        }
        int uid = row.getAuthorid();
        row.set_IsInBlackList(blacklist.contains(String.valueOf(uid)));
        String username = userInfo.getString("username");
        if (username != null && username.length() == 39 && username.startsWith("#anony_")) {
            row.setAuthor(ArticleAuthorSupport.anonymousName(username));
            row.setISANONYMOUS(true);
        } else {
            row.setAuthor(username);
        }
        row.setJs_escap_avatar(userInfo.getString("avatar"));
        row.setYz(userInfo.getString("yz"));
        row.setMuteTime(userInfo.getString("mute_time"));
        try {
            row.setAurvrc(Integer.valueOf(userInfo.getString("rvrc")));
        } catch (Exception e) {
            row.setAurvrc(0);
        }
        row.setSignature(userInfo.getString("signature"));

        try {
            row.setPostCount(userInfo.getString("postnum"));
            row.setReputation(Float.parseFloat(userInfo.getString("rvrc")) / 10.0f);
            row.setMemberGroup(groupObj.getJSONObject(userInfo.getString("memberid")).getString("0"));
        } catch (Exception e) {
        }

        JSONObject obj = userInfo.getJSONObject("buffs");
        if (obj != null) {
            for (String id : ForumConstants.BUFF_MUTE_IDS) {
                if (obj.containsKey(id)) {
                    row.setMuted(true);
                    break;
                }
            }
        }
    }

}
