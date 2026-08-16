package com.justwen.androidnga.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.alibaba.android.arouter.launcher.ARouter
import com.justwen.androidnga.base.service.api.IThemeManagerService

abstract class BaseActivity : AppCompatActivity() {

    protected var mNaviBarHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        initContentView()
        initHandleBackEvent()
        initEdgeToEdge()
        initStatusBar()
        initNavigationBar()
    }

    private fun initHandleBackEvent() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = onHandleBackEvent()
                if (!isEnabled) {
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    protected fun onHandleBackEvent(): Boolean {
        return false
    }

    private fun initStatusBar() {
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)
        controller.isAppearanceLightStatusBars = false
    }

    private fun initNavigationBar() {
        val background = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, background, true)
        val backgroundColor = if (background.resourceId != 0) {
            ContextCompat.getColor(this, background.resourceId)
        } else {
            background.data
        }
        window.navigationBarColor = backgroundColor
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.decorView.setBackgroundColor(backgroundColor)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun initEdgeToEdge() {
        enableEdgeToEdge()
        val contentView = findViewById<View>(android.R.id.content)
        if (contentView != null) {
            val initialPaddingLeft = contentView.paddingLeft
            val initialPaddingTop = contentView.paddingTop
            val initialPaddingRight = contentView.paddingRight
            val initialPaddingBottom = contentView.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(
                contentView
            ) { _, insets ->
                val stateBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val statusView = window.decorView.findViewById<View?>(R.id.status_bar)
                    ?: View(contentView.context).also { created ->
                        created.id = R.id.status_bar
                        val parent = contentView.parent as ViewGroup
                        parent.addView(created, 0)
                    }
                statusView.layoutParams = statusView.layoutParams?.apply {
                    height = stateBars.top
                } ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    stateBars.top
                )
                statusView.setBackgroundColor(getPrimaryColor())

                val navaBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                mNaviBarHeight = navaBars.bottom
                contentView.setPadding(
                    initialPaddingLeft,
                    initialPaddingTop,
                    initialPaddingRight,
                    initialPaddingBottom + navaBars.bottom
                )
                insets
            }
        }
    }

    private fun getPrimaryColor(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    open fun initContentView() {
        setContentView(R.layout.activity_toolbar_template)
        setupToolbar()
    }

    open fun setupToolbar(toolbar: Toolbar = findViewById(R.id.toolbar)) {
        if (supportActionBar == null) {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setHomeButtonEnabled(true)
            }
        }
    }

    private fun initTheme() {
        val themeManager = ARouter.getInstance().build(IThemeManagerService.ROUTER_PATH)
            .navigation() as IThemeManagerService
        setTheme(themeManager.getTheme())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
