package sp.phone.mvp.presenter;

import android.app.Activity;
import android.net.Uri;

import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.mvp.contract.TopicPostContract;
import sp.phone.mvp.model.TopicPostModel;
import sp.phone.param.PostParam;
import sp.phone.task.TopicPostTask;
import sp.phone.ui.fragment.TopicPostFragment;
import sp.phone.util.ActivityUtils;
import sp.phone.util.FunctionUtils;
import sp.phone.util.StringUtils;

public class TopicPostPresenter extends BasePresenter<TopicPostFragment, TopicPostModel>
        implements TopicPostContract.Presenter, TopicPostTask.CallBack {

    private boolean mLoading;

    private PostParam mPostParam;

    @Override
    public void setEmoticon(String emotion) {
        int separator = emotion.indexOf('-');
        if (separator > 0) mBaseView.insertBodyText(emotion.substring(0, separator));
    }

    @Override
    public void setPostParam(PostParam postParam) {
        mPostParam = postParam;
        mBaseModel.getPostInfo(mPostParam, new OnHttpCallBack<PostParam>() {
            @Override
            public void onError(String text) {
                if (mBaseView != null) {
                    ActivityUtils.showToast(text);
                }
            }

            @Override
            public void onSuccess(PostParam data) {
                mPostParam = data;
            }
        });
    }

    public String getInitialTitle() { return mPostParam.getPostSubject(); }

    public String getInitialBody() { return mPostParam.getPostContent(); }

    @Override
    public void post(String title, String body, boolean isAnony) {
        if (mLoading) {
            mBaseView.showToast(R.string.avoidWindfury);
            return;
        }
        mLoading = true;
        mPostParam.setAnonymous(isAnony);
        mPostParam.setPostSubject(title);
        if (!body.isEmpty()) {
            mPostParam.setPostContent(FunctionUtils.ColorTxtCheck(body));
            mBaseModel.post(mPostParam, this);
        }
    }

    @Override
    public void showFilePicker() {
        mBaseView.showFilePicker();
    }

    @Override
    public void startUploadTask(final Uri uri) {
        mBaseView.showUploadFileProgressBar();
        mBaseModel.uploadFile(uri, mPostParam, new OnHttpCallBack<String>() {
            @Override
            public void onError(String text) {
                if (mBaseView != null) {
                    mBaseView.hideUploadFileProgressBar();
                    ToastUtils.error(text);
                }
            }

            @Override
            public void onSuccess(String data) {
                if (mBaseView != null) {
                    mBaseView.hideUploadFileProgressBar();
                    ToastUtils.success("上传成功");
                    finishUpload(data, uri);
                }
            }
        });
    }

    @Override
    public void insertAtFormat() {
        mBaseView.insertBodyText("[@]", 2);
    }

    @Override
    public void insertQuoteFormat() {
        mBaseView.insertBodyText("[quote][/quote]", "[quote]".length());
    }

    @Override
    public void insertUrlFormat() {
        mBaseView.insertBodyText("[url][/url]", "[url]".length());
    }

    @Override
    public void insertBoldFormat() {
        mBaseView.insertBodyText("[b][/b]", "[b]".length());
    }

    @Override
    public void insertItalicFormat() {
        mBaseView.insertBodyText("[i][/i]", "[i]".length());
    }

    @Override
    public void insertUnderLineFormat() {
        mBaseView.insertBodyText("[u][/u]", "[u]".length());
    }

    @Override
    public void insertDeleteLineFormat() {
        mBaseView.insertBodyText("[del][/del]", "[del]".length());
    }

    @Override
    public void insertCollapseFormat() {
        mBaseView.insertBodyText("[collapse][/collapse]", "[collapse]".length());
    }

    @Override
    public void insertFontColorFormat(String fontColor) {
        mBaseView.insertBodyText(fontColor, fontColor.length() - "[/color]".length());
    }

    @Override
    public void insertFontSizeFormat(String fontSize) {
        mBaseView.insertBodyText(fontSize, "[size=100%]".length());
    }

    @Override
    public void insertTopicCategory(String category) {
        mBaseView.insertTitleText(category);
    }

    @Override
    public void loadTopicCategory(OnHttpCallBack<List<String>> callBack) {
        mBaseModel.loadTopicCategory(mPostParam, callBack);
    }

    @Override
    public void onArticlePostFinished(boolean isSuccess, String result) {
        ActivityUtils.getInstance().dismiss();
        if (mBaseView != null) {
            if (!StringUtils.isEmpty(result)) {
                mBaseView.showToast(result);
            }
            if (isSuccess) {
                mBaseView.setResult(Activity.RESULT_OK);
                mBaseView.finish();
            }
        }
        mLoading = false;
    }

    private void finishUpload(String picUrl, Uri uri) {
        mBaseView.insertUploadedFile(uri, "[img]./" + picUrl + "[/img]");
    }

    @Override
    protected TopicPostModel onCreateModel() {
        return new TopicPostModel();
    }
}