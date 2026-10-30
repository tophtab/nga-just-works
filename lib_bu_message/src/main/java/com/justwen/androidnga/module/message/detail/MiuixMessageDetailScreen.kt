package com.justwen.androidnga.module.message.detail

import android.text.TextUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.justwen.androidnga.core.data.MessageArticlePageInfo
import com.justwen.androidnga.ui.compose.miuix.MiuixPageContent
import com.justwen.androidnga.ui.compose.miuix.MiuixPagingList
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SystemSender = "#SYSTEM#"

/** Messages of one thread; links inside a message open in the browser as in the classic UI. */
@Composable
internal fun MiuixMessageDetailScreen(
    page: MiuixPageContent,
    items: LazyPagingItems<MessageArticlePageInfo>,
    onOpenLink: (String) -> Unit,
) {
    MiuixPagingList(
        page = page,
        items = items,
        errorText = stringResource(com.justwen.androidnga.ui.compose.R.string.error_load_failed),
        retryText = "重试",
        // Clear the compose button of the Miuix page.
        bottomClearance = 88.dp,
    ) { message ->
        MiuixMessageArticle(message, onOpenLink)
    }
}

@Composable
private fun MiuixMessageArticle(message: MessageArticlePageInfo, onOpenLink: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val bodyStyle = MiuixTheme.textStyles.body1.copy(color = colors.onSurfaceContainer)
    val text = buildAnnotatedString {
        // android.util.Pair: first is the text, second marks a link.
        message.contentSections.forEach { section ->
            if (section.second != true) {
                append(readableMessageText(section.first))
            } else {
                // The link section may still hold its BBCode label, e.g. "=/read.php?..][b]title[/b]";
                // show the readable label and keep the original section as the link target.
                pushStringAnnotation(tag = "URL", annotation = section.first)
                withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium)) {
                    append(readableLinkLabel(section.first))
                }
                pop()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (!message.subject.isNullOrEmpty()) {
            Text(
                text = message.subject,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        @Suppress("DEPRECATION")
        ClickableText(
            text = text,
            style = bodyStyle,
            onClick = { offset ->
                text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { onOpenLink(it.item) }
            },
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = message.author.takeUnless(TextUtils::isEmpty) ?: SystemSender,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = message.time,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

private val BbCodeTag = Regex("""\[/?(?:b|i|u|del|color|size|font|align|url)(?:=[^\]]*)?]""")
private val BlankLineRun = Regex("""\n[ \t]*(?:\n[ \t]*){2,}""")

/**
 * Display-only cleanup for the Miuix message card: system messages keep NGA BBCode (for
 * example `[url=/read.php?...][b]...[/b]`) that the parser leaves in plain sections,
 * and their bodies carry runs of empty lines. Links stay in their own sections.
 */
internal fun readableMessageText(text: String): String =
    text.replace(BbCodeTag, "").replace(BlankLineRun, "\n\n")

/** Text of a parsed link section: an `=target]label` remainder shows only its label. */
internal fun readableLinkLabel(section: String): String {
    val label = if (section.startsWith("=") && section.contains("]")) section.substringAfter("]") else section
    return readableMessageText(label).ifBlank { section }
}
