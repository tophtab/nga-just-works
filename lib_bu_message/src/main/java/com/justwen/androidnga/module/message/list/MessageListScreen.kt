package com.justwen.androidnga.module.message.list

import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.module.message.R
import com.justwen.androidnga.ui.compose.widget.PullRefreshColumn

/** Default (classic Compose) private-message list. Rendering only. */
@Composable
internal fun MessageListScreen(
    items: LazyPagingItems<MessageThreadPageInfo>,
    onOpen: (MessageThreadPageInfo) -> Unit,
) {
    PullRefreshColumn(
        columnItem = { MessageListItem(messageInfo = it, onOpen = onOpen) },
        lazyPagingItems = items,
        onRefresh = { items.refresh() },
    )
}

@Preview
@Composable
internal fun MessageListItem(
    messageInfo: MessageThreadPageInfo = MessageThreadPageInfo(),
    onOpen: (MessageThreadPageInfo) -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onOpen(messageInfo) }
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.default_avatar),
            modifier = Modifier
                .padding(end = 16.dp)
                .size(36.dp),
            contentDescription = null
        )

        var userName = messageInfo.from_username
        if (TextUtils.isEmpty(userName)) {
            userName = "#SYSTEM#"
        }

        Column(modifier = Modifier.weight(2f)) {
            Text(text = userName, maxLines = 1, color = Color(0xFF545454), fontSize = 16.sp)
            Text(
                messageInfo.subject,
                Modifier.padding(top = 8.dp),
                maxLines = 2,
                color = Color(0xFF294563),
                fontSize = 12.sp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = messageInfo.time.split(" ")[0],
                maxLines = 1,
                color = Color(0xFF294563),
                fontSize = 12.sp
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                    painter = painterResource(R.drawable.replies_icon),
                    contentDescription = null,
                )
                Text(
                    messageInfo.posts.toString(),
                    color = Color(0xFF294563),
                    fontSize = 12.sp
                )
            }
        }
    }
}
