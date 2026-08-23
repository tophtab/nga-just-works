package gov.anzong.androidnga.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;

import com.justwen.androidnga.cloud.CloudServerManager;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.common.SwipeBackHelper;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.DeviceUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import sp.phone.common.NotificationController;
import sp.phone.common.PhoneConfiguration;
import sp.phone.theme.ThemeManager;
import gov.anzong.androidnga.common.util.NLog;

/**
 * Created by liuboyu on 16/6/28.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected PhoneConfiguration mConfig;

    private boolean mToolbarEnabled;

    private boolean mComposeEnabled;

    private int mNaviBarHeight;

    private boolean mSwipeBackEnabled = true;

    /**
     * Non null only when swipe back is actually running for this activity, which requires both
     * a subclass opt in and a device with on screen navigation buttons.
     */
    private SwipeBackHelper mSwipeBackHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mConfig = PhoneConfiguration.getInstance();
        updateThemeUi();
        super.onCreate(savedInstanceState);
        ThemeManager.getInstance().initializeWebTheme(this);

        setupSwipeBack();

        try {
            configureSystemBars();
        } catch (Exception e) {
            NLog.e("configure system bars exception occur: " + e);
        }
        enableEdge2Edge();
    }

    /**
     * Gesture navigation already provides a screen edge back swipe, so the in app one would
     * only duplicate it. Restore it exclusively for devices driven by navigation buttons.
     * Subclasses opt out by calling {@link #setSwipeBackEnable(boolean)} before super.onCreate.
     */
    private void setupSwipeBack() {
        if (!mSwipeBackEnabled || !DeviceUtils.hasNavigationButtons(this)) {
            return;
        }
        try {
            SwipeBackHelper helper = new SwipeBackHelper();
            helper.onCreate(this);
            mSwipeBackHelper = helper;
        } catch (Exception e) {
            // Degrade to the plain activity rather than taking down every navigation button
            // device. Leaving the field null also lets configureSystemBars below repaint the
            // decor background the helper may already have cleared.
            mSwipeBackHelper = null;
            NLog.e("setup swipe back exception occur: " + e);
        }
    }

    protected void setSwipeBackEnable(boolean enable) {
        mSwipeBackEnabled = enable;
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mSwipeBackHelper != null) {
            mSwipeBackHelper.onPostCreate();
            mSwipeBackHelper.setContentBackgroundColor(ContextUtils.getColor(R.color.background_color));
        }
    }

    @Override
    public <T extends View> T findViewById(int id) {
        T view = super.findViewById(id);
        if (view == null && mSwipeBackHelper != null) {
            view = mSwipeBackHelper.findViewById(id);
        }
        return view;
    }

    /**
     * Android 15 enforces edge-to-edge for targetSdk 35. Configure both the
     * legacy window color and the decor background so transparent gesture/IME
     * navigation areas still use the active NGA theme instead of white.
     */
    private void configureSystemBars() {
        int backgroundColor = ContextUtils.getColor(R.color.background_color);
        getWindow().setNavigationBarColor(backgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (mSwipeBackHelper == null) {
            // SwipeBackLayout needs an empty decor background to reveal the activity underneath
            // while dragging, so the swipe back path paints the same color on the content root
            // in onPostCreate instead. Keep this call written exactly as is, SystemThemeContractTest
            // asserts on the literal source text.
            getWindow().getDecorView().setBackgroundColor(backgroundColor);
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(!ThemeManager.getInstance().isNightMode());
    }

    public void setComposeEnabled(boolean composeEnabled) {
        mComposeEnabled = composeEnabled;
    }

    private void enableEdge2Edge() {
        View contentView = findViewById(android.R.id.content);
        if (mToolbarEnabled && !mComposeEnabled && contentView != null) {
            final int initialPaddingLeft = contentView.getPaddingLeft();
            final int initialPaddingTop = contentView.getPaddingTop();
            final int initialPaddingRight = contentView.getPaddingRight();
            final int initialPaddingBottom = contentView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(contentView, new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets stateBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    View statusView = getWindow().getDecorView().findViewById(R.id.status_bar);
                    if (statusView == null) {
                        ViewGroup parent = (ViewGroup) contentView.getParent();
                        statusView = new View(contentView.getContext());
                        statusView.setId(R.id.status_bar);
                        parent.addView(statusView, 0);
                    }
                    ViewGroup.LayoutParams statusLayoutParams = statusView.getLayoutParams();
                    if (statusLayoutParams == null) {
                        statusLayoutParams = new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, stateBars.top);
                    } else {
                        statusLayoutParams.height = stateBars.top;
                    }
                    statusView.setLayoutParams(statusLayoutParams);
                    statusView.setBackgroundColor(
                            ThemeManager.getInstance().getPrimaryColor(contentView.getContext()));

                    Insets navaBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    mNaviBarHeight = navaBars.bottom;
                    contentView.setPadding(
                            initialPaddingLeft,
                            initialPaddingTop,
                            initialPaddingRight,
                            initialPaddingBottom + navaBars.bottom);
                    return insets;
                }
            });
        }
    }

    // Android15上开启EdgeToEdge后adjustResize会失效，这里临时做下兼容
    protected void compatActivityAdjustResize(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        View content = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        final Rect r = new Rect();
        content.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            content.getWindowVisibleDisplayFrame(r);
            int screenHeight = content.getRootView().getHeight();
            int keyboardHeight = screenHeight - r.bottom - mNaviBarHeight;
            if (keyboardHeight > screenHeight / 4) { // 键盘高度超过屏幕1/4
                content.setPadding(0, 0, 0, keyboardHeight);
            } else {
                content.setPadding(0, 0, 0, 0);
            }
        });
    }

    protected void setToolbarEnabled(boolean enabled) {
        mToolbarEnabled = enabled;
    }

    public void setupToolbar(Toolbar toolbar) {
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            }
        }
    }

    public void setupToolbar() {
        setupToolbar((Toolbar) findViewById(R.id.toolbar));
    }

    public void setupActionBar() {
        if (mToolbarEnabled) {
            setupToolbar();
        } else {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            }
        }
    }

    protected void updateThemeUi() {
        ThemeManager tm = ThemeManager.getInstance();
        setTheme(tm.getTheme(mToolbarEnabled));
    }

    @Deprecated
    public void setupActionBar(Toolbar toolbar) {
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);

        }
        return true;

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            return super.dispatchTouchEvent(ev);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            return super.dispatchKeyEvent(event);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        view.setFitsSystemWindows(!mToolbarEnabled);
    }

    @Override
    protected void onResume() {
        checkUpgrade();
        NotificationController.getInstance().checkNotificationDelay();
        super.onResume();
    }

    private void checkUpgrade() {
        if (PreferenceUtils.getData(PreferenceKey.KEY_CHECK_UPGRADE_STATE, true)) {
            long time = PreferenceUtils.getData(PreferenceKey.KEY_CHECK_UPGRADE_TIME, 0L);
            if (System.currentTimeMillis() - time > 1000 * 60 * 60 * 24) {
                CloudServerManager.checkUpgrade();
                PreferenceUtils.putData(PreferenceKey.KEY_CHECK_UPGRADE_TIME, System.currentTimeMillis());
            }
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        try {
            super.startActivityForResult(intent, requestCode, options);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }
}
