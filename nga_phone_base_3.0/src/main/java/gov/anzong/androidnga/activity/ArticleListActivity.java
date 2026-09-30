package gov.anzong.androidnga.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.alibaba.android.arouter.facade.annotation.Route;

import gov.anzong.androidnga.arouter.ARouterConstants;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ArticleLinkParser;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.param.ParamKey;
import sp.phone.ui.fragment.ArticleSearchFragment;
import sp.phone.ui.fragment.ArticleTabFragment;

/**
 * 帖子详情页, 是否MD都用这个
 */
@Route(path = ARouterConstants.ACTIVITY_TOPIC_CONTENT)
public class ArticleListActivity extends BaseActivity {

    private ArticleListParam mRequestParam;
    private boolean mPendingLaunch;

    private void setupFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(android.R.id.content);

        if (fragment == null) {
            if (mRequestParam.searchPost == 0 && mRequestParam.pid == 0) {
                fragment = new ArticleTabFragment();
            } else {
                fragment = new ArticleSearchFragment();
            }
            fragment.setHasOptionsMenu(true);
            Bundle bundle = new Bundle();
            bundle.putParcelable(ParamKey.KEY_PARAM, mRequestParam);
            fragment.setArguments(bundle);
            fm.beginTransaction().replace(android.R.id.content, fragment).commit();
        } else {
            fragment.setHasOptionsMenu(true);
        }
    }

    private ArticleListParam getArticleListParam() {

        Bundle bundle = getIntent().getExtras();
        String url = getIntent().getDataString();
        ArticleListParam param = null;
        if (url != null) {
            param = ArticleLinkParser.parse(url);
        } else if (bundle != null) {
            param = bundle.getParcelable(ParamKey.KEY_PARAM);
            if (param == null) {
                param = new ArticleListParam();
                param.tid = bundle.getInt(ParamKey.KEY_TID, 0);
                param.pid = bundle.getInt(ParamKey.KEY_PID, 0);
                param.authorId = bundle.getInt(ParamKey.KEY_AUTHOR_ID, 0);
                param.searchPost = bundle.getInt(ParamKey.KEY_SEARCH_POST, 0);
                param.page = bundle.getInt(ParamKey.KEY_PAGE, 1);
                param.title = bundle.getString(ParamKey.KEY_TITLE);
            }
        }

        if (param != null) param.page = Math.max(1, param.page);
        return param;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setToolbarEnabled(true);
        mRequestParam = getArticleListParam();
        super.onCreate(savedInstanceState);
        if (mRequestParam == null) {
            finish();
            return;
        }
        openReader(savedInstanceState == null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mRequestParam = getArticleListParam();
        if (mRequestParam == null) {
            finish();
            return;
        }
        mPendingLaunch = true;
        if (!getSupportFragmentManager().isStateSaved()) openReader(true);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPendingLaunch && !isFinishing()) openReader(true);
    }

    private void openReader(boolean newLaunch) {
        mPendingLaunch = false;
        if (newLaunch) {
            FragmentManager fm = getSupportFragmentManager();
            fm.executePendingTransactions();
            Fragment old = fm.findFragmentById(android.R.id.content);
            // resetReader publishes synchronously: retire every old view observer first.
            if (old != null) fm.beginTransaction().remove(old).commitNow();
            new ViewModelProvider(this).get(ArticleShareViewModel.class).resetReader(mRequestParam);
        }
        setupFragment();
        if (newLaunch || mRequestParam.title != null) {
            setTitle(mRequestParam.title == null ? "" : mRequestParam.title);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
        if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
}
