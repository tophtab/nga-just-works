package com.justwen.androidnga.module.message.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.paging.compose.collectAsLazyPagingItems
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent

/**
 * Route shell for one private-message thread: FAB, ViewModel lookup, screen calls and the
 * browser hand-off for links inside a message.
 */
@Route(path = ARouterConstants.ACTIVITY_MESSAGE_DETAIL)
class MessageDetailActivity : BaseComposeActivity() {

    private val viewModel: MessageDetailViewModel by lazy {
        ViewModelProvider(this)[MessageDetailViewModel::class.java]
    }

    override fun getFabClickAction(): () -> Unit {
        return {
            ARouter.getInstance()
                .build(ARouterConstants.ACTIVITY_MESSAGE_POST)
                .withInt("mid", viewModel.mid.toInt())
                .withString("action", "reply")
                .withString("to", viewModel.recipient)
                .withString("title", viewModel.title)
                .navigation(this)
        }
    }

    @Composable
    override fun ContentView() {
        MessageDetailScreen(viewModel.messageDetailData.collectAsLazyPagingItems(), ::startWebView)
    }

    @Composable
    override fun MiuixContentView(page: MiuixPageContent) {
        MiuixMessageDetailScreen(page, viewModel.messageDetailData.collectAsLazyPagingItems(), ::startWebView)
    }

    private fun startWebView(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }
}
