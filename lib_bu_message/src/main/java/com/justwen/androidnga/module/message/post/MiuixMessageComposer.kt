package com.justwen.androidnga.module.message.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent
import com.justwen.androidnga.ui.compose.miuix.miuixListPadding
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * Miuix composer for a private message. The draft text lives in the shared ViewModel, so
 * both appearances edit the same state; this composable only renders and forwards edits.
 */
@Composable
internal fun MiuixMessageComposer(
    page: MiuixPageContent,
    newPostMode: Boolean,
    recipient: String,
    subject: String,
    content: String,
    onRecipientChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val padding = miuixListPadding(page.padding)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .then(page.scrollModifier)
            .verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (newPostMode) {
            TextField(
                value = recipient,
                onValueChange = onRecipientChange,
                label = "输入ID/用户名",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
        TextField(
            value = subject,
            onValueChange = onSubjectChange,
            label = "标题",
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        TextField(
            value = content,
            onValueChange = onContentChange,
            label = "内容",
            minLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
        )
        TextButton(
            text = "发送消息",
            onClick = onSend,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )
    }
}
