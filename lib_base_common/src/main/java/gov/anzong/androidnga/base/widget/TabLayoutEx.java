package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager.widget.ViewPager;

import com.nshmura.recyclertablayout.RecyclerTabLayout;

public class TabLayoutEx extends RecyclerTabLayout {

    /**
     * 占位间隔，真正的间隔由 {@link #setOnCurrentTabLongPressListener} 传入。
     */
    private static final long DEFAULT_CURRENT_TAB_LONG_PRESS_REPEAT_INTERVAL_MS = 5_000L;

    private OnTabReselectedListener mOnTabReselectedListener;

    private OnCurrentTabLongPressListener mOnCurrentTabLongPressListener;

    private final LongPressRepeater mCurrentTabLongPressRepeater = createCurrentTabLongPressRepeater();

    private LongPressRepeater createCurrentTabLongPressRepeater() {
        LongPressRepeater repeater = new LongPressRepeater(
                DEFAULT_CURRENT_TAB_LONG_PRESS_REPEAT_INTERVAL_MS,
                this::dispatchCurrentTabLongPress);
        repeater.setRepeatCondition(this::isCurrentTab);
        return repeater;
    }

    public TabLayoutEx(Context context) {
        super(context);
    }

    public TabLayoutEx(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TabLayoutEx(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setUpWithViewPager(ViewPager viewPager) {
        DefaultAdapter adapter = new TabAdapter(viewPager);
        adapter.setTabPadding(mTabPaddingStart, mTabPaddingTop, mTabPaddingEnd, mTabPaddingBottom);
        adapter.setTabTextAppearance(mTabTextAppearance);
        adapter.setTabSelectedTextColor(mTabSelectedTextColorSet, mTabSelectedTextColor);
        adapter.setTabMaxWidth(mTabMaxWidth);
        adapter.setTabMinWidth(mTabMinWidth);
        adapter.setTabBackgroundResId(mTabBackgroundResId);
        adapter.setTabOnScreenLimit(mTabOnScreenLimit);
        setUpWithAdapter(adapter);
    }

    public void notifyDataSetChanged() {
        mAdapter.notifyDataSetChanged();
    }

    public void setTabOnScreenLimit(int tabLimit) {
        mTabOnScreenLimit = tabLimit;
    }

    public void setOnTabReselectedListener(OnTabReselectedListener listener) {
        mOnTabReselectedListener = listener;
    }

    public void setOnCurrentTabLongPressListener(
            OnCurrentTabLongPressListener listener, long repeatIntervalMillis) {
        if (listener != null && repeatIntervalMillis <= 0) {
            throw new IllegalArgumentException("repeatIntervalMillis must be positive");
        }
        mCurrentTabLongPressRepeater.stop();
        mOnCurrentTabLongPressListener = listener;
        if (listener != null) {
            mCurrentTabLongPressRepeater.setRepeatIntervalMillis(repeatIntervalMillis);
        }
    }

    public interface OnTabReselectedListener {
        void onTabReselected(int position);
    }

    public interface OnCurrentTabLongPressListener {
        void onCurrentTabLongPress(int position);
    }

    /**
     * 只有长按当前选中的页码才刷新。位置每次实时解析，按住期间 tab 被重新绑定也不会拿到过期下标。
     */
    private boolean isCurrentTab(View tabView) {
        int position = getChildAdapterPosition(tabView);
        return mOnCurrentTabLongPressListener != null
                && mViewPager != null
                && position != NO_POSITION
                && position == mViewPager.getCurrentItem();
    }

    private void dispatchCurrentTabLongPress(View tabView) {
        mOnCurrentTabLongPressListener.onCurrentTabLongPress(getChildAdapterPosition(tabView));
    }

    @Override
    protected void onDetachedFromWindow() {
        mCurrentTabLongPressRepeater.stop();
        super.onDetachedFromWindow();
    }

    private class TabAdapter extends DefaultAdapter {

        public TabAdapter(ViewPager viewPager) {
            super(viewPager);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewHolder holder = super.onCreateViewHolder(parent, viewType);
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != NO_POSITION) {
                    if (pos == getViewPager().getCurrentItem()) {
                        if (mOnTabReselectedListener != null) {
                            mOnTabReselectedListener.onTabReselected(pos);
                        }
                    } else {
                        getViewPager().setCurrentItem(pos, false);
                    }
                }
            });
            mCurrentTabLongPressRepeater.attach(holder.itemView);
            return holder;
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            mCurrentTabLongPressRepeater.stop(holder.itemView);
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            TabTextView tabTextView = (TabTextView) holder.itemView;
            if (mTabOnScreenLimit > 0) {
                int width = getMeasuredWidth() / mTabOnScreenLimit;
                tabTextView.setMaxWidth(width);
                tabTextView.setMinWidth(width);
            } else {
                if (mTabMaxWidth > 0) {
                    tabTextView.setMaxWidth(mTabMaxWidth);
                }
                tabTextView.setMinWidth(mTabMinWidth);
            }
        }
    }

}
