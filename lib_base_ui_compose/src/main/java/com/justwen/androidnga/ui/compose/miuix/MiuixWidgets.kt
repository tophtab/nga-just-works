package com.justwen.androidnga.ui.compose.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Horizontal margin HyperOS settings/list cards keep from the screen edge. */
val MiuixCardHorizontalMargin = 12.dp

/** A rounded HyperOS card holding a group of rows, with the standard outer margins. */
@Composable
fun MiuixGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .padding(horizontal = MiuixCardHorizontalMargin)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

/** Centered loading indicator for initial page loads. */
@Composable
fun MiuixLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** Centered message with an optional retry action, for empty and error states. */
@Composable
fun MiuixMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            TextButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * HyperOS pull-to-refresh for a Miuix page body, bound to the page's collapsing top app bar.
 * [isRefreshing] stays hoisted by the caller; the indicator sits below the top app bar.
 */
@Composable
fun MiuixPullToRefresh(
    page: MiuixPageContent,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        pullToRefreshState = rememberPullToRefreshState(),
        // Indicator only: no "Pull down to refresh" / "Release to refresh" captions.
        refreshTexts = emptyList(),
        topAppBarScrollBehavior = page.scrollBehavior,
        contentPadding = PaddingValues(top = page.padding.calculateTopPadding() + 12.dp),
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/**
 * Paged list on a Miuix page: HyperOS pull-to-refresh, card-grouped rows, and loading/error
 * states driven by the paging load state.
 */
@Composable
fun <T : Any> MiuixPagingList(
    page: MiuixPageContent,
    items: LazyPagingItems<T>,
    errorText: String,
    retryText: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    /** Extra bottom space so the last item clears a floating action button. */
    bottomClearance: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    val refreshState = items.loadState.refresh
    var userRefreshing by remember { mutableStateOf(false) }
    if (userRefreshing && refreshState !is LoadState.Loading) {
        userRefreshing = false
    }
    val count = items.itemCount
    PullToRefresh(
        isRefreshing = userRefreshing,
        onRefresh = {
            userRefreshing = true
            items.refresh()
        },
        pullToRefreshState = rememberPullToRefreshState(),
        // Indicator only: no "Pull down to refresh" / "Release to refresh" captions.
        refreshTexts = emptyList(),
        topAppBarScrollBehavior = page.scrollBehavior,
        contentPadding = PaddingValues(top = page.padding.calculateTopPadding() + 12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            count == 0 && refreshState is LoadState.Loading && !userRefreshing ->
                MiuixLoading(Modifier.padding(top = page.padding.calculateTopPadding()))

            count == 0 && refreshState is LoadState.Error ->
                MiuixMessage(
                    text = errorText,
                    actionText = retryText,
                    onAction = { items.retry() },
                    modifier = Modifier.padding(top = page.padding.calculateTopPadding()),
                )

            else -> LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .then(page.scrollModifier),
                contentPadding = miuixListPadding(page.padding, extraBottom = bottomClearance),
            ) {
                item(key = "miuix-top-gap") { Box(Modifier.padding(top = 12.dp)) }
                items(count) { index ->
                    val item = items[index] ?: return@items
                    // One Miuix card per item, separated by spacing, like HyperOS list cards.
                    Card(
                        modifier = Modifier
                            .padding(horizontal = MiuixCardHorizontalMargin)
                            .padding(bottom = 10.dp),
                    ) {
                        itemContent(item)
                    }
                }
                if (items.loadState.append is LoadState.Loading) {
                    item(key = "miuix-append") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row of a card-grouped list. Rows of the same list are drawn as a single continuous card:
 * only the first and last rows round their outer corners, as in HyperOS lists.
 */
@Composable
fun MiuixListCardItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val top = if (index == 0) 16.dp else 0.dp
    val bottom = if (index == count - 1) 16.dp else 0.dp
    Box(
        modifier = modifier
            .padding(horizontal = MiuixCardHorizontalMargin)
            .miuixCardSegment(top, bottom),
    ) {
        CardContentColor(content)
    }
}

/** Adds standard bottom spacing after a group of card rows inside a LazyColumn. */
fun LazyListScope.miuixGroupGap(key: String) {
    item(key = key) { Box(Modifier.padding(bottom = 12.dp)) }
}
