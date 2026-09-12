package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.justwen.androidnga.base.activity.ARouterConstants;
import com.trello.rxlifecycle2.android.FragmentEvent;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.activity.fragment.ForumWebFragment;
import gov.anzong.androidnga.base.util.ShareUtils;
import gov.anzong.androidnga.base.widget.LongPressRepeater;
import gov.anzong.androidnga.base.widget.TabLayoutEx;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.mvp.presenter.ArticlePageCache;
import sp.phone.mvp.viewmodel.ArticlePagePrefetchPlanner;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.mvp.model.thread.ArticleAnchor;
import sp.phone.mvp.model.thread.ArticlePagingInfo;
import sp.phone.mvp.model.thread.ArticleQuery;
import sp.phone.mvp.model.thread.ArticleReaderState;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.task.BookmarkTask;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.adapter.ArticlePagerAdapter;
import sp.phone.ui.fragment.dialog.GotoDialogFragment;
import sp.phone.util.ARouterUtils;
import sp.phone.util.ActivityUtils;
import sp.phone.util.StringUtils;

/**
 * 帖子详情Fragment
 * Created by Justwen on 2017/7/9.
 */

public class ArticleTabFragment extends BaseRxFragment {

    @BindView(R.id.pager)
    public ViewPager mViewPager;

    private ArticlePagerAdapter mPagerAdapter;

    private ArticleListParam mRequestParam;

    @BindView(R.id.tabs)
    public TabLayoutEx mTabLayout;

    @BindView(R.id.appbar)
    public AppBarLayout mAppBarLayout;

    private static final String GOTO_TAG = "goto";

    private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;

    @BindView(R.id.fab_post)
    public FloatingActionButton mFab;

    private LongPressRepeater mFabRefreshRepeater;

    private int mReplyCount;

    private int mCurrentPage = 1;

