package sp.phone.mvp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sp.phone.http.bean.ThreadData;
import sp.phone.param.ArticleListParam;
import sp.phone.mvp.model.thread.ArticleAnchor;
import sp.phone.mvp.model.thread.ArticleQuery;
import sp.phone.mvp.model.thread.ArticleReaderSession;
import sp.phone.mvp.model.thread.ArticleReaderState;
import sp.phone.mvp.model.thread.ArticleRequestKey;

/**
 * @author yangyihang
 */
public class ArticleShareViewModel extends ViewModel {

    private ArticleReaderSession mReaderSession;
    private final MutableLiveData<ArticleReaderState> mReaderState = new MutableLiveData<>();

    public ArticleReaderSession initializeReader(ArticleListParam param) {
        ArticleQuery query = ArticleQuery.from(param);
        if (mReaderSession == null || !mReaderSession.state().query.equals(query)) {
            mReaderSession = new ArticleReaderSession(query, param.page);
            mTopicOwner.setValue(null);
            publishReaderState();
        }
        return mReaderSession;
    }

    public ArticleReaderSession getReaderSession() { return mReaderSession; }
    public LiveData<ArticleReaderState> getReaderState() { return mReaderState; }

    public boolean ensureEnvironment(String fingerprint, boolean enabled, String owner, String origin, int page) {
        boolean changed = mReaderSession.ensureEnvironment(fingerprint, enabled, owner, origin, page);
        if (changed) publishReaderState();
        return changed;
    }

    public int adoptPage(ArticleRequestKey key, ThreadData data, boolean foreground) {
        int result = mReaderSession.adopt(key, data, foreground);
        if (result != 0) publishReaderState();
        return result;
    }

    public void setPendingAnchor(ArticleAnchor anchor) {
        mReaderSession.setAnchor(anchor);
        publishReaderState();
    }

    public void selectPage(int page) { mReaderSession.select(page); }

    private void publishReaderState() {
        ArticleReaderState previous = mReaderState.getValue();
        ArticleReaderState state = mReaderSession.state();
        if (previous == null || previous.generation != state.generation || !state.canPrefetch()) {
            setPrefetchPages(Collections.emptyList());
        }
        mReaderState.setValue(state);
    }

    private MutableLiveData<Integer> mRefreshPage = new MutableLiveData<>();

    private MutableLiveData<Integer> mCachePage = new MutableLiveData<>();

    private MutableLiveData<String> mTopicOwner = new MutableLiveData<>();

    private MutableLiveData<List<Integer>> mPrefetchPages = new MutableLiveData<>();

    public MutableLiveData<Integer> getRefreshPage() {
        return mRefreshPage;
    }

    public void setRefreshPage(int refreshPage) {
        mRefreshPage.setValue(refreshPage);
    }

    public MutableLiveData<Integer> getCachePage() {
        return mCachePage;
    }

    public void setCachePage(int cachePage) {
        mCachePage.setValue(cachePage);
    }

    public LiveData<String> getTopicOwner() {
        return mTopicOwner;
    }

    public void setTopicOwner(String owner) {
        mTopicOwner.setValue(owner);
    }

    public LiveData<List<Integer>> getPrefetchPages() {
        return mPrefetchPages;
    }

    public void setPrefetchPages(List<Integer> prefetchPages) {
        List<Integer> snapshot = prefetchPages == null
                ? new ArrayList<>()
                : new ArrayList<>(prefetchPages);
        mPrefetchPages.setValue(Collections.unmodifiableList(snapshot));
    }
}
