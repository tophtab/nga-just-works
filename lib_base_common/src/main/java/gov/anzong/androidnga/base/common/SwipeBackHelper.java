package gov.anzong.androidnga.base.common;

import android.app.Activity;
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

}
