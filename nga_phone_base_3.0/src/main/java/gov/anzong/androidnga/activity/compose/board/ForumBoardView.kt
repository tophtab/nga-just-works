package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.window.layout.WindowMetricsCalculator
import coil.compose.rememberAsyncImagePainter
import com.justwen.androidnga.ui.compose.miuix.isMiuixComposition
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.justwen.androidnga.ui.compose.widget.PagerInteractionState
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.common.PreferenceKey
import gov.anzong.androidnga.core.board.data.BoardEntity
import kotlinx.coroutines.launch
import kotlin.math.abs


@Suppress("ModifierParameter")
@Composable
fun ForumBoardView(
    forumBoardViewModel: ForumBoardViewModel,
    pagerUserScrollEnabled: Boolean = true,
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
    onFavoriteReorderActiveChanged: (Boolean) -> Unit = {},
) {
    val boardData by forumBoardViewModel.boardLiveData.observeAsState()
    val compactSetting = rememberHomeLayoutCompact()
    val fillsScreen = homeFillsScreen()
    val compact = compactSetting && fillsScreen
    var favoriteReorderActive by remember { mutableStateOf(false) }
    var tabReorderActive by remember { mutableStateOf(false) }
    var homeOrderSnapshot by remember { mutableStateOf<List<String>?>(null) }
    val currentOnFavoriteReorderActiveChanged by
        rememberUpdatedState(onFavoriteReorderActiveChanged)
    DisposableEffect(Unit) {
        currentOnFavoriteReorderActiveChanged(false)
        onDispose {
            homeOrderSnapshot?.let(forumBoardViewModel::cancelHomeBoardReorder)
            homeOrderSnapshot = null
            favoriteReorderActive = false
            tabReorderActive = false
            currentOnFavoriteReorderActiveChanged(false)
        }
    }
    boardData?.let { boards ->
        val tabs = boards.map { it.name }
        val tabKeys = boards.map { it.id }
        val initialPage = if (forumBoardViewModel.bookmarkSizeLiveData.value!! > 0) 0 else 1
        TabLayoutWithPager(
            tabs = tabs,
            initialPage = initialPage,
            userScrollEnabled = pagerUserScrollEnabled &&
                !favoriteReorderActive &&
                !tabReorderActive,
            pagerModifier = pagerModifier,
            onPagerInteractionChanged = onPagerInteractionChanged,
            tabKeys = tabKeys,
            reorderableTabRange = if (boards.size > 1) 1..boards.lastIndex else null,
            onTabReorderStart = { tabKey ->
                if (tabKey != "bookmark") {
                    homeOrderSnapshot = forumBoardViewModel.beginHomeBoardReorder()
                }
            },
            onTabReorderMove = { fromIndex, toIndex ->
                if (fromIndex <= 0 || toIndex <= 0) {
                    false
                } else {
                    forumBoardViewModel.moveHomeBoard(fromIndex - 1, toIndex - 1)
                }
            },
            onTabReorderCommit = {
                homeOrderSnapshot?.let(forumBoardViewModel::commitHomeBoardReorder)
                homeOrderSnapshot = null
            },
            onTabReorderCancel = {
                homeOrderSnapshot?.let(forumBoardViewModel::cancelHomeBoardReorder)
                homeOrderSnapshot = null
            },
            onTabReorderActiveChanged = { tabReorderActive = it },
        ) { pageIndex ->
            HomePage(compact) {
                ForumBoardContent(pageIndex, forumBoardViewModel) { active ->
                    favoriteReorderActive = active
                    currentOnFavoriteReorderActiveChanged(active)
                }
            }
        }
    }
}

/**
 * One home page (a board group's grid) at its layout width: the compact home layout keeps the
 * grid at phone width and centred (three columns), the tab row above it stays full width. The
 * composition is the same either way, so switching (e.g. when the home becomes a split pane)
 * keeps the grid's scroll position.
 */
@Composable
private fun HomePage(compact: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .then(if (compact) Modifier.widthIn(max = COMPACT_HOME_MAX_WIDTH) else Modifier)
                .fillMaxSize(),
        ) {
            content()
        }
    }
}