    private int mTotalPages = 1;
    private boolean mApplyingState;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        }

        getActivityViewModel().initializeReader(mRequestParam);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_article_tab, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        ButterKnife.bind(this, view);
        mPagerAdapter = new ArticlePagerAdapter(getChildFragmentManager(), mRequestParam);
        mPagerAdapter.updateReaderState(getActivityViewModel().getReaderSession().state());
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);
        mCurrentPage = mPagerAdapter.getActualPage(mViewPager.getCurrentItem());
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mApplyingState) return;
                mCurrentPage = mPagerAdapter.getActualPage(position);
                getActivityViewModel().selectPage(mCurrentPage);
                publishPrefetchPages();
            }
        });

        mTabLayout.setTabOnScreenLimit(1);
        mTabLayout.setUpWithViewPager(mViewPager);
        mTabLayout.setOnTabReselectedListener(position -> scrollCurrentPageToTop());
        mTabLayout.setOnCurrentTabLongPressListener(
                position -> refreshCurrentPage(),
                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS);
        mFabRefreshRepeater = new LongPressRepeater(
                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS, v -> refreshCurrentPage());
        mFabRefreshRepeater.attach(mFab);
        getActivityViewModel().getReaderState().observe(getViewLifecycleOwner(), this::renderReaderState);
        super.onViewCreated(view, savedInstanceState);
    }

    private void renderReaderState(ArticleReaderState state) {
        mApplyingState = true;
        mPagerAdapter.updateReaderState(state);
        int selected = mPagerAdapter.positionOfPage(state.currentPage);
        if (selected >= 0) mViewPager.setCurrentItem(selected, false);
        mCurrentPage = mPagerAdapter.getActualPage(mViewPager.getCurrentItem());
        mTotalPages = state.paging == null || state.paging.totalPages == null ? 0 : state.paging.totalPages;
        mReplyCount = state.paging == null || state.paging.totalRows == null ? 0 : state.paging.totalRows;
        int count = mPagerAdapter.getCount();
        mTabLayout.setTabOnScreenLimit(count <= 5 ? count : 0);
        mTabLayout.notifyDataSetChanged();
        mApplyingState = false;
        publishPrefetchPages();
        if (getActivity() != null) getActivity().invalidateOptionsMenu();
    }

    private void publishPrefetchPages() {
        if (getActivity() != null && getActivityViewModel().getReaderSession().state().canPrefetch()) {
            getActivityViewModel().setPrefetchPages(
                    ArticlePagePrefetchPlanner.plan(mCurrentPage, mTotalPages));
        } else if (getActivity() != null) {
            getActivityViewModel().setPrefetchPages(java.util.Collections.emptyList());
        }
    }

    private void scrollCurrentPageToTop() {
        mAppBarLayout.setExpanded(true, true);
        ArticleListFragment fragment = mPagerAdapter.getCurrentFragment();
        if (fragment != null) {
            fragment.scrollToTop();
        }
    }

    private void refreshCurrentPage() {
        ArticleListFragment fragment = mPagerAdapter.getCurrentFragment();
        if (fragment != null && !fragment.isRefreshing()) {
            fragment.loadPage();
        }
    }

    @Override
    public void onResume() {
        registerRxBus(FragmentEvent.PAUSE);
        super.onResume();
    }

    @OnClick(R.id.fab_post)
    public void reply() {
        Intent intent = new Intent();
        String tid = String.valueOf(mRequestParam.tid);
        intent.putExtra("prefix", "");
        intent.putExtra("tid", tid);
        intent.putExtra("action", "reply");
        if (!StringUtils.isEmpty(UserManagerImpl.getInstance().getUserName())) {// 登入了才能发
            intent.setClass(getContext(),
                    PhoneConfiguration.getInstance().postActivityClass);
        } else {
            ActivityUtils.startLoginActivity(getContext());
        }
        getActivity().startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_bookmark:
                BookmarkTask.execute(mRequestParam.tid);
                break;
            case R.id.menu_goto_floor:
                createGotoDialog();
                break;
            case R.id.menu_share:
                share();
                break;
            case R.id.menu_copy_url:
                copyUrl();
                break;
            case R.id.menu_nightmode:
                ThemeManager.getInstance().setNightMode(true);
                break;
            case R.id.menu_daymode:
                ThemeManager.getInstance().setNightMode(false);
                break;
            case R.id.menu_download:
                getActivityViewModel().setCachePage(mCurrentPage);
                break;
            case R.id.menu_show_whole_thread:
                ArticleListFragment selected = mPagerAdapter.getCurrentFragment();
                ArticleListParam full = selected == null ? null : selected.fullThreadParam();
                if (full == null) { showToast("尚未确定帖子，请先加载内容"); break; }
                Intent intent = new Intent(getContext(), PhoneConfiguration.getInstance().articleActivityClass);
                intent.putExtra(ParamKey.KEY_PARAM, full);
                startActivity(intent);
                break;
            case R.id.menu_open_by_browser:
                ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                        .withString("url", getCurrentUrl())
                        .withString("title", mRequestParam.title)
                        .withString("fragment", ForumWebFragment.class.getName())
                        .navigation(getContext());
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        mTabLayout.setOnCurrentTabLongPressListener(null, 0L);
        if (mFabRefreshRepeater != null) {
            mFabRefreshRepeater.detach(mFab);
            mFabRefreshRepeater = null;
        }
        super.onDestroyView();
    }

    private ArticleShareViewModel getActivityViewModel() {
        return getActivityViewModelProvider().get(ArticleShareViewModel.class);
    }

    private String getCurrentUrl() {
        return ArticleQuery.from(mRequestParam).browserUrl(Utils.getNGAHost(), mCurrentPage);
    }

    private void copyUrl() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            ClipData clipData = ClipData.newPlainText("text", getCurrentUrl());
            clipboardManager.setPrimaryClip(clipData);
            showToast("已经复制至粘贴板");
        }
    }

    private void share() {
        String title = getString(R.string.share);
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(getActivity().getTitle())) {
            builder.append("《").append(getActivity().getTitle()).append("》 - 艾泽拉斯国家地理论坛，地址：");
        }
        builder.append(getCurrentUrl()).append(" (分享自 NGA Just Works)");
        ShareUtils.INSTANCE.shareText(getContext(), title, builder.toString());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.article_list_option_menu, menu);
        if (mRequestParam.authorId != 0) {
            menu.add(Menu.NONE, R.id.menu_show_whole_thread, Menu.NONE, R.string.show_whole_thread);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_goto_floor).setVisible(mTotalPages > 0 || canGoToFloor());

        if (ThemeManager.getInstance().isNightModeFollowSystem()) {
            menu.findItem(R.id.menu_nightmode).setVisible(false);
            menu.findItem(R.id.menu_daymode).setVisible(false);
        } else if (ThemeManager.getInstance().isNightMode()) {
            menu.findItem(R.id.menu_nightmode).setVisible(false);
            menu.findItem(R.id.menu_daymode).setVisible(true);
        } else {
            menu.findItem(R.id.menu_nightmode).setVisible(true);
            menu.findItem(R.id.menu_daymode).setVisible(false);
        }

        menu.findItem(R.id.menu_download)
                .setVisible(ArticlePageCache.isCacheableContext(mRequestParam));
        ArticleListFragment current = mPagerAdapter == null ? null : mPagerAdapter.getCurrentFragment();
        menu.findItem(R.id.menu_download).setEnabled(current != null && current.hasCompletePage());
        super.onPrepareOptionsMenu(menu);
    }

    private void createGotoDialog() {

        Bundle args = new Bundle();
        args.putInt("page", mTotalPages);
        ArticleListFragment current = mPagerAdapter.getCurrentFragment();
        Integer localMax = current == null ? null : current.maxKnownFloor();
        int maxFloor = canEstimateFloorPage() ? Math.max(0, mReplyCount - 1) : 0;
        args.putInt("floor_max", localMax == null ? maxFloor : Math.max(localMax, maxFloor));
        args.putBoolean("can_page", mTotalPages > 0);
        args.putBoolean("can_floor", canGoToFloor());

        DialogFragment df = new GotoDialogFragment();
        df.setArguments(args);
        df.setTargetFragment(this, ActivityUtils.REQUEST_CODE_JUMP_PAGE);

        FragmentManager fm = getActivity().getSupportFragmentManager();

        Fragment prev = fm.findFragmentByTag(GOTO_TAG);
        if (prev != null) {
            fm.beginTransaction().remove(prev).commit();
        }
        df.show(fm, GOTO_TAG);

    }

    private boolean canGoToFloor() {
        ArticleListFragment current = mPagerAdapter == null ? null : mPagerAdapter.getCurrentFragment();
        return canEstimateFloorPage() || current != null && current.maxKnownFloor() != null;
    }

    private boolean canEstimateFloorPage() {
        ArticlePagingInfo paging = getActivityViewModel().getReaderSession().state().paging;
        return paging != null && paging.canEstimateFloorPage && mReplyCount > 0;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityUtils.REQUEST_CODE_TOPIC_POST && resultCode == Activity.RESULT_OK) {
            getActivityViewModel().setRefreshPage(mCurrentPage);
        } else if (requestCode == ActivityUtils.REQUEST_CODE_JUMP_PAGE && resultCode == Activity.RESULT_OK && data != null) {
            if (data.hasExtra("page")) {
                int position = mPagerAdapter.positionOfPage(data.getIntExtra("page", 1));
                if (position >= 0) mViewPager.setCurrentItem(position);
            } else if (canGoToFloor()) {
                int floor = data.getIntExtra("floor", 0);
                ArticleReaderState state = getActivityViewModel().getReaderSession().state();
                ArticleListFragment current = mPagerAdapter.getCurrentFragment();
                Integer page = current != null && current.containsFloor(floor) ? mCurrentPage
                        : canEstimateFloorPage() ? state.paging.candidatePage(floor) : null;
                int position = page == null ? -1 : mPagerAdapter.positionOfPage(page);
                if (position >= 0) {
                    getActivityViewModel().setPendingAnchor(new ArticleAnchor(state.generation, page, null, floor));
                    mViewPager.setCurrentItem(position);
                } else showToast("当前内容中未找到目标楼层，无法确定其他页的位置");
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

}
