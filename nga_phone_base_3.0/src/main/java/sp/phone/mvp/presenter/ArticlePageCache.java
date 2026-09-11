package sp.phone.mvp.presenter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;

import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ArticleListParam;

/** Eligibility and metadata preparation for saving one full online thread page. */
public final class ArticlePageCache {

    private ArticlePageCache() {
    }

    public static boolean isCacheableContext(ArticleListParam param) {
        // The parent pager may not have a page yet; only its children are 1-based.
        return param != null
                && param.tid > 0
                && param.pid == 0
                && param.authorId == 0
                && param.searchPost == 0
                && !param.loadCache;
    }

    static ArticleListParam prepare(ArticleListParam param, ThreadData data) {
        if (!isCacheableContext(param) || param.page < 1
                || data == null || isBlank(data.getRawData())) {
            return null;
        }

        ThreadPageInfo loadedInfo = data.getThreadInfo();
        if (loadedInfo != null && loadedInfo.getTid() != param.tid) {
            return null;
        }

        String topicInfo = param.topicInfo;
        if (isBlank(topicInfo)) {
            if (!isUsableDescription(loadedInfo, param.tid)) {
                return null;
            }
            topicInfo = JSON.toJSONString(loadedInfo);
        } else {
            try {
                if (!isUsableDescription(JSON.parseObject(topicInfo, ThreadPageInfo.class), param.tid)) {
                    return null;
                }
            } catch (JSONException e) {
                return null;
            }
        }

        // The model writes asynchronously; never lend it the live pager parameters.
        ArticleListParam cacheParam = (ArticleListParam) param.clone();
        if (cacheParam != null) {
            cacheParam.topicInfo = topicInfo;
        }
        return cacheParam;
    }

    private static boolean isUsableDescription(ThreadPageInfo info, int tid) {
        return info != null && info.getTid() == tid && !isBlank(info.getSubject());
    }

    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        for (int offset = 0; offset < trimmed.length(); ) {
            int codePoint = trimmed.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
