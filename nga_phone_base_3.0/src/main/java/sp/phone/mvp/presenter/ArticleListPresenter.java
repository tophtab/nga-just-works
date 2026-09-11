package sp.phone.mvp.presenter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.OnLifecycleEvent;

import com.justwen.androidnga.base.activity.ARouterConstants;

import java.util.Map;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.activity.fragment.ForumWebFragment;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.common.UserManager;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.BaseSubscriber;
import sp.phone.rxjava.RxUtils;
import sp.phone.task.LikeTask;
import sp.phone.ui.fragment.ArticleListFragment;
import sp.phone.util.ARouterUtils;
import sp.phone.util.FunctionUtils;
import sp.phone.util.StringUtils;

/**
 * Created by Justwen on 2017/11/22.
 */

public class ArticleListPresenter extends BasePresenter<ArticleListFragment, ArticleListModel> implements ArticleListContract.Presenter {

    private LikeTask mLikeTask;

    private ThreadData mThreadData;

    private ArticleListParam mRequestParam;

    private final Map<String, String> mHeaderMap = new ArrayMap<>();

    private final ArticlePageRequestState mPageRequestState = new ArticlePageRequestState();

    private class ArticleCallback implements OnHttpCallBack<ThreadData> {
        @Override
        public void onError(String text) {
            mPageRequestState.failForegroundLoad(mThreadData != null);
            if (mBaseView != null) {
                mBaseView.hideLoadingView();
                mBaseView.setRefreshing(false);
                mBaseView.showToast(text);
            }
        }

        @Override
        public void onError(String msg, Throwable t) {
            onError(msg);
            if (t instanceof ArticleListModel.ServerException) {
                showWithWebView();
            }
        }

        @Override
        public void onSuccess(ThreadData data) {
            mThreadData = data;
            mPageRequestState.completeForegroundLoad();
            if (mBaseView != null) {
                mBaseView.setRefreshing(false);
                mBaseView.setData(data);
                RxUtils.postDelay(300, new BaseSubscriber<Long>() {
                    @Override
                    public void onNext(Long aLong) {
                        if (mBaseView != null) {
                            mBaseView.hideLoadingView();
                        }
                    }
                });
            }
        }
    };

    private class RetryCallback extends ArticleCallback {

        @Override
        public void onError(String msg, Throwable t) {
            if (!(t instanceof ArticleListModel.ServerException) || !retryWithNewAccount()) {
                super.onError(msg, t);
            }
        }
    }

    private class PrefetchCallback implements OnHttpCallBack<ThreadData> {

        @Override
        public void onError(String text) {
            handlePrefetchFailure();
        }

        @Override
        public void onError(String msg, Throwable t) {
            handlePrefetchFailure();
        }

        @Override
        public void onSuccess(ThreadData data) {
            boolean wasPromoted = mPageRequestState.completePrefetch();
            mThreadData = data;
            if (mBaseView != null) {
                if (wasPromoted) {
                    mBaseView.setRefreshing(false);
                }
                mBaseView.setData(data);
                mBaseView.hideLoadingView();
            }
        }
    }

    private final OnHttpCallBack<ThreadData> mRetryCallback = new RetryCallback();

    private final OnHttpCallBack<ThreadData> mDataCallBack = new ArticleCallback();

    private final OnHttpCallBack<ThreadData> mPrefetchCallback = new PrefetchCallback();

    @Override
    protected ArticleListModel onCreateModel() {
        return new ArticleListModel();
    }

    private boolean retryWithNewAccount() {
        if (mBaseView == null) {
            return false;
        }
        String cookie = UserManagerImpl.getInstance().getNextCookie();
        if (cookie == null) {
            return false;
        }
        Map<String, String> header = new ArrayMap<>();
        header.put("Cookie", cookie);
        mBaseModel.loadPage(mRequestParam, header, mDataCallBack);
        return true;
    }

    @Override
    public void loadPage(ArticleListParam param) {
        mRequestParam = param;
        requestForegroundLoad(true);
    }

    @Override
    public void prefetchPage() {
        if (mBaseView == null
                || mRequestParam == null
                || mRequestParam.loadCache
                || mThreadData != null
                || !mPageRequestState.beginPrefetch()) {
            return;
        }
        mBaseModel.loadPage(mRequestParam, mHeaderMap, mPrefetchCallback);
    }

    private void handlePrefetchFailure() {
        boolean wasPromoted = mPageRequestState.failPrefetch();
        if (wasPromoted) {
            requestForegroundLoad(false);
        }
    }

