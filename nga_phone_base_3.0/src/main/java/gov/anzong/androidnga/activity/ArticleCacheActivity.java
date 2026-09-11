package gov.anzong.androidnga.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.base.widget.TabLayoutEx;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import sp.phone.mvp.model.thread.ArticleAccounts;
import sp.phone.mvp.model.thread.ArticleCacheEntry;
import sp.phone.mvp.model.thread.ArticleCacheStore;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.ui.adapter.ArticlePagerAdapter;

/** Reads one validated cache entry; its tabs always carry actual stored page numbers. */
public class ArticleCacheActivity extends BaseActivity {
    private ArticleListParam mRequestParam;
    private ArticleCacheEntry mEntry;
    private Disposable mCacheLoad;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        setToolbarEnabled(true);
        mRequestParam = getIntent().getParcelableExtra(ParamKey.KEY_PARAM);
        super.onCreate(savedInstanceState);
        if (mRequestParam == null || !mRequestParam.loadCache || mRequestParam.pid != 0
                || mRequestParam.authorId != 0 || mRequestParam.searchPost != 0) {
            finish();
            return;
        }
        try {
            mEntry = ArticleCacheEntry.from(mRequestParam);
            if (!ownerMatches()) { finish(); return; }
        } catch (IllegalArgumentException invalid) { finish(); return; }
        setTitle(mRequestParam.title);
        mCacheLoad = Observable.fromCallable(() -> new ArticleCacheStore(getFilesDir())
                        .pages(mEntry, ArticleAccounts.currentOwner()))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(pages -> {
                    if (isFinishing() || isDestroyed() || !ownerMatches()) return;
                    if (pages.isEmpty()) { failLoad(); return; }
                    initViews(pages);
                }, error -> { if (!isFinishing() && !isDestroyed()) failLoad(); });
    }

    private boolean ownerMatches() {
        return mEntry != null && (mEntry.owner == null || mEntry.owner.equals(ArticleAccounts.currentOwner()));
    }

    private void failLoad() { ToastUtils.error("读取缓存失败！"); finish(); }

    private void initViews(List<Integer> pages) {
        setContentView(R.layout.fragment_article_tab);
        ArticlePagerAdapter adapter = new ArticlePagerAdapter(getSupportFragmentManager(), mRequestParam);
        List<String> pageNames = new ArrayList<>();
        for (Integer page : pages) pageNames.add(String.valueOf(page));
        adapter.setPageIndexList(pageNames);
        findViewById(R.id.fab_post).setVisibility(View.GONE);
        ViewPager viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);
        int selected = adapter.positionOfPage(mRequestParam.page);
        viewPager.setCurrentItem(Math.max(0, selected), false);
        TabLayoutEx tabLayout = findViewById(R.id.tabs);
        int count = pages.size();
        tabLayout.setTabOnScreenLimit(count <= 5 ? count : 0);
        tabLayout.setUpWithViewPager(viewPager);
    }

    @Override protected void onResume() {
        super.onResume();
        if (mEntry != null && !ownerMatches()) finish();
    }

    @Override protected void onDestroy() {
        if (mCacheLoad != null) mCacheLoad.dispose();
        super.onDestroy();
    }
}
