package com.justwen.androidnga.module.message.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Default (classic Compose) private-message composer. Rendering only. */
@Preview
@Composable
internal fun MessagePostScreen(
    newPostMode: Boolean = true,
    recipient: String = "",
    subject: String = "",
    content: String = "",
    onRecipientChange: (String) -> Unit = {},
    onSubjectChange: (String) -> Unit = {},
    onContentChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
) {
    Column(
        modifier = Modifier.padding(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp
        )
    ) {
        if (newPostMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                TextField(modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    value = recipient,
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                    onValueChange = onRecipientChange,
                    placeholder = { Text(text = "输入ID/用户名") })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                value = subject,
                colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                onValueChange = onSubjectChange,
                placeholder = { Text(text = "标题") })
        }

        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            TextField(modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
                value = content,
                colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                onValueChange = onContentChange,
                placeholder = { Text(text = "内容") })

            TextButton(modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                ),
                onClick = onSend) {
                Text(text = "发送消息", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        }
    }
}