/** 首页布局「紧凑」时板块宫格的最大宽度：手机宽度，3 列。 */
private val COMPACT_HOME_MAX_WIDTH = 480.dp

/** 设置里的「首页布局」，在设置页修改后回到首页即生效。 */
@Composable
private fun rememberHomeLayoutCompact(): Boolean {
    var compact by remember { mutableStateOf(readHomeLayoutCompact()) }
    DisposableEffect(Unit) {
        val preferences = PreferenceUtils.getDefaultPreferences()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceKey.KEY_HOME_LAYOUT) compact = readHomeLayoutCompact()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return compact
}

private fun readHomeLayoutCompact(): Boolean =
    PreferenceUtils.getData(PreferenceKey.KEY_HOME_LAYOUT, HOME_LAYOUT_FILL) == HOME_LAYOUT_COMPACT

private const val HOME_LAYOUT_FILL = "fill"
private const val HOME_LAYOUT_COMPACT = "compact"

/**
 * Whether the home page has the whole screen to itself rather than one pane of a split (the
 * tablet split or system split screen): the compact layout only narrows a full-screen home, a
 * split pane looks the same in both layouts. Entering or leaving a split resizes the window,
 * which arrives here as a configuration change (MainActivity handles size changes itself).
 */
@Composable
private fun homeFillsScreen(): Boolean {
    val activity = LocalActivity.current ?: return true
    val configuration = LocalConfiguration.current
    return remember(activity, configuration) {
        val metrics = WindowMetricsCalculator.getOrCreate()
        metrics.computeCurrentWindowMetrics(activity).bounds.width() >=
            metrics.computeMaximumWindowMetrics(activity).bounds.width()
    }
}

@Composable
fun ForumBoardGroupView(board: BoardEntity, context: Context = ContextUtils.getContext()) {
    if (isMiuixComposition) {
        SmallTitle(text = board.name)
        return
    }
    Row(modifier = Modifier.padding(16.dp)) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.default_board_icon),
            contentDescription = "",
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = board.name,
            color = Color(context.resources.getColor(R.color.text_color, null)),
        )
    }
}

