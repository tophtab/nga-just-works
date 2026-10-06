package sp.phone.mvp.model.entity;

import androidx.annotation.NonNull;
import com.alibaba.fastjson2.annotation.JSONField;
import java.util.Objects;
import sp.phone.mvp.model.thread.ArticleCacheEntry;

import gov.anzong.androidnga.common.base.JavaBean;

public class ThreadPageInfo implements JavaBean {

    private int mTid;

    private String mAuthor;

    private int mFid;

    private int mAuthorId;

    private String mLastPoster;

    private int mReplies;

    private String mSubject;

    private String mTitleFont;

    private int mType;

    private String mTopicMisc;

    private int mPage;

    private int mPid;

    private int mPosition;

    private boolean mIsAnonymity;

    private int mPostDate;

    private ReplyInfo mReplyInfo;

    private String mBoard;
    private transient ArticleCacheEntry mCacheEntry;

    @JSONField(serialize = false, deserialize = false)
    public ArticleCacheEntry getCacheEntry() { return mCacheEntry; }
    @JSONField(serialize = false, deserialize = false)
    public void setCacheEntry(ArticleCacheEntry entry) { mCacheEntry = entry; }

    /**
     * 是否是版面镜像
     */
    private boolean mMirrorBoard;

    public boolean isMirrorBoard() {
        return mMirrorBoard;
    }

    @JSONField(alternateNames = {"postdate"})
    public int getPostDate() {
        return mPostDate;
    }

    @JSONField(alternateNames = {"postdate"})
    public void setPostDate(int postDate) {
        mPostDate = postDate;
    }

    public int getTid() {
        return mTid;
    }

    public void setTid(int tid) {
        mTid = tid;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public void setAuthor(String author) {
        mAuthor = author;
    }

    public int getFid() {
        return mFid;
    }

    public void setFid(int fid) {
        mFid = fid;
    }

    @JSONField(alternateNames = {"authorid"})
    public int getAuthorId() {
        return mAuthorId;
    }

    @JSONField(alternateNames = {"authorid"})
    public void setAuthorId(int authorId) {
        mAuthorId = authorId;
    }

    @JSONField(alternateNames = {"lastposter"})
    public String getLastPoster() {
        return mLastPoster;
    }

    @JSONField(alternateNames = {"lastposter"})
    public void setLastPoster(String lastPoster) {
        mLastPoster = lastPoster;
    }

    public int getReplies() {
        return mReplies;
    }

    public void setReplies(int replies) {
        mReplies = replies;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        mSubject = subject;
    }

    @JSONField(alternateNames = {"titlefont"})
    public String getTitleFont() {
        return mTitleFont;
    }

    @JSONField(alternateNames = {"titlefont"})
    public void setTitleFont(String titleFont) {
        mTitleFont = titleFont;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    @JSONField(alternateNames = {"topic_misc"})
    public String getTopicMisc() {
        return mTopicMisc;
    }

    @JSONField(alternateNames = {"topic_misc"})
    public void setTopicMisc(String topicMisc) {
        mTopicMisc = topicMisc;
    }

    public int getPage() {
        return mPage;
    }

    public void setPage(int page) {
        mPage = page;
    }

    public int getPid() {
        return mPid;
    }

    public void setPid(int pid) {
        mPid = pid;
    }

    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public boolean isAnonymity() {
        return mIsAnonymity;
    }

    public void setAnonymity(boolean anonymity) {
        mIsAnonymity = anonymity;
    }

    public ReplyInfo getReplyInfo() {
        return mReplyInfo;
    }

    public void setReplyInfo(ReplyInfo replyInfo) {
        mReplyInfo = replyInfo;
    }

    public String getBoard() {
        return mBoard;
    }

    public void setBoard(String parentBoard) {
        mBoard = parentBoard;
        mMirrorBoard = "版面镜像".equals(parentBoard);
    }

    public static class ReplyInfo implements JavaBean {

        private String mPidStr;

        private String mContent;

        private String mSubject;

        private String mPostDate;

        private String mAuthorId;

        private String mTidStr;

        public String getPidStr() {
            return mPidStr;
        }

        public void setPidStr(String pidStr) {
            mPidStr = pidStr;
        }

        public String getContent() {
            return mContent;
        }

        public void setContent(String content) {
            mContent = content;
        }

        public String getSubject() {
            return mSubject;
        }

        public void setSubject(String subject) {
            mSubject = subject;
        }

        public String getPostDate() {
            return mPostDate;
        }

        public void setPostDate(String postDate) {
            mPostDate = postDate;
        }

        public String getAuthorId() {
            return mAuthorId;
        }

        public void setAuthorId(String authorId) {
            mAuthorId = authorId;
        }

        public String getTidStr() {
            return mTidStr;
        }

        public void setTidStr(String tidStr) {
            mTidStr = tidStr;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ThreadPageInfo
                && mTid == ((ThreadPageInfo) obj).getTid()
                && mPid == ((ThreadPageInfo) obj).getPid()
                && Objects.equals(mCacheEntry, ((ThreadPageInfo) obj).mCacheEntry);
    }

    @Override public int hashCode() { return Objects.hash(mTid, mPid, mCacheEntry); }

    @NonNull
    @Override
    public String toString() {
        return "tid = " + mTid + "  pid = " + mPid;
    }
}
