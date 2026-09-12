package sp.phone.ui.adapter;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import sp.phone.mvp.model.thread.ArticleReaderState;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.ui.fragment.ArticleListFragment;

/**
 * 帖子详情分页Adapter
 * Created by Justwen on 2017/7/9.
 */

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private int mCount = 1;
    private int mSinglePage;

    private ArticleListParam mRequestParam;

    private List<Integer> mPageIndexList;

    private ArticleListFragment mCurrentFragment;

    public ArticlePagerAdapter(FragmentManager fm, ArticleListParam param) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mRequestParam = (ArticleListParam) param.clone();
        mSinglePage = Math.max(1, param.page);
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = new ArticleListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ParamKey.KEY_PARAM, getRequestParam(position));
        fragment.setArguments(bundle);
        return fragment;
    }

    private ArticleListParam getRequestParam(int position) {
        ArticleListParam param = (ArticleListParam) mRequestParam.clone();
        param.page = getActualPage(position);
        return param;
    }

    public int getActualPage(int position) {
        if (mPageIndexList != null) return mPageIndexList.get(position);
        return mSinglePage > 0 ? mSinglePage : position + 1;
    }

    public int positionOfPage(int page) {
        if (mPageIndexList != null) return mPageIndexList.indexOf(page);
        if (mSinglePage > 0) return page == mSinglePage ? 0 : -1;
        return page > 0 && page <= mCount ? page - 1 : -1;
    }

    public void updateReaderState(ArticleReaderState state) {
        Integer total = state.paging == null ? null : state.paging.totalPages;
        int count = total == null ? 1 : total;
        int singlePage = total == null ? state.currentPage : 0;
        boolean changed = mRequestParam.readerGeneration != state.generation
                || mCount != count || mSinglePage != singlePage;
        if (mRequestParam.readerGeneration != state.generation) mCurrentFragment = null;
        mRequestParam.readerGeneration = state.generation;
        mCount = count;
        mSinglePage = singlePage;
        if (changed) notifyDataSetChanged();
    }

    @Override
    public int getItemPosition(Object object) {
        if (!(object instanceof ArticleListFragment)) return POSITION_NONE;
        ArticleListFragment fragment = (ArticleListFragment) object;
        if (fragment.getReaderGeneration() != mRequestParam.readerGeneration) return POSITION_NONE;
        int position = positionOfPage(fragment.getRequestPage());
        return position < 0 ? POSITION_NONE : position;
    }

    @Override
    public int getCount() {
        return mCount;
    }

    public void setPageIndexList(List<String> pageIndexList) {
        TreeSet<Integer> pages = new TreeSet<>();
        for (String value : pageIndexList) {
            int page = Integer.parseInt(value);
            if (page > 0) pages.add(page);
        }
        mPageIndexList = new ArrayList<>(pages);
        mCount = mPageIndexList.size();
        notifyDataSetChanged();
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
        if (object instanceof ArticleListFragment) {
            mCurrentFragment = (ArticleListFragment) object;
        }
    }

    public ArticleListFragment getCurrentFragment() {
        return mCurrentFragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return String.valueOf(getActualPage(position));
    }
}
