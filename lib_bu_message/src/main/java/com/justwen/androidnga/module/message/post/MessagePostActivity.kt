package com.justwen.androidnga.module.message.post

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.module.message.R
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.launch

/**
 * Route shell for composing a private message: title, ViewModel lookup, screen calls and
 * one-shot result feedback. Validation and sending live in the ViewModel.
 */
@Route(path = ARouterConstants.ACTIVITY_MESSAGE_POST)
class MessagePostActivity : BaseComposeActivity() {

    private val viewModel: MessagePostViewModel by lazy {
        ViewModelProvider(this)[MessagePostViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel.isNewPostMode) {
            setTitle(R.string.new_message)
        } else {
            setTitle(R.string.reply_message)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MessagePostEvent.Success -> {
                            ToastUtils.success(event.message)
                            finish()
                        }
                        is MessagePostEvent.Failure -> ToastUtils.error(event.message)
                    }
                }
            }
        }
    }

    @Composable
    override fun ContentView() {
        MessagePostScreen(
            newPostMode = viewModel.isNewPostMode,
            recipient = viewModel.recipient,
            subject = viewModel.subject,
            content = viewModel.content,
            onRecipientChange = viewModel::onRecipientChange,
            onSubjectChange = viewModel::onSubjectChange,
            onContentChange = viewModel::onContentChange,
            onSend = viewModel::send,
        )
    }

    @Composable
    override fun MiuixContentView(page: MiuixPageContent) {
        MiuixMessageComposer(
            page = page,
            newPostMode = viewModel.isNewPostMode,
            recipient = viewModel.recipient,
            subject = viewModel.subject,
            content = viewModel.content,
            onRecipientChange = viewModel::onRecipientChange,
            onSubjectChange = viewModel::onSubjectChange,
            onContentChange = viewModel::onContentChange,
            onSend = viewModel::send,
        )
    }
}
