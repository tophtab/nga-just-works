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
import sp.phone.mvp.model.thread.*;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.param.ArticleListParam;
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
    private boolean mForeground;
    private long mSequence;
    private ArticleRequestKey mActiveKey;
    private String mCacheError;

    @Override protected ArticleListModel onCreateModel() { return new ArticleListModel(); }

    private ArticleShareViewModel viewModel() { return mBaseView.getReaderViewModel(); }
    private ArticleReaderSession reader() { return viewModel().getReaderSession(); }
    private boolean compatEnabled() {
        return mBaseView != null && mBaseView.requireContext()
                .getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE)
                .getBoolean(mBaseView.getString(gov.anzong.androidnga.common.R.string.pref_show_with_app_api), false);
    }

    private boolean current(long sequence, ArticleRequestKey key, String fingerprint, boolean enabled) {
        return mBaseView != null && sequence == mSequence && mRequestParam.readerGeneration == key.generation
                && reader().accepts(key) && reader().environmentMatches(fingerprint, enabled)
                && fingerprint.equals(ArticleAccounts.fingerprint()) && enabled == compatEnabled();
    }

    private void finishSilently(long sequence) {
        if (sequence != mSequence) return;
        mPageRequestState.reset();
        mThreadData = null;
        if (mBaseView != null) {
            mBaseView.setRefreshing(false);
            mBaseView.hideLoadingView();
        }
    }

    private void showData(ThreadData data) {
        mThreadData = data;
        mCacheError = null;
        mPageRequestState.completeForegroundLoad();
        if (mBaseView != null) {
            mBaseView.setData(data);
            mBaseView.setRefreshing(false);
            mBaseView.hideLoadingView();
        }
    }

    private void finishError(String message, boolean browser) {
        if (mRequestParam.loadCache) mCacheError = message;
        mPageRequestState.failForegroundLoad(mThreadData != null);
        if (mBaseView != null) {
            mBaseView.setRefreshing(false);
            mBaseView.hideLoadingView();
            if (mForeground) {
                mBaseView.showToast(message);
                if (browser) showWithWebView();
            }
        }
    }

    @Override public void loadPage(ArticleListParam param) {
        mRequestParam = param;
        requestForegroundLoad(true);
    }

    public void onReaderChanged() {
        mSequence++;
        mActiveKey = null;
        mThreadData = null;
        mPageRequestState.reset();
        if (mForeground) requestForegroundLoad(false);
    }

    private void requestForegroundLoad(boolean explicitRefresh) {
        if (mBaseView == null || mRequestParam == null || mRequestParam.loadCache || !mForeground) return;
        final boolean enabled = compatEnabled();
        final String fingerprint = ArticleAccounts.fingerprint();
        final ArticleAccount account;
        try {
            account = enabled ? ArticleAccounts.capture() : null;
        } catch (ArticleFailure failure) {
            if (viewModel().ensureEnvironment(fingerprint, enabled, ArticleAccounts.currentOwner(),
                    mBaseModel.getOrigin(), mRequestParam.page)) return;
            finishError(failure.getMessage(), false);
            return;
        }
        if (viewModel().ensureEnvironment(fingerprint, enabled, account == null ? null : account.owner,
                mBaseModel.getOrigin(), mRequestParam.page)) return;
        if (mBaseView == null) return;
        if (mRequestParam.readerGeneration != reader().state().generation) return;
        final ArticleRequestKey key = reader().key(mRequestParam.page);
        if (mActiveKey != null && !mActiveKey.equals(key)) {
            mSequence++;
            mPageRequestState.reset();
            mThreadData = null;
        }
        mActiveKey = key;
        ThreadData handoff = explicitRefresh ? null : reader().takeHandoff(key.generation, key.page);
        if (handoff != null) {
            String notice = ArticleNavigation.handoffNotice(handoff, reader().state().pendingAnchor);
            mPageRequestState.requestForegroundLoad(false);
            showData(handoff);
            if (notice != null && mBaseView != null && mForeground) mBaseView.showToast(notice);
            return;
        }
        ArticlePageRequestState.ForegroundLoadDecision decision = mPageRequestState.requestForegroundLoad(explicitRefresh);
        if (decision == ArticlePageRequestState.ForegroundLoadDecision.SHOW_READY_DATA && mThreadData != null) {
            showData(mThreadData);
        }
        if (decision == ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH
                || decision == ArticlePageRequestState.ForegroundLoadDecision.NONE) {
            // Resume/repeated refresh must keep waiting for the current request, even with old data.
            mBaseView.setRefreshing(true);
        } else if (decision == ArticlePageRequestState.ForegroundLoadDecision.START) {
            mBaseView.setRefreshing(true);
            final long sequence = ++mSequence;
            if (enabled) {
                ArticleOperation operation = new ArticleOperation(key, reader().origin(), account,
                        com.justwen.androidnga.base.network.retrofit.RetrofitHelper.getInstance().getUserAgent(),
                        key.source, key.page);
                startScoped(operation, new ArticleAttemptPolicy(), sequence, fingerprint,
                        transitionAnchor(key), null);
            } else {
                String originalCookie = UserManagerImpl.getInstance().getCookie();
                mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), mHeaderMap,
                        new LegacyCallback(key, sequence, fingerprint, originalCookie, true));
            }
        }
    }

    private ArticleAnchor transitionAnchor(ArticleRequestKey key) {
        ArticleAnchor pending = reader().state().pendingAnchor;
        if (pending != null && pending.generation == key.generation && pending.page == key.page) return pending;
        ArticleAnchor visible = mBaseView.captureAnchor(key.generation, key.page);
        if (visible != null) return visible;
        if (key.query.pid > 0) return new ArticleAnchor(key.generation, key.page, key.query.pid, null);
        ArticlePagingInfo known = reader().state().paging;
        if (key.query.kind == ArticleQueryKind.FULL && key.pageSize != null
                && (key.source == ArticleSource.READ_PHP || known != null && known.canEstimateFloorPage)) {
            long floor = ((long) key.page - 1) * key.pageSize;
            if (floor >= 0 && floor <= Integer.MAX_VALUE) return new ArticleAnchor(key.generation, key.page, null, (int) floor);
        }
        return null;
    }

    private void startScoped(ArticleOperation operation, ArticleAttemptPolicy policy, long sequence,
                             String fingerprint, ArticleAnchor anchor, ThreadData alignmentFallback) {
        if (!current(sequence, operation.key, fingerprint, true) || !mForeground) {
            finishSilently(sequence);
            return;
        }
        mBaseModel.loadScopedPage(operation, new OnHttpCallBack<ThreadData>() {
            @Override public void onSuccess(ThreadData data) {
                if (!current(sequence, operation.key, fingerprint, true)) { finishSilently(sequence); return; }
                boolean layoutChanged = data.getPagingInfo().source != operation.key.source
                        || !java.util.Objects.equals(data.getPagingInfo().pageSize, operation.key.pageSize)
                        || data.getPagingInfo().effectivePage != operation.key.page;
                Integer candidate = layoutChanged ? ArticleNavigation.alignmentPage(anchor, data) : null;
                if (operation.source == ArticleSource.APP_API && policy.tryAlignment(candidate, mForeground, true)) {
                    startScoped(operation.forPage(candidate), policy, sequence, fingerprint, anchor, data);
                    return;
                }
                finishScoped(operation.key, data, anchor, layoutChanged);
            }
            @Override public void onError(String text) { onError(text, new ArticleFailure(ArticleFailureKind.PROTOCOL)); }
            @Override public void onError(String text, Throwable error) {
                if (!current(sequence, operation.key, fingerprint, true)) { finishSilently(sequence); return; }
                ArticleFailure failure = ArticleErrors.failure(error);
                // A refresh of an already adopted App source must never retry App as a fallback.
                if (operation.source == ArticleSource.READ_PHP &&
                        policy.tryApp(failure, mForeground, compatEnabled(), true)) {
                    startScoped(operation.forSource(ArticleSource.APP_API), policy, sequence, fingerprint, anchor, null);
                } else if (alignmentFallback != null && mForeground) {
                    finishScoped(operation.key, alignmentFallback, anchor, true);
                } else {
                    finishError(failure.getMessage(), failure.allowsBrowser());
                }
            }
        });
    }

    private void finishScoped(ArticleRequestKey key, ThreadData data, ArticleAnchor anchor, boolean layoutChanged) {
        if (anchor != null && layoutChanged && mForeground) {
            reader().setAnchor(new ArticleAnchor(key.generation, data.getPagingInfo().effectivePage, anchor.pid, anchor.floor));
        }
        long sequence = mSequence;
        int adoption = viewModel().adoptPage(key, data, mForeground);
        // LiveData observers may synchronously take the handoff or start a new request.
        if (adoption == 2 || sequence != mSequence || mBaseView == null
                || mRequestParam.readerGeneration != key.generation) return;
        if (adoption == 1) showData(data);
        else {
            mPageRequestState.reset();
            if (mBaseView != null) { mBaseView.setRefreshing(false); mBaseView.hideLoadingView(); }
        }
        if (adoption != 0 && mForeground && mBaseView != null) {
            if (!data.isContentComplete()) mBaseView.showToast("部分内容暂无法完整显示");
            else if (key.source != data.getPagingInfo().source) mBaseView.showToast(
                    anchor == null && key.page > 1 ? "已使用兼容模式显示，阅读位置可能变化" : "已使用兼容模式显示");
        }
    }

    private class LegacyCallback implements OnHttpCallBack<ThreadData> {
        private final ArticleRequestKey key;
        private final long sequence;
        private final String fingerprint;
        private final String originalCookie;
        private final boolean mayRetry;
        LegacyCallback(ArticleRequestKey key, long sequence, String fingerprint, String originalCookie, boolean mayRetry) {
            this.key = key; this.sequence = sequence; this.fingerprint = fingerprint;
            this.originalCookie = originalCookie; this.mayRetry = mayRetry;
        }
        @Override public void onSuccess(ThreadData data) {
            if (!current(sequence, key, fingerprint, false)) { finishSilently(sequence); return; }
            data.setPagingInfo(ArticlePagingInfo.normal(key.query, key.page, data).withRequest(null, key.generation));
            int adoption = viewModel().adoptPage(key, data, mForeground);
            if (adoption == 2 || sequence != mSequence || mBaseView == null
                    || mRequestParam.readerGeneration != key.generation) return;
            if (adoption == 1) showData(data);
            else {
                mPageRequestState.reset();
                if (mBaseView != null) { mBaseView.setRefreshing(false); mBaseView.hideLoadingView(); }
            }
        }
        @Override public void onError(String message) { onError(message, null); }
        @Override public void onError(String message, Throwable error) {
            if (!current(sequence, key, fingerprint, false)) { finishSilently(sequence); return; }
            if (mForeground && mayRetry && error instanceof ArticleListModel.ServerException && retryWithNewAccount()) return;
            finishError(message, error instanceof ArticleListModel.ServerException);
        }
        private boolean retryWithNewAccount() {
            UserManagerImpl manager = UserManagerImpl.getInstance();
            // Count before getNextCookie: zero accounts used to index -1; one simply repeated itself.
            String next = ArticleAttemptPolicy.retryCookie(manager.getUserSize(), originalCookie, manager::getNextCookie);
            if (next == null) return false;
            mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), java.util.Collections.singletonMap("Cookie", next),
                    new LegacyCallback(key, sequence, fingerprint, originalCookie, false));
            return true;
        }
    }

    @Override public void prefetchPage() {
        if (mBaseView == null || mRequestParam == null || mRequestParam.loadCache
                || mRequestParam.pid != 0 || mRequestParam.authorId != 0 || mRequestParam.searchPost != 0
                || mThreadData != null || !reader().state().canPrefetch()
                || mRequestParam.readerGeneration != reader().state().generation) return;
        final boolean enabled = compatEnabled();
        final String fingerprint = ArticleAccounts.fingerprint();
        if (!reader().environmentMatches(fingerprint, enabled)) return;
        final ArticleRequestKey key = reader().key(mRequestParam.page);
        if (key.source != ArticleSource.READ_PHP || !mPageRequestState.beginPrefetch()) return;
        mActiveKey = key;
        long sequence = ++mSequence;
        PrefetchCallback callback = new PrefetchCallback(key, sequence, fingerprint, enabled);
        if (enabled) {
            try {
                ArticleOperation operation = new ArticleOperation(key, reader().origin(), ArticleAccounts.capture(),
                        com.justwen.androidnga.base.network.retrofit.RetrofitHelper.getInstance().getUserAgent(),
                        ArticleSource.READ_PHP, key.page);
                mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), operation, callback);
            } catch (ArticleFailure failure) { callback.onError(failure.getMessage()); }
        } else {
            mBaseModel.loadPage((ArticleListParam) mRequestParam.clone(), mHeaderMap, callback);
        }
    }

    private class PrefetchCallback implements OnHttpCallBack<ThreadData> {
        private final ArticleRequestKey key;
        private final long sequence;
        private final String fingerprint;
        private final boolean enabled;
        PrefetchCallback(ArticleRequestKey key, long sequence, String fingerprint, boolean enabled) {
            this.key = key; this.sequence = sequence; this.fingerprint = fingerprint; this.enabled = enabled;
        }
        @Override public void onSuccess(ThreadData data) {
            if (!current(sequence, key, fingerprint, enabled)) { finishSilently(sequence); return; }
            if (data.getPagingInfo() == null) data.setPagingInfo(ArticlePagingInfo.normal(key.query, key.page, data));
            boolean wasPromoted = mPageRequestState.completePrefetch();
            int adoption = viewModel().adoptPage(key, data, wasPromoted && mForeground);
            if (adoption == 1) {
                mThreadData = data;
                if (mBaseView != null) {
                    mBaseView.setData(data);
                    if (wasPromoted) mBaseView.setRefreshing(false);
                    mBaseView.hideLoadingView();
                }
            } else if (adoption == 0) mPageRequestState.reset();
        }
        @Override public void onError(String text) { handlePrefetchFailure(); }
        @Override public void onError(String text, Throwable error) { handlePrefetchFailure(); }
        private void handlePrefetchFailure() {
            if (!current(sequence, key, fingerprint, enabled)) { finishSilently(sequence); return; }
            boolean wasPromoted = mPageRequestState.failPrefetch();
            if (wasPromoted && mForeground) requestForegroundLoad(false);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void movePrefetchToBackground() {
        mForeground = false;
        boolean wasPromoted = mPageRequestState.movePrefetchToBackground();
        if (mBaseView != null) mBaseView.setRefreshing(false);
    }

    private void showWithWebView() {
        if (mBaseView == null || !mForeground || !mBaseView.requireContext()
                .getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE)
                .getBoolean(mBaseView.getString(gov.anzong.androidnga.common.R.string.pref_show_with_webview), true)) return;
        ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                .withString("url", getCurrentUrl()).withString("title", mRequestParam.title)
                .withString("fragment", ForumWebFragment.class.getName()).navigation(mBaseView.getContext());
        mBaseView.finish();
    }

    private String getCurrentUrl() {
        String origin = reader().origin() == null ? Utils.getNGAHost() : reader().origin();
        return ArticleQuery.from(mRequestParam).browserUrl(origin, mRequestParam.page);
    }

    public ArticleListPresenter(ArticleListParam param) { mRequestParam = param; }
    public ArticleListPresenter() { }

    @Override
    public void banThisSB(ThreadRowInfo row) {
        if (!ArticleRowPresentation.hasUser(row)) {
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
        if (!ArticleRowPresentation.hasSource(row) || !ArticleRowPresentation.isPost(row)) return;
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = ArticleSourceText.normalizeReplyHeader(row.getContent())
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        String tidStr = String.valueOf(row.getTid());
        if (row.getPid() != 0) {
            postPrefix.append("[quote][pid=")
                    .append(ArticleNavigation.quoteAddress(row))
                    .append("]")// Topic
                    .append("Reply");
            postPrefix.append("[/pid] [b]Post by ")
                    .append(ArticleQuote.authorMarkup(row)).append(" (");
            postPrefix.append(postTime)
                    .append("):[/b]\n")
                    .append(content)
                    .append("[/quote]\n");
        }

        Bundle bundle = new Bundle();
        bundle.putInt("pid", row.getPid());
        bundle.putInt("fid", row.getFid());
        bundle.putInt("tid", row.getTid());

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
        if (!ArticleRowPresentation.canReply(row)) return;
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = ArticleSourceText.normalizeReplyHeader(row.getContent())
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        String mention = null;
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        String tidStr = String.valueOf(row.getTid());
        if (row.getPid() != 0) {
            mention = ArticleQuote.mention(row);
            postPrefix.append("[quote][pid=")
                    .append(ArticleNavigation.quoteAddress(row))
                    .append("]")// Topic
                    .append("Reply");
            postPrefix.append("[/pid] [b]Post by ")
                    .append(ArticleQuote.authorMarkup(row)).append(" (");
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
        if (mThreadData != null && mBaseView != null && mForeground && mActiveKey != null
                && current(mSequence, mActiveKey, ArticleAccounts.fingerprint(), compatEnabled())) {
            ArticleListParam cacheParam = ArticlePageCache.prepare(mRequestParam, mThreadData);
            if (cacheParam == null) {
                ToastUtils.error("缓存失败！");
                return;
            }
            long sequence = mSequence;
            long generation = mRequestParam.readerGeneration;
            mBaseModel.cachePage(cacheParam, mThreadData, new OnHttpCallBack<String>() {
                private boolean valid() {
                    return mBaseView != null && mForeground && sequence == mSequence
                            && generation == mRequestParam.readerGeneration;
                }
                @Override public void onSuccess(String message) { if (valid()) mBaseView.showToast(message); }
                @Override public void onError(String message) { if (valid()) mBaseView.showToast(message); }
            });
        }
    }

    @Override
    public void loadCachePage() {

    }

    @Override
    public void onViewCreated() {
        if (mRequestParam != null && mRequestParam.loadCache) {
            long sequence = ++mSequence;
            mBaseModel.loadCachePage(mRequestParam, new OnHttpCallBack<ThreadData>() {
                private boolean valid() {
                    return mBaseView != null && sequence == mSequence && (mRequestParam.cacheOwner == null
                            || mRequestParam.cacheOwner.equals(ArticleAccounts.currentOwner()));
                }
                @Override public void onSuccess(ThreadData data) {
                    if (valid()) showData(data); else finishSilently(sequence);
                }
                @Override public void onError(String text) {
                    if (valid()) finishError(text, false); else finishSilently(sequence);
                }
            });
        }
    }

    @Override protected void onResume() {
        mForeground = true;
        if (mRequestParam != null && !mRequestParam.loadCache) requestForegroundLoad(false);
        else if (mThreadData != null && (mRequestParam.cacheOwner == null
                || mRequestParam.cacheOwner.equals(ArticleAccounts.currentOwner()))) showData(mThreadData);
        else if (mRequestParam != null && mRequestParam.loadCache && mCacheError != null) {
            String error = mCacheError;
            mCacheError = null;
            finishError(error, false);
            mCacheError = null;
        }
        super.onResume();
    }

    @Override protected void onDestroy() {
        mSequence++;
        mPageRequestState.reset();
        mThreadData = null;
        super.onDestroy();
    }
}
