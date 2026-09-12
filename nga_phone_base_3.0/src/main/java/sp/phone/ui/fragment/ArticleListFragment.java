package sp.phone.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.android.arouter.launcher.ARouter;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.common.PreferenceKey;
import sp.phone.ai.summary.FloorSummaryInput;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.User;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.presenter.ArticleListPresenter;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.mvp.model.thread.*;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.profile.AuthorLocationService;
import sp.phone.ui.adapter.ArticleListAdapter;
import sp.phone.ui.fragment.dialog.BaseDialogFragment;
import sp.phone.ui.fragment.dialog.AiSummaryDialog;
import sp.phone.ui.fragment.dialog.PostCommentDialogFragment;
import sp.phone.util.ActivityUtils;
import sp.phone.util.FunctionUtils;
import gov.anzong.androidnga.common.util.NLog;
import sp.phone.util.StringUtils;
import sp.phone.view.LoadingLayout;
import sp.phone.view.RecyclerViewEx;

/*
 * MD 帖子详情每一页
 */
public class ArticleListFragment extends BaseMvpFragment<ArticleListPresenter> implements ArticleListContract.View {

    private static final String TAG = ArticleListFragment.class.getSimpleName();

    @BindView(R.id.list)
    public RecyclerViewEx mListView;

    @BindView(R.id.loading_view)
    public LoadingLayout mLoadingView;

    @BindView(R.id.swipe_refresh)
    public SwipeRefreshLayout mSwipeRefreshLayout;

    private ArticleListAdapter mArticleAdapter;

    private AuthorLocationService.Page mAuthorLocations;

    private Unbinder mViewBindings;

    private ThreadData mDeliveredData;

    protected ArticleListParam mRequestParam;
    private ThreadData mDisplayedData;
    private String mDisplayedTopicOwner;

    private AiSummaryDialog mAiSummaryDialog;

    private String mAiThreadTitle;

    private long mAiContentGeneration;

