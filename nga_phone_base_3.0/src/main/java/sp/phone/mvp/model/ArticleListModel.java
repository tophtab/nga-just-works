package sp.phone.mvp.model;


import com.trello.rxlifecycle2.android.FragmentEvent;


import java.io.IOException;
import java.util.Map;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import sp.phone.http.bean.ThreadData;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.model.convert.ArticleConvertFactory;
import sp.phone.mvp.model.convert.ErrorConvertFactory;
import sp.phone.mvp.model.thread.*;
import sp.phone.mvp.model.thread.ArticleOperation;
import sp.phone.mvp.model.thread.ArticleSource;
import sp.phone.mvp.model.thread.AppArticleParser;
import sp.phone.mvp.model.thread.NormalArticleParser;
import sp.phone.mvp.model.thread.ArticleErrors;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.BaseSubscriber;
import gov.anzong.androidnga.common.util.NLog;

/**
 * 加载帖子内容
 * Created by Justwen on 2017/7/10.
 */

public class ArticleListModel extends BaseModel implements ArticleListContract.Model {

    private static final String TAG = ArticleListModel.class.getSimpleName();

    private RetrofitService mService;

    public ArticleListModel() {
        mService = (RetrofitService) RetrofitHelper.getInstance().getService(RetrofitService.class);
    }

    public String getOrigin() { return getAvailableDomain(); }

    /** Ordinary prefetch keeps the loadPage entry; only the enabled scoped chain uses byte I/O. */
    public void loadPage(ArticleListParam param, ArticleOperation operation, OnHttpCallBack<ThreadData> callback) {
        if (operation.source != ArticleSource.READ_PHP) throw new IllegalArgumentException("Ordinary source required");
        loadScopedPage(operation, callback);
    }

    public void loadScopedPage(ArticleOperation operation, OnHttpCallBack<ThreadData> callback) {
        new ArticleByteClient().read(operation)
                .subscribeOn(Schedulers.io())
                .compose(getLifecycleProvider().<String>bindUntilEvent(FragmentEvent.DETACH))
                .map(raw -> {
                    ThreadData data = operation.source == ArticleSource.APP_API
                            ? new AppArticleParser().parse(raw, operation.key.query, operation.page)
                            : new NormalArticleParser().parse(raw, operation.key.query, operation.page);
                    data.setPagingInfo(data.getPagingInfo().withRequest(operation.key.owner, operation.key.generation));
                    return data;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadData>() {
                    @Override public void onNext(ThreadData data) { callback.onSuccess(data); }
                    @Override public void onError(Throwable error) {
                        Throwable failure = ArticleErrors.failure(error);
                        callback.onError(failure.getMessage(), failure);
                    }
                });
    }

    public String getUrl(ArticleListParam param) {
        int page = param.page;
        int tid = param.tid;
        int pid = param.pid;
        int authorId = param.authorId;
        String url = getAvailableDomain() + "/read.php?" + "&page=" + page + "&__output=8&noprefix&v2";
        if (tid != 0) {
            url = url + "&tid=" + tid;
        }
        if (pid != 0) {
            url = url + "&pid=" + pid;
        }

        if (authorId != 0) {
            url = url + "&authorid=" + authorId;
        }

        return url;

    }

    @Override
    public void loadPage(ArticleListParam param, final OnHttpCallBack<ThreadData> callBack) {
        loadPage(param, (Map<String, String>) null, callBack);
    }

    @Override
    public void loadPage(ArticleListParam param, Map<String, String> header, OnHttpCallBack<ThreadData> callBack) {
        String url = getUrl(param);
        mService.get(url, header)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .compose(getLifecycleProvider().<String>bindUntilEvent(FragmentEvent.DETACH))
                .map(new Function<String, ThreadData>() {
                    @Override
                    public ThreadData apply(@NonNull String s) throws Exception {
                        long time = System.currentTimeMillis();
                        ThreadData data = ArticleConvertFactory.getArticleInfo(s);
                        NLog.e(TAG, "time = " + (System.currentTimeMillis() - time));
                        if (data == null) {
                            String errorMsg = ErrorConvertFactory.getErrorMessage(s);
                            if (errorMsg != null) {
                                throw new Exception(errorMsg);
                            } else {
                                throw new ServerException("NGA后台抽风了，请尝试右上角菜单中的使用内置浏览器打开");
                            }
                        } else {
                            return data;
                        }
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadData>() {

                    @Override
                    public void onNext(@NonNull ThreadData threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        callBack.onError(ErrorConvertFactory.getErrorMessage(throwable), throwable);
                    }
                });
    }

    @Override
    public void cachePage(ArticleListParam param, ThreadData data, OnHttpCallBack<String> callback) {
        final ArticleCacheWrite snapshot;
        try {
            snapshot = new ArticleCacheWrite(ArticleCacheEntry.from(param), param.page, param.topicInfo,
                    data.getRawData(), data.getPagingInfo());
        } catch (RuntimeException invalid) {
            callback.onError("缓存失败！");
            return;
        }
        String fingerprint = ArticleAccounts.fingerprint();
        Observable.fromCallable(() -> {
            new ArticleCacheStore(ContextUtils.getContext().getFilesDir()).write(snapshot, () -> {
                if (!fingerprint.equals(ArticleAccounts.fingerprint())) throw new IllegalStateException("Account changed");
                return ArticleAccounts.currentOwner();
            });
            return "缓存成功！";
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<String>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<String>() {
                    @Override public void onNext(String message) {
                        if (fingerprint.equals(ArticleAccounts.fingerprint())) callback.onSuccess(message);
                    }
                    @Override public void onError(Throwable error) {
                        if (fingerprint.equals(ArticleAccounts.fingerprint())) callback.onError("缓存失败！");
                    }
                });
    }

    @Override
    public void loadCachePage(ArticleListParam param, OnHttpCallBack<ThreadData> callback) {
        ArticleListParam snapshot = (ArticleListParam) param.clone();
        Observable.fromCallable(() -> {
            ArticleCacheEntry entry = ArticleCacheEntry.from(snapshot);
            ArticleStoredPage stored = new ArticleCacheStore(ContextUtils.getContext().getFilesDir())
                    .read(entry, snapshot.page, ArticleAccounts.currentOwner());
            ThreadData data = new ArticleCacheReplay().parse(stored);
            if (entry.owner != null && !entry.owner.equals(ArticleAccounts.currentOwner())) {
                throw new IOException("Cache account changed");
            }
            return data;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadData>() {
                    @Override public void onNext(ThreadData data) { callback.onSuccess(data); }
                    @Override public void onError(Throwable error) { callback.onError("读取缓存失败！"); }
                });
    }

    public static class ServerException extends Exception {

        public ServerException(String message) {
            super(message);
        }
    }

}
