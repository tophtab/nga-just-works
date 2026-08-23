package gov.anzong.androidnga.base.common;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivityHelper;

public class SwipeBackHelper {

    private static final int EDGE_SIZE_DP = 10;

    private SwipeBackActivityHelper mHelper;

    public void onCreate(Activity activity) {
        mHelper = new SwipeBackActivityHelper(activity);
        mHelper.onActivityCreate();
        SwipeBackLayout swipeBackLayout = mHelper.getSwipeBackLayout();
        float density = activity.getResources().getDisplayMetrics().density;
        swipeBackLayout.setEdgeSize((int) (EDGE_SIZE_DP * density + 0.5f));
        swipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_ALL);
        swipeBackLayout.addSwipeListener(new TranslucentOnEdgeTouch(activity));
    }

    public void onPostCreate() {
        mHelper.onPostCreate();
    }

    /**
     * onActivityCreate clears the decor background so the activity underneath shows through
     * while dragging, and attachToActivity then repaints the moved content root with the theme
     * windowBackground. That resource is #202020 at night while the rest of the app uses
     * background_color (#080C10), so the caller has to paint the content root itself.
     */
    public void setContentBackgroundColor(@ColorInt int color) {
        View contentRoot = mHelper.getSwipeBackLayout().getChildAt(0);
        if (contentRoot != null) {
            contentRoot.setBackgroundColor(color);
        }
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return (T) mHelper.findViewById(id);
    }

    /**
     * The library reveals the activity underneath by reflecting on the hidden
     * Activity#convertToTranslucent. That reflection has been blocked since Android 9 and
     * fails silently inside the library, so the dragged page exposes a black gap instead of
     * the previous screen. Activity#setTranslucent is the public replacement added in API 30.
     *
     * The theme already carries android:windowIsTranslucent, but that alone does not keep the
     * activity below drawn on current platforms; the explicit call does.
     */
    private static final class TranslucentOnEdgeTouch implements SwipeBackLayout.SwipeListener {

        private final Activity mActivity;

        TranslucentOnEdgeTouch(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void onScrollStateChange(int state, float scrollPercent) {
        }

        @Override
        public void onEdgeTouch(int edgeFlag) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return;
            }
            try {
                mActivity.setTranslucent(true);
            } catch (Exception e) {
                // A refused conversion only costs the reveal, never the drag itself.
            }
        }

        @Override
        public void onScrollOverThreshold() {
        }
    }

}