    private OnTopicMenuItemClickListener mMenuItemClickListener = new OnTopicMenuItemClickListener() {

        private ThreadRowInfo mThreadRowInfo;

        @Override
        public void setThreadRowInfo(ThreadRowInfo threadRowInfo) {
            mThreadRowInfo = threadRowInfo;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mPresenter == null) {
                return false;
            }

            ThreadRowInfo row = mThreadRowInfo;

            String pidStr = String.valueOf(row.getPid());
            String tidStr = String.valueOf(row.getTid());
            int tid = row.getTid();

            switch (item.getItemId()) {
                case R.id.menu_ai_summary:
                    showAiSummary(row);
                    break;
                case R.id.menu_edit:
                    if (FunctionUtils.isComment(row) || !ArticleRowPresentation.isPost(row) || !ArticleRowPresentation.hasSource(row)) {
                        showToast(R.string.cannot_eidt_comment);
                        break;
                    } else {
                        ARouter.getInstance()
                                .build(ARouterConstants.ACTIVITY_POST)
                                .withString(ParamKey.KEY_PID, pidStr)
                                .withString(ParamKey.KEY_TID, tidStr)
                                .withString("title", StringUtils.unEscapeHtml(row.getSubject()))
                                .withString("action", "modify")
                                .withString("prefix", StringUtils.unEscapeHtml(StringUtils.removeBrTag(row.getContent())))
                                .navigation(getActivity(), ActivityUtils.REQUEST_CODE_LOGIN);
                    }
                    break;
                case R.id.menu_post_comment:
                    mPresenter.postComment(mRequestParam, row);
                    break;
                case R.id.menu_report:
                    FunctionUtils.handleReport(row, row.getTid(), getFragmentManager());
                    break;
                case R.id.menu_vote:
                    FunctionUtils.createVoteDialog(row, getActivity(), mListView, mToast);
                    break;
                case R.id.menu_ban_this_one:
                    boolean wasBlacklisted = row.get_isInBlackList();
                    mPresenter.banThisSB(row);
                    if (wasBlacklisted != row.get_isInBlackList()
                            && mDisplayedData != null && mArticleAdapter != null) {
                        int position = mDisplayedData.getRowList().indexOf(row);
                        if (position >= 0) mArticleAdapter.notifyItemChanged(position);
                    }
                    break;
                case R.id.menu_show_this_person_only:
                    ARouter.getInstance()
                            .build(ARouterConstants.ACTIVITY_TOPIC_CONTENT)
                            .withString("tab", "1")
                            .withInt(ParamKey.KEY_TID, tid)
                            .withInt(ParamKey.KEY_AUTHOR_ID, row.getAuthorid())
                            .withInt("fromreplyactivity", 1)
                            .navigation();
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    private View.OnClickListener mMenuTogglerListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            final ThreadRowInfo clickedRow = (ThreadRowInfo) view.getTag();
            final long menuContentGeneration = mAiContentGeneration;
            mMenuItemClickListener.setThreadRowInfo(clickedRow);
            int menuId;
            if (mRequestParam.pid == 0) {
                menuId = R.menu.article_list_context_menu;
            } else {
                menuId = R.menu.article_list_context_menu_with_tid;
            }
            PopupMenu popupMenu = new PopupMenu(getContext(), view);
            popupMenu.inflate(menuId);
            onPrepareOptionsMenu(popupMenu.getMenu(), (ThreadRowInfo) view.getTag());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_ai_summary
                        && menuContentGeneration != mAiContentGeneration) {
                    return true;
                }
                mMenuItemClickListener.setThreadRowInfo(clickedRow);
                return mMenuItemClickListener.onMenuItemClick(item);
            });
        }

        private void onPrepareOptionsMenu(Menu menu, ThreadRowInfo row) {
            boolean userKnown = ArticleRowPresentation.hasUser(row);
            boolean ordinary = ArticleRowPresentation.isPost(row) && !FunctionUtils.isComment(row);
            MenuItem summaryItem = menu.findItem(R.id.menu_ai_summary);
            if (summaryItem != null) summaryItem.setEnabled(ArticleRowPresentation.hasSource(row));
            MenuItem commentItem = menu.findItem(R.id.menu_post_comment);
            if (commentItem != null) commentItem.setVisible(ordinary && ArticleRowPresentation.hasSource(row));
            MenuItem authorItem = menu.findItem(R.id.menu_show_this_person_only);
            if (authorItem != null) authorItem.setVisible(ordinary && userKnown);
            MenuItem item = menu.findItem(R.id.menu_ban_this_one);
            if (item != null) {
                item.setVisible(userKnown);
                item.setTitle(row.get_isInBlackList() ? R.string.cancel_ban_thisone : R.string.ban_thisone);
            }

            item = menu.findItem(R.id.menu_vote);
            if (item != null && StringUtils.isEmpty(row.getVote())) {
                item.setVisible(false);
            }

            item = menu.findItem(R.id.menu_edit);
            if (item != null) {
                User user = UserManagerImpl.getInstance().getActiveUser();
                if (!ordinary || !userKnown || !ArticleRowPresentation.hasSource(row)
                        || user == null || !user.getUserId().equals(String.valueOf(row.getAuthorid()))) {
                    item.setVisible(false);
                }
            }
        }

    };

    private View.OnClickListener mSupportListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadRowInfo row = ((ThreadRowInfo) view.getTag());
            mPresenter.postSupportTask(row.getTid(), row.getPid());
        }
    };

    private View.OnClickListener mOpposeListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadRowInfo row = ((ThreadRowInfo) view.getTag());
            mPresenter.postOpposeTask(row.getTid(), row.getPid());
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        NLog.d(TAG, "onCreate");
        mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        mRequestParam.page = Math.max(1, mRequestParam.page);
        if (!mRequestParam.loadCache) {
            ArticleReaderSession session = getReaderViewModel().initializeReader(mRequestParam);
            if (mRequestParam.readerGeneration == 0 || session.isUnbound()) mRequestParam.readerGeneration = session.state().generation;
        }
        registerRxBus();

        initData();
        super.onCreate(savedInstanceState);
    }

    private void initData() {
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        viewModel.getRefreshPage().observe(this, page -> {
            if (page == mRequestParam.page) {
                loadPage();
            }
        });

        viewModel.getCachePage().observe(this, page -> {
            if (page == mRequestParam.page) {
                mPresenter.cachePage();
            }
        });

        if (!mRequestParam.loadCache) {
            viewModel.getReaderState().observe(this, state -> {
                if (getParentFragment() == null && mRequestParam.readerGeneration != state.generation) {
                    dismissAiSummary();
                    mAiContentGeneration++;
                    mAiThreadTitle = null;
                    mRequestParam.readerGeneration = state.generation;
                    mRequestParam.page = state.currentPage;
                    mDeliveredData = null;
                    mDisplayedData = null;
                    if (mAuthorLocations != null) mAuthorLocations.deliver(null, false);
                    if (mArticleAdapter != null) {
                        mArticleAdapter.setData(null);
                        mArticleAdapter.notifyDataSetChanged();
                    }
                    if (mPresenter != null) mPresenter.onReaderChanged();
                }
                consumePendingAnchor();
            });
        }
        if (isOnlineTopicPagerPage()) {
            viewModel.getPrefetchPages().observe(this, pages -> {
                if (pages != null && pages.contains(mRequestParam.page)
                        && mRequestParam.readerGeneration == viewModel.getReaderSession().state().generation
                        && viewModel.getReaderSession().state().canPrefetch()) {
                    mPresenter.prefetchPage();
                }
            });
        }
    }

    private boolean isOnlineTopicPagerPage() {
        return !mRequestParam.loadCache
                && mRequestParam.searchPost == 0
                && mRequestParam.pid == 0 && mRequestParam.authorId == 0
                && getParentFragment() instanceof ArticleTabFragment;
    }

    public ArticleShareViewModel getReaderViewModel() {
        return getActivityViewModelProvider().get(ArticleShareViewModel.class);
    }

    public int getRequestPage() { return mRequestParam.page; }
    public long getReaderGeneration() { return mRequestParam.readerGeneration; }
    public ArticleListParam fullThreadParam() { return ArticleNavigation.showAll(mRequestParam, mDisplayedData); }
    public boolean hasCompletePage() { return mDisplayedData != null && mDisplayedData.isContentComplete(); }
    public boolean containsFloor(int floor) {
        return mDisplayedData != null && new ArticleAnchor(mRequestParam.readerGeneration,
                mRequestParam.page, null, floor).find(mDisplayedData.getRowList()) >= 0;
    }
    public Integer maxKnownFloor() {
        Integer maximum = null;
        if (mDisplayedData != null) for (ThreadRowInfo row : mDisplayedData.getRowList()) {
            if (ArticleRowPresentation.hasFloor(row) && (maximum == null || row.getLou() > maximum)) maximum = row.getLou();
        }
        return maximum;
    }

    public ArticleAnchor captureAnchor(long generation, int page) {
        if (mDisplayedData == null || mDisplayedData.getPagingInfo() == null
                || mDisplayedData.getPagingInfo().generation != generation || mListView == null
                || !(mListView.getLayoutManager() instanceof LinearLayoutManager)) return null;
        int index = ((LinearLayoutManager) mListView.getLayoutManager()).findFirstVisibleItemPosition();
        if (index < 0 || index >= mDisplayedData.getRowList().size()) return null;
        ThreadRowInfo row = mDisplayedData.getRowList().get(index);
        return new ArticleAnchor(generation, page, row.getPid() > 0 ? row.getPid() : null,
                ArticleRowPresentation.hasFloor(row) ? row.getLou() : null);
    }

    private void consumePendingAnchor() {
        if (mRequestParam.loadCache || mDisplayedData == null || mListView == null || !isResumed()) return;
        ArticleReaderSession reader = getReaderViewModel().getReaderSession();
        if (mDisplayedData.getPagingInfo() == null || mDisplayedData.getPagingInfo().generation != reader.state().generation) return;
        ArticleAnchor anchor = reader.consumeAnchor(mRequestParam.readerGeneration, mRequestParam.page);
        if (anchor == null) return;
        int index = anchor.find(mDisplayedData.getRowList());
        if (index >= 0) {
            ThreadData displayed = mDisplayedData;
            mListView.post(() -> {
                if (mListView != null && isResumed() && mDisplayedData == displayed
                        && mRequestParam.readerGeneration == reader.state().generation
                        && anchor.find(displayed.getRowList()) == index) mListView.scrollToPosition(index);
            });
        } else showToast("未找到目标回复或楼层，阅读位置未能保留");
    }

    @Override public void onResume() {
        super.onResume();
        consumePendingAnchor();
        if (getActivity() != null) getActivity().invalidateOptionsMenu();
    }

    @Override
    protected ArticleListPresenter onCreatePresenter() {
        return new ArticleListPresenter(mRequestParam);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mViewBindings = ButterKnife.bind(this, view);
        mLoadingView.bindToLifecycle(getViewLifecycleOwner());
        ((BaseActivity) getActivity()).setupToolbar();
        mArticleAdapter = new ArticleListAdapter(getContext(),getActivity().getSupportFragmentManager());
        mArticleAdapter.setSupportListener(mSupportListener);
        mArticleAdapter.setOpposeListener(mOpposeListener);
        mArticleAdapter.setMenuTogglerListener(mMenuTogglerListener);
        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mListView.setItemViewCacheSize(20);
        mListView.setAdapter(mArticleAdapter);
        mAuthorLocations = AuthorLocationService.bind(getContext(), getViewLifecycleOwner(),
                mArticleAdapter::setAuthorLocations);
        mListView.setEmptyView(view.findViewById(R.id.empty_view));
        applyReplyFabClearance();
        if (PhoneConfiguration.getInstance().useSolidColorBackground()) {
            mListView.addItemDecoration(new DividerItemDecoration(view.getContext(), DividerItemDecoration.VERTICAL));
        }

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadPage();
            }
        });
        if (isCurrentData(mDeliveredData)) {
            renderData(mDeliveredData);
            // Recreating a retained view reuses location cache only; it is not page delivery.
            mAuthorLocations.deliver(mDeliveredData, false);
            hideLoadingView();
        } else {
            mDeliveredData = null;
        }
        if (mRequestParam.loadCache) mSwipeRefreshLayout.setEnabled(false);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        dismissAiSummary();
        if (mAuthorLocations != null) {
            mAuthorLocations.close();
            mAuthorLocations = null;
        }
        if (mArticleAdapter != null) mArticleAdapter.releaseWebViews();
        if (mListView != null) mListView.setAdapter(null);
        mDisplayedData = null;
        mDisplayedTopicOwner = null;
        mArticleAdapter = null;
        if (mViewBindings != null) mViewBindings.unbind();
        mViewBindings = null;
        super.onDestroyView();
    }

    private void applyReplyFabClearance() {
        if (mRequestParam.loadCache) {
            return;
        }
        int bottomPadding = mListView.getPaddingBottom()
                + getResources().getDimensionPixelSize(R.dimen.article_list_reply_fab_clearance);
        mListView.setPadding(
                mListView.getPaddingLeft(),
                mListView.getPaddingTop(),
                mListView.getPaddingRight(),
                bottomPadding);
        mListView.setClipToPadding(false);
    }

    public void loadPage() {
        dismissAiSummary();
        mPresenter.loadPage(mRequestParam);
    }

    private void showAiSummary(ThreadRowInfo row) {
        if (!isResumed() || getContext() == null || row == null
                || !isCurrentData(mDisplayedData) || !ArticleRowPresentation.hasSource(row)) {
            return;
        }
        dismissAiSummary();
        String title = mAiThreadTitle == null ? mRequestParam.title : mAiThreadTitle;
        FloorSummaryInput input = FloorSummaryInput.fromRow(title, row);
        final long contentGeneration = mAiContentGeneration;
        mAiSummaryDialog = AiSummaryDialog.showFloor(getContext(), input,
                () -> isResumed() && contentGeneration == mAiContentGeneration
                        && isCurrentData(mDisplayedData)
                        ? input.getTarget() : null);
    }

    private void dismissAiSummary() {
        if (mAiSummaryDialog != null) {
            mAiSummaryDialog.dismiss();
            mAiSummaryDialog = null;
        }
    }

    @Override
    public void onPause() {
        // ArticlePagerAdapter keeps adjacent pages STARTED; they leave RESUMED on a page switch.
        dismissAiSummary();
        super.onPause();
    }

    public void scrollToTop() {
        if (mListView != null && mListView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) mListView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }
    }

    private boolean isCurrentData(ThreadData data) {
        if (data == null || getActivity() == null) return false;
        if (mRequestParam.loadCache) {
            return mRequestParam.cacheOwner == null
                    || mRequestParam.cacheOwner.equals(ArticleAccounts.currentOwner());
        }
        ArticlePagingInfo paging = data.getPagingInfo();
        ArticleReaderSession reader = getReaderViewModel().getReaderSession();
        boolean enabled = requireContext()
                .getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE)
                .getBoolean(getString(gov.anzong.androidnga.common.R.string.pref_show_with_app_api), false);
        return paging != null && reader != null
                && paging.generation == mRequestParam.readerGeneration
                && paging.generation == reader.state().generation
                && paging.effectivePage == mRequestParam.page
                && reader.environmentMatches(ArticleAccounts.fingerprint(), enabled);
    }

    @Override
    public void setData(ThreadData data) {
        // Reject stale deliveries before changing the active floor summary.
        if (!isCurrentData(data)) return;
        dismissAiSummary();
        mAiContentGeneration++;
        mAiThreadTitle = data.getThreadInfo() != null
                ? data.getThreadInfo().getSubject() : null;
        mDeliveredData = data;
        if (getView() == null || mArticleAdapter == null || mAuthorLocations == null) {
            return;
        }
        renderData(data);
        // Both normal and offscreen-prefetched success reach this seam independently.
        mAuthorLocations.deliver(data, !mRequestParam.loadCache);
    }

    private void renderData(ThreadData data) {
        ArticleShareViewModel viewModel = getReaderViewModel();
        if (isResumed() && mRequestParam.title == null && data.getThreadInfo() != null) {
            getActivity().setTitle(data.getThreadInfo().getSubject());
        }
        if (data.getRowList() != null && !data.getRowList().isEmpty()) {
            ThreadRowInfo first = data.getRowList().get(0);
            if (first.getLou() == 0 && ArticleRowPresentation.hasFloor(first)
                    && ArticleRowPresentation.hasUser(first)) viewModel.setTopicOwner(first.getAuthor());
        }
        String topicOwner = viewModel.getTopicOwner().getValue();
        // READY delivery reuses this view's WebViews unless the response or OP label changed.
        if (mDisplayedData != data || !TextUtils.equals(mDisplayedTopicOwner, topicOwner)) {
            mDisplayedData = data;
            mDisplayedTopicOwner = topicOwner;
            mArticleAdapter.setTopicOwner(topicOwner);
            mArticleAdapter.setData(data);
            mArticleAdapter.notifyDataSetChanged();
        }
        if (isResumed()) getActivity().invalidateOptionsMenu();
        if (mRequestParam.pid > 0 && !mRequestParam.loadCache
                && viewModel.getReaderSession().state().pendingAnchor == null) {
            viewModel.getReaderSession().setAnchor(new ArticleAnchor(mRequestParam.readerGeneration,
                    mRequestParam.page, mRequestParam.pid, null));
        }
        consumePendingAnchor();
    }

    @Override
    public void startPostActivity(Intent intent) {
        if (!StringUtils.isEmpty(UserManagerImpl.getInstance().getUserName())) {// 登入了才能发
            intent.setClass(getActivity(), PhoneConfiguration.getInstance().postActivityClass);
        } else {
            ActivityUtils.startLoginActivity(getActivity());
        }
        startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
    }

    @Override
    public void showPostCommentDialog(String prefix, Bundle bundle) {
        BaseDialogFragment df = new PostCommentDialogFragment();
        df.setArguments(bundle);
        df.show(getActivity().getSupportFragmentManager());
    }


    @Override
    public void setRefreshing(boolean refreshing) {
        if (mSwipeRefreshLayout != null) mSwipeRefreshLayout.setRefreshing(refreshing);
    }

    @Override
    public boolean isRefreshing() {
        return mSwipeRefreshLayout != null && (mSwipeRefreshLayout.isShown()
                ? mSwipeRefreshLayout.isRefreshing() : mLoadingView != null && mLoadingView.isShown());
    }

    @Override
    public void hideLoadingView() {
        if (mLoadingView != null) mLoadingView.setVisibility(View.GONE);
        if (mSwipeRefreshLayout != null) mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    interface OnTopicMenuItemClickListener extends PopupMenu.OnMenuItemClickListener {

        void setThreadRowInfo(ThreadRowInfo threadRowInfo);

    }


}
