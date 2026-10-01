package gov.anzong.androidnga.activity.compose.board

import gov.anzong.androidnga.activity.compose.board.data.ForumsListBean
import gov.anzong.androidnga.core.board.data.BoardEntity

/** Owned by the model's synchronized boundary; no IO or account state. */
internal class BoardIconState(readPreference: () -> Any?) {
    private val savedPrefix = try {
        BoardIconUrlResolver.normalize(readPreference())
    } catch (_: Exception) { null }
    var prefix: String = savedPrefix ?: BoardIconUrlResolver.defaultPrefix
        private set
    private var remoteAccepted = false
    val needsCachedPrefix: Boolean get() = savedPrefix == null && !remoteAccepted

    fun acceptCached(value: Any?): Boolean = needsCachedPrefix && accept(value)

    fun acceptRemote(value: Any?): Boolean {
        val normalized = BoardIconUrlResolver.normalize(value) ?: return false
        // Even an unchanged valid remote value supersedes a delayed cache read.
        remoteAccepted = true
        return accept(normalized)
    }

    private fun accept(value: Any?): Boolean {
        val normalized = BoardIconUrlResolver.normalize(value) ?: return false
        if (normalized == prefix) return false
        prefix = normalized
        return true
    }

    fun applyRemote(
        response: ForumsListBean,
        roots: List<BoardEntity>,
        bookmark: BoardEntity,
        currentIndex: MutableMap<String, BoardEntity>,
        savePrefix: () -> Unit,
        saveTree: (List<BoardEntity>) -> Unit,
    ): Boolean {
        val prefixChanged = acceptRemote(response.forum_icon_pre)
        val added = mergeRemoteBoards(response, currentIndex)
        hydrate(roots)
        hydrate(listOf(bookmark))
        if (prefixChanged) savePrefix()
        if (added > 0) saveTree(snapshotBoardTree(roots))
        return added > 0
    }

    fun hydrate(boards: List<BoardEntity>) {
        boards.forEach {
            it.iconUrl = BoardIconUrlResolver.resolve(prefix, it.fid, it.stid)
            it.children?.let(::hydrate)
        }
    }

    fun restoreBookmarks(
        bookmark: BoardEntity,
        snapshot: List<BoardEntity>,
        expected: List<BoardEntity>? = null,
    ): Boolean {
        val children = bookmark.children ?: return false
        if (expected != null) {
            if (!BookmarkOrder.restoreIfCurrent(children, expected, snapshot)) return false
        } else {
            children.clear()
            children.addAll(snapshot)
        }
        hydrate(listOf(bookmark))
        return true
    }
}

/** Apply against the current index, including favorites, not the request-time tree. */
internal fun mergeRemoteBoards(
    response: ForumsListBean,
    boardMap: MutableMap<String, BoardEntity>,
): Int {
    var added = 0
    response.result?.forEach { category ->
        if (category.id in setOf("other", "wow", "company")) {
            category.groups?.forEach groupLoop@ { group ->
                val parent = boardMap[group.id] ?: return@groupLoop
                val children = parent.children ?: return@groupLoop
                group.forums?.forEach forumLoop@ { forum ->
                    val id = boardId(forum.id, forum.stid) ?: return@forumLoop
                    val name = forum.name?.takeIf { it.isNotBlank() } ?: return@forumLoop
                    if (!boardMap.containsKey(id)) {
                        val board = BoardEntity().apply {
                            this.id = id
                            fid = forum.id
                            stid = forum.stid
                            this.name = name
                            parentId = parent.id
                        }
                        children.add(board)
                        boardMap[id] = board
                        added++
                    }
                }
            }
        }
    }
    return added
}

internal fun boardId(fid: Int, stid: Int): String? = when {
    fid != 0 && stid != 0 -> "${fid}_${stid}"
    fid != 0 -> fid.toString()
    stid != 0 -> stid.toString()
    else -> null
}

/** Freeze the complete canonical hierarchy before handing it to the IO executor. */
internal fun snapshotBoardTree(roots: List<BoardEntity>): List<BoardEntity> = roots.map { source ->
    BoardEntity().apply {
        id = source.id
        name = source.name
        parentId = source.parentId
        type = source.type
        fid = source.fid
        stid = source.stid
        head = source.head
        children = source.children?.let { snapshotBoardTree(it).toMutableList() }
    }
}

internal data class BoardRenderGroup(val board: BoardEntity, val children: List<BoardEntity>)

/** Only list membership is copied; cells continue to observe the original entities. */
internal fun boardRenderSnapshot(root: BoardEntity): List<BoardRenderGroup> =
    root.children.orEmpty().map { BoardRenderGroup(it, it.children?.toList().orEmpty()) }
