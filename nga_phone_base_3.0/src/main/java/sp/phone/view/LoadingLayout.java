package sp.phone.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import gov.anzong.androidnga.R;

/**
 * Created by Justwen on 2018/3/11.
 */

public class LoadingLayout extends LinearLayout {

    private final LoadingTipState mTipState = new LoadingTipState();
    private final LifecycleEventObserver mLifecycleObserver =
            (source, event) -> onHostLifecycleEvent(event);
    private TextView mTipView;
    private Lifecycle mHostLifecycle;
    private boolean mAttachedToWindow;
    private int mDisplayedTip;

    public LoadingLayout(Context context) {
        this(context, null);
    }

    public LoadingLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER);
        setOrientation(LinearLayout.VERTICAL);
        LayoutInflater.from(getContext()).inflate(R.layout.include_loading_view, this, true);
        mTipView = findViewById(R.id.loading_tip);
    }

    /** Bind to the Fragment's view owner, so retained offscreen pages never select tips. */
    public void bindToLifecycle(@NonNull LifecycleOwner owner) {
        Lifecycle lifecycle = owner.getLifecycle();
        if (mHostLifecycle != lifecycle) {
            if (mHostLifecycle != null) {
                mHostLifecycle.removeObserver(mLifecycleObserver);
            }
            mTipState.reset();
            mHostLifecycle = lifecycle;
            if (lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) {
                mHostLifecycle = null;
            } else {
                lifecycle.addObserver(mLifecycleObserver);
            }
        }
        updateTip();
    }

    private void onHostLifecycleEvent(Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            mHostLifecycle.removeObserver(mLifecycleObserver);
            mHostLifecycle = null;
            mTipState.reset();
        }
        updateTip();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        updateTip();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttachedToWindow = false;
        updateTip();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // Completion can also hide a detached view before it is shown again.
        updateTip();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateTip();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTip();
    }

    private void updateTip() {
        // View construction may dispatch visibility callbacks before our fields are initialized.
        if (mTipView == null) {
            return;
        }
        boolean resumed = mHostLifecycle != null
                && mHostLifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
        boolean visibleToUser = mAttachedToWindow && isShown()
                && getWindowVisibility() == View.VISIBLE;
        int tip = mTipState.update(getVisibility() == View.VISIBLE, resumed, visibleToUser,
                () -> BundledLoadingTips.next(getContext()));
        if (mDisplayedTip != tip) {
            mDisplayedTip = tip;
            if (tip == 0) {
                mTipView.setText(null);
            } else {
                mTipView.setText(tip);
            }
        }
        mTipView.setVisibility(tip == 0 ? View.GONE : View.VISIBLE);
    }
}
