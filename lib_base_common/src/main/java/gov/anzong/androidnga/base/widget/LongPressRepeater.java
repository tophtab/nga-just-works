package gov.anzong.androidnga.base.widget;

import android.view.View;

/**
 * 长按后立即触发一次，并在手指按住期间按固定间隔重复触发。
 * <p>
 * 这是本项目里唯一一份「长按重复」实现，不要再写第二个 {@code postDelayed} 循环。
 * 触发什么由调用方决定，什么时候触发由这个类决定；控件自己的额外约束通过
 * {@link RepeatCondition} 传进来，不要塞进这个类。
 * <p>
 * 松手、滑出控件、被父容器拦截、控件脱离窗口，都会停止重复。
 */
public final class LongPressRepeater implements View.OnLongClickListener {

    /**
     * 长按触发回调，长按阈值到达时立即触发一次，之后按间隔重复。
     */
    public interface OnLongPressRepeatListener {
        void onLongPressRepeat(View view);
    }

    /**
     * 每次触发前的额外条件。首次长按不满足条件时长按事件不会被消费，
     * 调用方原本的点击行为照常走；按住期间条件不再成立就停止重复。
     */
    public interface RepeatCondition {
        boolean canRepeat(View view);
    }

    private final OnLongPressRepeatListener mListener;

    private long mRepeatIntervalMillis;

    private RepeatCondition mRepeatCondition;

    private View mPressedView;

    private final Runnable mRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            View view = mPressedView;
            if (view == null
                    || !view.isAttachedToWindow()
                    || !view.isPressed()
                    || !canRepeat(view)) {
                stop();
                return;
            }

            mListener.onLongPressRepeat(view);
            view.postDelayed(this, mRepeatIntervalMillis);
        }
    };

    public LongPressRepeater(long repeatIntervalMillis, OnLongPressRepeatListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        setRepeatIntervalMillis(repeatIntervalMillis);
        mListener = listener;
    }

    public void setRepeatIntervalMillis(long repeatIntervalMillis) {
        if (repeatIntervalMillis <= 0) {
            throw new IllegalArgumentException("repeatIntervalMillis must be positive");
        }
        mRepeatIntervalMillis = repeatIntervalMillis;
    }

    public void setRepeatCondition(RepeatCondition condition) {
        mRepeatCondition = condition;
    }

    /**
     * 把长按监听装到 view 上。可以对多个 view 调用，同一时刻只会有一个 view 处于重复状态。
     */
    public void attach(View view) {
        view.setOnLongClickListener(this);
    }

    /**
     * 停止重复并摘掉长按监听。
     */
    public void detach(View view) {
        stop(view);
        view.setOnLongClickListener(null);
    }

    /**
     * 无条件停止当前的重复。
     */
    public void stop() {
        if (mPressedView != null) {
            mPressedView.removeCallbacks(mRepeatRunnable);
            mPressedView = null;
        }
    }

    /**
     * 仅当正在重复的就是该 view 时停止，避免误伤别的 view 上正在进行的长按。
     */
    public void stop(View view) {
        if (mPressedView == view) {
            stop();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (!canRepeat(view)) {
            return false;
        }

        stop();
        mPressedView = view;
        mListener.onLongPressRepeat(view);
        if (view.isAttachedToWindow() && view.isPressed()) {
            view.postDelayed(mRepeatRunnable, mRepeatIntervalMillis);
        }
        return true;
    }

    private boolean canRepeat(View view) {
        return mRepeatCondition == null || mRepeatCondition.canRepeat(view);
    }
}
