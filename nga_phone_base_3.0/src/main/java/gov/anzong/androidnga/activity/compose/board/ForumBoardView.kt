package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.justwen.androidnga.ui.compose.widget.PagerInteractionState
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.base.util.ContextUtils
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
            ForumBoardContent(pageIndex, forumBoardViewModel) { active ->
                favoriteReorderActive = active
                currentOnFavoriteReorderActiveChanged(active)
            }
        }
    }
}

@Composable
fun ForumBoardGroupView(board: BoardEntity, context: Context = ContextUtils.getContext()) {
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(paddingValue)
            .fillMaxWidth()
            .clickable {
                if (clickEnabled) {
                    forumBoardViewModel.showTopicList(child)
                }
            }
    ) {
        Spacer(modifier = Modifier.height(paddingValue))
        val resId = getResId(child)
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
                .padding(top = paddingValue, bottom = paddingValue),
            color = Color(context.resources.getColor(R.color.text_color, null)),
            text = child.name
        )
        Spacer(modifier = Modifier.height(paddingValue))
    }
}

private fun getResId(board: BoardEntity): Int {
    if (board.stid != 0) {
        return 0
    }

    val fid = board.fid
    val resName = if (fid > 0) "p$fid" else "p_" + abs(fid)
    return ContextUtils.getResources()
        .getIdentifier(resName, "drawable", ContextUtils.getContext().packageName)
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
    val maxColumn = 3
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
            Text(modifier = Modifier.padding(8.dp), text = "建议登录多个账号，可有效改善跳转系统浏览器问题")
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(maxColumn),
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
            item(span = { GridItemSpan(maxColumn) }) {
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
        val maxColumn = 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(maxColumn),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            boardList.forEach { group ->
                val it = group.board
                if (it.type == BoardEntity.BoardType.GROUP) {
                    item(span = { GridItemSpan(maxColumn) }) {
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
            item(span = { GridItemSpan(maxColumn) }) {
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
