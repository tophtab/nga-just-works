package com.justwen.androidnga.module.message.list

import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.module.message.R
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent
import com.justwen.androidnga.ui.compose.miuix.MiuixPagingList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape

private const val SystemSender = "#SYSTEM#"

/** HyperOS-style list of private message threads: avatar, sender, subject, date and replies. */
@Composable
internal fun MiuixMessageListScreen(
    page: MiuixPageContent,
    items: LazyPagingItems<MessageThreadPageInfo>,
    onOpen: (MessageThreadPageInfo) -> Unit,
) {
    MiuixPagingList(
        page = page,
        items = items,
        errorText = stringResource(com.justwen.androidnga.ui.compose.R.string.error_load_failed),
        retryText = "重试",
        // Clear the compose button of the Miuix page.
        bottomClearance = 88.dp,
    ) { message ->
        MiuixMessageThreadRow(message, onOpen)
    }
}

@Composable
private fun MiuixMessageThreadRow(message: MessageThreadPageInfo, onOpen: (MessageThreadPageInfo) -> Unit) {
    val sender = message.from_username.takeUnless(TextUtils::isEmpty) ?: SystemSender
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    BasicComponent(
        onClick = { onOpen(message) },
        startAction = {
            Image(
                painter = painterResource(R.drawable.default_avatar),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(miuixShape(20.dp)),
            )
        },
        endActions = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.time.orEmpty().substringBefore(' '),
                    style = MiuixTheme.textStyles.footnote1,
                    color = summary,
                )
                Text(
                    text = "${message.posts} 回复",
                    style = MiuixTheme.textStyles.footnote1,
                    color = summary,
                )
            }
        },
    ) {
        Text(
            text = sender,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = message.subject.orEmpty(),
            modifier = Modifier.padding(top = 2.dp),
            style = MiuixTheme.textStyles.body2,
            color = summary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