    private void requestForegroundLoad(boolean explicitRefresh) {
        if (mBaseView == null || mRequestParam == null) {
            return;
        }
        ArticlePageRequestState.ForegroundLoadDecision decision =
                mPageRequestState.requestForegroundLoad(explicitRefresh);
        if (decision == ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH) {
            mBaseView.setRefreshing(true);
        } else if (decision == ArticlePageRequestState.ForegroundLoadDecision.START) {
            mBaseView.setRefreshing(true);
            mBaseModel.loadPage(mRequestParam, mHeaderMap, mRetryCallback);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void movePrefetchToBackground() {
        boolean wasPromoted = mPageRequestState.movePrefetchToBackground();
        if (wasPromoted && mBaseView != null) {
            mBaseView.setRefreshing(false);
        }
    }

    private void showWithWebView() {
        if (mBaseView == null || !mBaseView.getContext().getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE).getBoolean(mBaseView.getString(gov.anzong.androidnga.common.R.string.pref_show_with_webview), true)) {
            return;
        }
        ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                .withString("url", getCurrentUrl())
                .withString("title", mRequestParam.title)
                .withString("fragment", ForumWebFragment.class.getName())
                .navigation(mBaseView.getContext());
        mBaseView.finish();
    }

    private String getCurrentUrl() {
        StringBuilder builder = new StringBuilder();
        builder.append(Utils.getNGAHost()).append("read.php?");
        if (mRequestParam.pid != 0) {
            builder.append("pid=").append(mRequestParam.pid);
        } else {
            builder.append("tid=").append(mRequestParam.tid);
        }
        return builder.toString();
    }

    public ArticleListPresenter(ArticleListParam articleListParam) {
        mRequestParam = articleListParam;
    }

    public ArticleListPresenter() {
    }

    @Override
    public void banThisSB(ThreadRowInfo row) {
        if (row.getISANONYMOUS()) {
            mBaseView.showToast(R.string.cannot_add_to_blacklist_cause_anony);
        } else {
            UserManager um = UserManagerImpl.getInstance();
            if (row.get_isInBlackList()) {
                row.set_IsInBlackList(false);
                um.removeFromBlackList(String.valueOf(row.getAuthorid()));
                mBaseView.showToast(R.string.remove_from_blacklist_success);
            } else {
                row.set_IsInBlackList(true);
                um.addToBlackList(row.getAuthor(), String.valueOf(row.getAuthorid()));
                mBaseView.showToast(R.string.add_to_blacklist_success);
            }
        }
    }

    @Override
    public void postComment(ArticleListParam param, ThreadRowInfo row) {
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = row.getContent()
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        final String name = row.getAuthor();
        final String uid = String.valueOf(row.getAuthorid());
        String tidStr = String.valueOf(param.tid);
        if (row.getPid() != 0) {
            postPrefix.append("[quote][pid=")
                    .append(row.getPid())
                    .append(',').append(tidStr).append(",").append(param.page)
                    .append("]")// Topic
                    .append("Reply");
            if (row.getISANONYMOUS()) {// 是匿名的人
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append("-1")
                        .append("]")
                        .append(name)
                        .append("[/uid][color=gray](")
                        .append(row.getLou())
                        .append("楼)[/color] (");
            } else {
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append(uid)
                        .append("]")
                        .append(name)
                        .append("[/uid] (");
            }
            postPrefix.append(postTime)
                    .append("):[/b]\n")
                    .append(content)
                    .append("[/quote]\n");
        }

        Bundle bundle = new Bundle();
        bundle.putInt("pid", row.getPid());
        bundle.putInt("fid", row.getFid());
        bundle.putInt("tid", param.tid);

        String prefix = StringUtils.removeBrTag(postPrefix.toString());
        if (!StringUtils.isEmpty(prefix)) {
            prefix = prefix + "\n";
        }
        mBaseView.showPostCommentDialog(prefix, bundle);
    }

    @Override
    public void postSupportTask(int tid, int pid) {
        if (mLikeTask == null) {
            mLikeTask = new LikeTask();
        }
        mLikeTask.execute(tid, pid, LikeTask.SUPPORT, ToastUtils::success);
    }

    @Override
    public void postOpposeTask(int tid, int pid) {
        if (mLikeTask == null) {
            mLikeTask = new LikeTask();
        }
        mLikeTask.execute(tid, pid, LikeTask.OPPOSE, ToastUtils::success);
    }

    @Override
    public void quote(ArticleListParam param, ThreadRowInfo row) {
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = row.getContent()
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        String mention = null;
        final String name = row.getAuthor();
        final String uid = String.valueOf(row.getAuthorid());
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        String tidStr = String.valueOf(param.tid);
        if (row.getPid() != 0) {
            mention = name;
            postPrefix.append("[quote][pid=")
                    .append(row.getPid())
                    .append(',').append(tidStr).append(",").append(param.page)
                    .append("]")// Topic
                    .append("Reply");
            if (row.getISANONYMOUS()) {// 是匿名的人
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append("-1")
                        .append("]")
                        .append(name)
                        .append("[/uid][color=gray](")
                        .append(row.getLou())
                        .append("楼)[/color] (");
            } else {
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append(uid)
                        .append("]")
                        .append(name)
                        .append("[/uid] (");
            }
            postPrefix.append(postTime)
                    .append("):[/b]\n")
                    .append(content)
                    .append("[/quote]\n");
        }

        Intent intent = new Intent();
        if (!StringUtils.isEmpty(mention)) {
            intent.putExtra("mention", mention);
        }
        intent.putExtra("prefix", StringUtils.removeBrTag(postPrefix.toString()));
        intent.putExtra("tid", tidStr);
        intent.putExtra("action", "reply");
        mBaseView.startPostActivity(intent);
    }

    @Override
    public void cachePage() {
        if (mThreadData != null) {
            ArticleListParam cacheParam = ArticlePageCache.prepare(mRequestParam, mThreadData);
            if (cacheParam == null) {
                ToastUtils.error("缓存失败！");
                return;
            }
            mBaseModel.cachePage(cacheParam, mThreadData.getRawData());
        }
    }

    @Override
    public void loadCachePage() {

    }

    @Override
    public void onViewCreated() {
        if (mRequestParam != null && mRequestParam.loadCache) {
            mBaseModel.loadCachePage(mRequestParam, mDataCallBack);
        }
    }

    @Override
    protected void onResume() {
        if (mRequestParam != null && !mRequestParam.loadCache) {
            requestForegroundLoad(false);
        }
        super.onResume();
    }
}
