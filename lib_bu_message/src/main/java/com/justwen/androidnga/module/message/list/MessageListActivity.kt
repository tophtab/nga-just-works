package com.justwen.androidnga.module.message.list

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.paging.compose.collectAsLazyPagingItems
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.base.service.api.IUserManagerService
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent
import com.justwen.androidnga.ui.compose.widget.OptionMenuData

/**
 * Route shell for the private-message list: menu, FAB, ViewModel lookup and navigation.
 * All rendering lives in [MessageListScreen] / [MiuixMessageListScreen].
 */
@Route(path = ARouterConstants.ACTIVITY_MESSAGE_LIST)
class MessageListActivity : BaseComposeActivity() {

    private val viewModel: MessageListViewModel by lazy {
        ViewModelProvider(this)[MessageListViewModel::class.java]
    }

    override fun getOptionMenuData(): List<OptionMenuData>? {
        val accountSwitch = OptionMenuData(title = "切换账号", action = {
            showAccountSwitchDialog()
        })
        return arrayListOf(accountSwitch)
    }

    private fun showAccountSwitchDialog() {
        val userManager = ARouter.getInstance().build(IUserManagerService.ROUTER_PATH)
            .navigation() as IUserManagerService
        userManager.showUserSwitchDialog(this) {
            // Rows are account-scoped: rebuild the Pager so the next request uses the
            // newly active account Cookie instead of replaying the cached page.
            viewModel.refresh()
            buildContentView()
        }
    }

    override fun getFabClickAction(): () -> Unit {
        return {
            ARouter.getInstance().build(ARouterConstants.ACTIVITY_MESSAGE_POST)
                .withString("action", "new")
                .navigation(this)
        }
    }

    @Composable
    override fun ContentView() {
        MessageListScreen(viewModel.messageListData.collectAsLazyPagingItems(), ::startMessageDetail)
    }

    @Composable
    override fun MiuixContentView(page: MiuixPageContent) {
        MiuixMessageListScreen(page, viewModel.messageListData.collectAsLazyPagingItems(), ::startMessageDetail)
    }

    internal fun startMessageDetail(messageInfo: MessageThreadPageInfo) {
        ARouter.getInstance().build(ARouterConstants.ACTIVITY_MESSAGE_DETAIL)
            .withInt("mid", messageInfo.mid)
            .withString("subject", messageInfo.subject.orEmpty())
            .withString("recipient", messageInfo.from_username.orEmpty())
            .navigation(this)
    }
}