@Composable
private fun ForumBoardGridItemView(
    child: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    modifier: Modifier = Modifier,
    clickEnabled: Boolean = true,
    context: Context = ContextUtils.getContext()
) {
    val paddingValue = 4.dp
    val imageSize = 48.dp
    val miuix = isMiuixComposition
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(paddingValue)
            .fillMaxWidth()
            .then(if (miuix) Modifier.miuixBoardTile() else Modifier)
            .clickable {
                if (clickEnabled) {
                    forumBoardViewModel.showTopicList(child)
                }
            }
    ) {
        // Miuix tiles are cards: give the icon and label room inside the card.
        val tileInset = if (miuix) 10.dp else paddingValue
        Spacer(modifier = Modifier.height(tileInset))
        val resId = remember(child.fid, child.stid) { getResId(child) }
        if (resId > 0) {
            Image(
                modifier = Modifier.size(imageSize),
                painter = painterResource(id = resId),
                contentDescription = ""
            )
        } else {
            val url = child.iconUrl
            Image(
                modifier = Modifier.size(imageSize),
                painter = rememberAsyncImagePainter(
                    model = url,
                    placeholder = painterResource(id = R.drawable.default_board_icon),
                    error = painterResource(id = R.drawable.default_board_icon)
                ),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .padding(top = paddingValue, bottom = paddingValue)
                .then(if (miuix) Modifier.padding(horizontal = 8.dp) else Modifier),
            color = if (miuix) miuixBoardTextColor() else Color(context.resources.getColor(R.color.text_color, null)),
            text = child.name,
            maxLines = if (miuix) 1 else Int.MAX_VALUE,
            overflow = if (miuix) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
        Spacer(modifier = Modifier.height(tileInset))
    }
}

/** Resource ids of bundled board icons by fid; getIdentifier is a reflective lookup per call. */
private val boardIconIds = HashMap<Int, Int>()

private fun getResId(board: BoardEntity): Int {
    if (board.stid != 0) {
        return 0
    }

    val fid = board.fid
    return boardIconIds.getOrPut(fid) {
        val resName = if (fid > 0) "p$fid" else "p_" + abs(fid)
        ContextUtils.getResources()
            .getIdentifier(resName, "drawable", ContextUtils.getContext().packageName)
    }
}

@Composable
fun ForumBoardBookmarkContent(
    bookmark: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    onReorderActiveChanged: (Boolean) -> Unit = {},
) {
    val bookmarks by forumBoardViewModel.bookmarkBoardsLiveData.observeAsState(
        bookmark.children?.toList().orEmpty()
    )
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeSize = with(density) { 72.dp.toPx() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggedIndex by remember { mutableStateOf(-1) }
    var draggedOffset by remember { mutableStateOf(Offset.Zero) }
    var reorderSnapshot by remember { mutableStateOf<List<BoardEntity>?>(null) }
    var suppressClickKey by remember { mutableStateOf<String?>(null) }

    fun finishDrag(commit: Boolean) {
        val snapshot = reorderSnapshot
        if (snapshot != null) {
            if (commit) {
                forumBoardViewModel.commitBookmarkReorder(snapshot)
            } else {
                forumBoardViewModel.cancelBookmarkReorder(snapshot)
            }
        }
        val completedKey = draggedKey
        draggedKey = null
        draggedIndex = -1
        draggedOffset = Offset.Zero
        reorderSnapshot = null
        onReorderActiveChanged(false)
        coroutineScope.launch {
            withFrameNanos { }
            if (suppressClickKey == completedKey) {
                suppressClickKey = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            reorderSnapshot?.let(forumBoardViewModel::cancelBookmarkReorder)
            reorderSnapshot = null
            onReorderActiveChanged(false)
        }
    }

    Column (Modifier.fillMaxSize()) {

        if (UserManager.getUserList().size == 1) {
            Text(
                modifier = Modifier.padding(if (isMiuixComposition) 16.dp else 8.dp),
                text = "建议登录多个账号，可有效改善跳转系统浏览器问题",
                color = if (isMiuixComposition) miuixBoardHintColor() else Color.Unspecified,
            )
        }

        LazyVerticalGrid(
            columns = boardGridColumns(),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            itemsIndexed(
                items = bookmarks,
                key = { _, board -> bookmarkStableKey(board) },
            ) { index, child ->
                val stableKey = bookmarkStableKey(child)
                val latestIndex by rememberUpdatedState(index)
                val isDragging = draggedKey == stableKey
                val accessibilityActions = listOf(
                    CustomAccessibilityAction("上移") {
                        moveBookmarkWithPersistence(
                            forumBoardViewModel, latestIndex, latestIndex - 1
                        )
                    },
                    CustomAccessibilityAction("下移") {
                        moveBookmarkWithPersistence(
                            forumBoardViewModel, latestIndex, latestIndex + 1
                        )
                    },
                    CustomAccessibilityAction("置顶") {
                        moveBookmarkWithPersistence(forumBoardViewModel, latestIndex, 0)
                    },
                    CustomAccessibilityAction("置底") {
                        moveBookmarkWithPersistence(
                            forumBoardViewModel, latestIndex, bookmarks.lastIndex
                        )
                    },
                )

                ForumBoardGridItemView(
                    child = child,
                    forumBoardViewModel = forumBoardViewModel,
                    clickEnabled = draggedKey == null && suppressClickKey != stableKey,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = draggedOffset.x
                                translationY = draggedOffset.y
                                shadowElevation = 12.dp.toPx()
                                alpha = 0.92f
                            }
                        }
                        .semantics { customActions = accessibilityActions }
                        .pointerInput(stableKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    reorderSnapshot = forumBoardViewModel.beginBookmarkReorder()
                                    draggedKey = stableKey
                                    draggedIndex = latestIndex
                                    draggedOffset = Offset.Zero
                                    suppressClickKey = stableKey
                                    onReorderActiveChanged(true)
                                },
                                onDragCancel = {
                                    finishDrag(commit = false)
                                },
                                onDragEnd = {
                                    finishDrag(commit = true)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggedOffset += dragAmount

                                    val layoutInfo = gridState.layoutInfo
                                    val draggedItem = layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == stableKey }
                                        ?: return@detectDragGesturesAfterLongPress

                                    val draggedCenterX = draggedItem.offset.x +
                                        draggedItem.size.width / 2f + draggedOffset.x
                                    val draggedCenterY = draggedItem.offset.y +
                                        draggedItem.size.height / 2f + draggedOffset.y

                                    val scrollDelta = when {
                                        draggedCenterY < layoutInfo.viewportStartOffset + edgeSize ->
                                            -((layoutInfo.viewportStartOffset + edgeSize - draggedCenterY)
                                                .coerceAtMost(edgeSize))
                                        draggedCenterY > layoutInfo.viewportEndOffset - edgeSize ->
                                            (draggedCenterY - (layoutInfo.viewportEndOffset - edgeSize))
                                                .coerceAtMost(edgeSize)
                                        else -> 0f
                                    }
                                    if (scrollDelta != 0f) {
                                        val consumed = gridState.dispatchRawDelta(scrollDelta)
                                        draggedOffset += Offset(0f, consumed)
                                    }

                                    val target = layoutInfo.visibleItemsInfo
                                        .asSequence()
                                        .filter {
                                            it.index in forumBoardViewModel
                                                .bookmarkBoardsLiveData.value.orEmpty().indices
                                        }
                                        .firstOrNull {
                                            draggedCenterX >= it.offset.x &&
                                                draggedCenterX <= it.offset.x + it.size.width &&
                                                draggedCenterY >= it.offset.y &&
                                                draggedCenterY <= it.offset.y + it.size.height
                                        }
                                    val targetIndex = target?.index ?: return@detectDragGesturesAfterLongPress
                                    if (targetIndex != draggedIndex &&
                                        forumBoardViewModel.moveBookmark(draggedIndex, targetIndex)
                                    ) {
                                        draggedOffset += Offset(
                                            (draggedItem.offset.x - target.offset.x).toFloat(),
                                            (draggedItem.offset.y - target.offset.y).toFloat(),
                                        )
                                        draggedIndex = targetIndex
                                    }
                                },
                            )
                        },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun ForumBoardContent(
    index: Int,
    forumBoardViewModel: ForumBoardViewModel,
    onReorderActiveChanged: (Boolean) -> Unit = {},
) {
    val boardData = forumBoardViewModel.getBoardData(index)
    if (boardData.type == BoardEntity.BoardType.BOOKMARK) {
        ForumBoardBookmarkContent(
            bookmark = boardData,
            forumBoardViewModel = forumBoardViewModel,
            onReorderActiveChanged = onReorderActiveChanged,
        )
    } else {
        val revision = forumBoardViewModel.boardContentRevision
        val boardList = remember(boardData.id, revision) { boardRenderSnapshot(boardData) }
        LazyVerticalGrid(
            columns = boardGridColumns(),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            boardList.forEach { group ->
                val it = group.board
                if (it.type == BoardEntity.BoardType.GROUP) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ForumBoardGroupView(it)
                    }
                    group.children.let { data ->
                        items(data.size) { index ->
                            ForumBoardGridItemView(data[index], forumBoardViewModel)
                        }
                    }
                } else {
                    item {
                        ForumBoardGridItemView(it, forumBoardViewModel)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

private fun moveBookmarkWithPersistence(
    forumBoardViewModel: ForumBoardViewModel,
    from: Int,
    to: Int,
): Boolean {
    val snapshot = forumBoardViewModel.beginBookmarkReorder()
    if (to !in snapshot.indices || !forumBoardViewModel.moveBookmark(from, to)) {
        return false
    }
    forumBoardViewModel.commitBookmarkReorder(snapshot)
    return true
}

/**
 * Board grid columns: three on phones as before, more on wide windows (seven on a landscape
 * tablet) instead of stretching three tiles across the screen. Tiles stay at least 160dp wide.
 */
private fun boardGridColumns(): GridCells = object : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val minTile = 160.dp.roundToPx()
        val count = (availableSize / minTile).coerceIn(3, 8)
        val base = availableSize / count
        val remainder = availableSize % count
        return List(count) { base + if (it < remainder) 1 else 0 }
    }
}
