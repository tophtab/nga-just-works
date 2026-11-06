package gov.anzong.androidnga.activity.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.justwen.androidnga.ui.compose.miuix.MiuixGroupCard
import com.justwen.androidnga.ui.compose.miuix.MiuixNavigationIcon
import com.justwen.androidnga.ui.compose.miuix.MiuixNavIcon
import com.justwen.androidnga.ui.compose.miuix.NgaMiuixTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperCheckbox
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixCapsuleShape

/**
 * Miuix search page: capsule search field in the top bar, contour tabs for the search scope,
 * option rows and history chips. Query execution and history stay in [SearchViewModel].
 */
@Composable
internal fun MiuixSearchPage(viewModel: SearchViewModel, onBack: () -> Unit, onSearch: (String) -> Unit) {
    NgaMiuixTheme {
        var query by remember { mutableStateOf("") }
        var tabIndex by remember { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixNavigationIcon(MiuixNavIcon.Back, onBack)
                    Spacer(Modifier.width(8.dp))
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { if (it.isNotBlank()) onSearch(it) },
                        expanded = true,
                        onExpandedChange = {},
                        label = "搜索",
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState()),
            ) {
                TabRowWithContour(
                    tabs = viewModel.searchData.map { it.first },
                    selectedTabIndex = tabIndex,
                    onTabSelected = { index ->
                        tabIndex = index
                        viewModel.searchMode.value = viewModel.searchData[index].second
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
                SearchOptions(viewModel, viewModel.searchData[tabIndex].second)
                SearchHistory(viewModel, onSearch)
            }
        }
    }
}

@Composable
private fun SearchOptions(viewModel: SearchViewModel, mode: Int) {
    when (mode) {
        SearchViewModel.SEARCH_MODE_TOPIC -> {
            var selected by remember { mutableStateOf(viewModel.searchTopicMode) }
            var withContent by remember { mutableStateOf(viewModel.searchTopicWithContent) }
            SmallTitle(text = "搜索选项")
            MiuixGroupCard {
                viewModel.searchTopicData.forEach { (title, value) ->
                    SuperRadioButton(
                        title = title,
                        selected = selected == value,
                        onClick = {
                            selected = value
                            viewModel.searchTopicMode = value
                        },
                    )
                }
                SuperCheckbox(
                    title = "包括正文",
                    checked = withContent,
                    onCheckedChange = {
                        withContent = it
                        viewModel.searchTopicWithContent = it
                    },
                )
            }
        }

        SearchViewModel.SEARCH_MODE_USER -> {
            var selected by remember { mutableStateOf(viewModel.searchUserMode) }
            SmallTitle(text = "搜索选项")
            MiuixGroupCard {
                viewModel.searchUserData.forEach { (title, value) ->
                    SuperRadioButton(
                        title = title,
                        selected = selected == value,
                        onClick = {
                            selected = value
                            viewModel.searchUserMode = value
                        },
                    )
                }
            }
        }

        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistory(viewModel: SearchViewModel, onSearch: (String) -> Unit) {
    val history by viewModel.keyList.observeAsState(emptyList())
    if (history.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(text = "搜索历史", modifier = Modifier.weight(1f))
        TextButton(
            text = "全部清除",
            onClick = { viewModel.clearHistory() },
            modifier = Modifier.padding(end = 16.dp),
        )
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        history.forEach { key ->
            HistoryChip(key, onClick = { onSearch(key) }, onDelete = { viewModel.deleteHistory(key) })
        }
    }
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(miuixCapsuleShape())
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MiuixTheme.textStyles.body2, color = colors.onSurfaceContainer)
        Icon(
            imageVector = MiuixIcons.Close,
            contentDescription = "删除",
            tint = colors.onSurfaceVariantSummary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(16.dp)
                .clip(miuixCapsuleShape())
                .clickable(onClick = onDelete),
        )
    }
}
