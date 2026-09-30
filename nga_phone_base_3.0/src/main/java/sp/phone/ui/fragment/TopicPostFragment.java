package sp.phone.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.base.widget.ProgressBarEx;
import sp.phone.mvp.contract.TopicPostContract;
import sp.phone.mvp.presenter.TopicPostPresenter;
import sp.phone.param.ParamKey;
import sp.phone.rxjava.RxEvent;
import sp.phone.view.editor.InlineMediaDecorator;
import sp.phone.view.editor.InlineMediaEditText;
import sp.phone.view.toolbar.ToolbarContainer;

public class TopicPostFragment extends BaseMvpFragment<TopicPostPresenter> implements TopicPostContract.View {

    private static final int REQUEST_CODE_SELECT_PIC = 1;

    private CheckBox mAnonyCheckBox;

    private InlineMediaEditText mBodyEditText;

    private InlineMediaDecorator mMediaDecorator;

    private Bundle mRetainedDraft;

    private ProgressBarEx mProgressBar;

    private EditText mTitleEditText;

    private ToolbarContainer mToolbarContainer;

    private Uri mUploadFilePath;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter.setPostParam(getArguments().getParcelable("param"));
        registerRxBus();
    }

    @Override
    protected TopicPostPresenter onCreatePresenter() {
        return new TopicPostPresenter();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_topic_post, container, false);
        mTitleEditText = rootView.findViewById(R.id.reply_titile_edittext);
        mBodyEditText = rootView.findViewById(R.id.reply_body_edittext);
        mAnonyCheckBox = rootView.findViewById(R.id.anony);
        return rootView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mAnonyCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ToastUtils.info("匿名发帖/回复每次将扣除一百铜币,慎重");
            }
        });
        mToolbarContainer = view.findViewById(R.id.control_panel);
        mToolbarContainer.setPresenter(mPresenter);
        mBodyEditText.setOnFocusChangeListener(mToolbarContainer);
        mTitleEditText.setOnFocusChangeListener(mToolbarContainer);
        mBodyEditText.setOnTouchListener(mToolbarContainer);
        mTitleEditText.setOnTouchListener(mToolbarContainer);

        Bundle draft = mRetainedDraft != null ? mRetainedDraft : savedInstanceState;
        if (draft == null || !draft.containsKey("body")) {
            draft = getArguments().getBundle("savedInstanceState");
        }
        if (draft != null && draft.containsKey("body")) {
            mBodyEditText.setText(draft.getString("body", ""));
            mTitleEditText.setText(draft.getString("title", ""));
            mAnonyCheckBox.setChecked(draft.getBoolean("anony", draft.getBoolean("anoay")));
            int length = mBodyEditText.length();
            mBodyEditText.setSelection(Math.max(0, Math.min(length, draft.getInt("selectionStart", length))),
                    Math.max(0, Math.min(length, draft.getInt("selectionEnd", length))));
        } else {
            mBodyEditText.setText(mPresenter.getInitialBody());
            mTitleEditText.setText(mPresenter.getInitialTitle());
            mBodyEditText.setSelection(mBodyEditText.length());
        }
        mMediaDecorator = new InlineMediaDecorator(mBodyEditText);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBodyEditText != null) saveDraft(outState);
        else if (mRetainedDraft != null) outState.putAll(mRetainedDraft);
    }

    private void saveDraft(Bundle state) {
        state.putString("body", mBodyEditText.getText().toString());
        state.putString("title", mTitleEditText.getText().toString());
        state.putBoolean("anony", mAnonyCheckBox.isChecked());
        state.putInt("selectionStart", mBodyEditText.getSelectionStart());
        state.putInt("selectionEnd", mBodyEditText.getSelectionEnd());
    }

    @Override
    public void onDestroyView() {
        mRetainedDraft = new Bundle();
        saveDraft(mRetainedDraft);
        mMediaDecorator.close();
        mMediaDecorator = null;
        mBodyEditText = null;
        mTitleEditText = null;
        mAnonyCheckBox = null;
        mToolbarContainer = null;
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (mMediaDecorator != null) mMediaDecorator.suspend();
        super.onPause();
    }

    @Override
    public boolean onBackPressed() {
        return mToolbarContainer.onBackPressed();
    }

    @Override
    public void insertBodyText(CharSequence text) {
        insertBodyText(text, 0);
    }

    @Override
    public void insertBodyText(CharSequence text, int position) {
        if (mBodyEditText == null) {
            // An explicit upload may finish while the Fragment has no view.
            if (mRetainedDraft != null) {
                String body = mRetainedDraft.getString("body", "");
                mRetainedDraft.putString("body", body + text);
            }
            return;
        }
        mBodyEditText.requestFocus();
        int start = Math.max(0, Math.min(mBodyEditText.getSelectionStart(), mBodyEditText.getSelectionEnd()));
        int end = Math.max(start, Math.max(mBodyEditText.getSelectionStart(), mBodyEditText.getSelectionEnd()));
        mBodyEditText.getText().replace(start, end, text);
        mBodyEditText.setSelection(Math.min(mBodyEditText.length(), start + (position > 0 ? position : text.length())));
    }

    @Override
    public void insertTitleText(CharSequence text) {
        try {
            mTitleEditText.getText().insert(0, text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void insertFile(String path, CharSequence file) {
        insertBodyText(path == null || path.isEmpty() ? "[img]./" + file + "[/img]\n" : file);
    }

    public void insertUploadedFile(Uri uri, String source) {
        if (mMediaDecorator != null) mMediaDecorator.registerLocalImage(source, uri);
        insertBodyText(source + "\n");
    }

    @Override
    public void showFilePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction("android.intent.action.GET_CONTENT");
        startActivityForResult(intent, REQUEST_CODE_SELECT_PIC);
    }


    @Override
    public void showUploadFileProgressBar() {
        if (mProgressBar == null) {
            mProgressBar = new ProgressBarEx(this);
        }
        mProgressBar.show("上传文件中......");
    }

    @Override
    public void hideUploadFileProgressBar() {
        mProgressBar.hide();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.topic_post_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.send) {
            mPresenter.post(mTitleEditText.getText().toString(), mBodyEditText.getText().toString(), mAnonyCheckBox.isChecked());
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void accept(RxEvent rxEvent) {
        if (rxEvent.what == RxEvent.EVENT_INSERT_EMOTICON) {
            mPresenter.setEmoticon((String) rxEvent.obj);
        }
    }

    @Override
    public void onResume() {
        if (mMediaDecorator != null) mMediaDecorator.resume();
        if (mUploadFilePath != null) {
            mPresenter.startUploadTask(mUploadFilePath);
            mUploadFilePath = null;
        }
        if (!"new".equals(getArguments().getString(ParamKey.KEY_ACTION))) {
            mTitleEditText.setHint(R.string.titlecannull);
            mBodyEditText.requestFocus();
        }
        super.onResume();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SELECT_PIC
                && resultCode == Activity.RESULT_OK
                && data != null) {
            mUploadFilePath = data.getData();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}