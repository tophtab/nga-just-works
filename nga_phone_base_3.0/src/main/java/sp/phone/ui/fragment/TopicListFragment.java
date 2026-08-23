package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.alibaba.android.arouter.launcher.ARouter;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import butterknife.BindView;
import butterknife.OnClick;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.base.widget.LongPressRepeater;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.ParamKey;
import sp.phone.util.ActivityUtils;

/**
 * Created by Justwen on 2017/11/19.
 */

public class TopicListFragment extends TopicSearchFragment {

    /**
     * 长按发帖按钮不松手时，每隔这么久回顶并刷新一次。
     */
    private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;

    private Menu mOptionMenu;

    private LongPressRepeater mFabRefreshRepeater;

    @BindView(R.id.fab_post)
    public FloatingActionButton mFab;

    @BindView(R.id.appbar)
    public AppBarLayout mAppBarLayout;

    @BindView(R.id.toolbar)
    public Toolbar mToolbar;

    @Override
    protected void setTitle() {
        if (mRequestParam.title != null) {
            setTitle(mRequestParam.title);
        }
    }

    @Override
    public void setData(TopicListInfo result) {
        super.setData(result);
        if (mRequestParam.title == null && result.getName() != null && getActivity() != null) {
            mRequestParam.title = result.getName();
            getActivity().setTitle(mRequestParam.title);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = R.layout.fragment_topic_list_board;
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFabRefreshRepeater = new LongPressRepeater(
                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS, v -> scrollToTopAndRefresh());
        mFabRefreshRepeater.attach(mFab);
    }

    @Override
    public void onDestroyView() {
        if (mFabRefreshRepeater != null) {
            mFabRefreshRepeater.detach(mFab);
            mFabRefreshRepeater = null;
        }
        super.onDestroyView();
    }

    @Override
    public void hideLoadingView() {
        AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        super.hideLoadingView();
    }

    @Override
    public void scrollTo(int position) {
        if (position == 0) {
            mAppBarLayout.setExpanded(true, true);
        }
        super.scrollTo(position);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @OnClick(R.id.fab_post)
    public void startPostActivity() {
        ARouter.getInstance()
                .build(ARouterConstants.ACTIVITY_POST)
                .withInt(ParamKey.KEY_FID, mRequestParam.fid)
                .withString(ParamKey.KEY_STID, mRequestParam.stid != 0 ? String.valueOf(mRequestParam.stid) : null)
                .withString(ParamKey.KEY_ACTION, "new")
                .navigation();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (mPresenter.isBookmarkBoard(mRequestParam.fid, mRequestParam.stid)) {
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(true);
        } else {
            menu.findItem(R.id.menu_add_bookmark).setVisible(true);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(false);
        }

        if (mTopicListInfo != null) {
            menu.findItem(R.id.menu_sub_board).setVisible(!mTopicListInfo.getSubBoardList().isEmpty());
        } else {
            menu.findItem(R.id.menu_sub_board).setVisible(false);
        }

        if (mRequestParam.fid == 0 && mRequestParam.stid == 0) {
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(false);
        }

        menu.findItem(R.id.menu_board_head).setVisible(mRequestParam.boardHead != null);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.topic_list_menu, menu);
        mOptionMenu = menu;
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_bookmark:
                mPresenter.addBookmarkBoard();
                item.setVisible(false);
                mOptionMenu.findItem(R.id.menu_remove_bookmark).setVisible(true);
                ToastUtils.showToast(R.string.toast_add_bookmark_board);
                break;
            case R.id.menu_remove_bookmark:
                mPresenter.removeBookmarkBoard(mRequestParam.fid, mRequestParam.stid);
                item.setVisible(false);
                mOptionMenu.findItem(R.id.menu_add_bookmark).setVisible(true);
                ToastUtils.showToast(R.string.toast_remove_bookmark_board);
                break;
            case R.id.menu_sub_board:
                showSubBoardList();
                break;
            case R.id.menu_board_head:
                mPresenter.startArticleActivity(mRequestParam.boardHead, mRequestParam.title + " - 版头");
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showSubBoardList() {
        Intent intent = new Intent(getContext(), LauncherSubActivity.class);
        intent.putExtra("fragment", BoardSubListFragment.class.getName());
        intent.putExtra(ParamKey.KEY_TITLE, mRequestParam.title);
        intent.putExtra(ParamKey.KEY_FID, mRequestParam.fid);

        intent.putParcelableArrayListExtra("subBoard", mTopicListInfo.getSubBoardList());
        startActivityForResult(intent, ActivityUtils.REQUEST_CODE_SUB_BOARD);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityUtils.REQUEST_CODE_SUB_BOARD && resultCode == Activity.RESULT_OK) {
            mPresenter.loadPage(1, mRequestParam);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
