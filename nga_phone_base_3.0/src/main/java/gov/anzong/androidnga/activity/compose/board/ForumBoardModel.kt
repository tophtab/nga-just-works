package gov.anzong.androidnga.activity.compose.board

import com.alibaba.fastjson.JSON
import gov.anzong.androidnga.activity.compose.board.data.ForumsListBean
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.utils.ThreadProvider
import gov.anzong.androidnga.common.PreferenceKey
import gov.anzong.androidnga.common.util.LogUtils
import gov.anzong.androidnga.core.board.data.Board
import gov.anzong.androidnga.core.board.data.BoardEntity

internal fun bookmarkStableKey(board: BoardEntity): String = "${board.fid}_${board.stid}"

internal object BookmarkOrder {

    fun move(items: MutableList<BoardEntity>, from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) {
            return false
        }
        val moved = items.removeAt(from)
        items.add(to, moved)
        return true
    }

    fun hasSameOrder(first: List<BoardEntity>, second: List<BoardEntity>): Boolean {
        return first.size == second.size && first.indices.all { index ->
            bookmarkStableKey(first[index]) == bookmarkStableKey(second[index])
        }
    }

    fun restoreIfCurrent(
        items: MutableList<BoardEntity>,
        expectedCurrent: List<BoardEntity>,
        snapshot: List<BoardEntity>,
    ): Boolean {
        if (!hasSameOrder(items, expectedCurrent)) {
            return false
        }
        items.clear()
        items.addAll(snapshot)
        return true
    }

}

internal enum class BookmarkPersistResult {
    SAVED,
    SUPERSEDED,
    FAILED,
}

internal enum class HomeBoardOrderPersistResult {
    SAVED,
    SUPERSEDED,
    FAILED,
}

class ForumBoardModel {

    private companion object {
        const val TAG = "ForumBoardModel"

        fun logError(message: String) {
            try {
                LogUtils.e(TAG, message)
            } catch (_: Throwable) {
                // Persistence semantics must not depend on the logger being
                // available (notably in local JVM tests).
            }
        }
    }

    private val boardList: MutableList<BoardEntity> = mutableListOf()

    private val boardMap: HashMap<String, BoardEntity> = HashMap()

    private val localBoardList: MutableList<BoardEntity>

    private val icons = BoardIconState {
        PreferenceUtils.getData(PreferenceKey.BOARD_ICON_URL, "")
    }

    val bookmarkBoard: BoardEntity

    init {
        val context = ContextUtils.getContext()
        bookmarkBoard = ForumBoardRepository.loadBookmarkBoardList(context)
        localBoardList = ForumBoardRepository.loadLocalBoardList(context)
        boardList.add(bookmarkBoard)
        val localBoardsById = localBoardList.associateBy { it.id }
        HomeBoardOrderStore.load(defaultHomeBoardIds()).forEach { id ->
            localBoardsById[id]?.let(boardList::add)
        }
        boardList.forEach {
            initBoardMap(it, null)
        }
        transferBookmarkBoards()
        hydrateIcons()
    }

    private fun initBoardMap(boardEntity: BoardEntity, parent: BoardEntity? = null) {
        with(boardEntity) {
            parentId = parent?.id
            id = generateBoardId(fid, stid, parentId) ?: id
        }
        boardMap[boardEntity.id] = boardEntity
        boardEntity.children?.let {
            it.forEach { data ->
                initBoardMap(data, boardEntity)
            }
        }
    }

    private fun transferBookmarkBoards() {
        val context = ContextUtils.getContext()
        if (ForumBoardRepository.bookmarkFileExists(context)) {
            // The JSON file is the single source of truth from this point on,
            // including when it intentionally contains an empty list.
            if (ForumBoardRepository.bookmarkFileIsReadable(context)) {
                clearLegacyBookmarkPreference()
            }
            return
        }
        val bookmarkJson = PreferenceUtils.getData(PreferenceKey.BOOKMARK_BOARD, "")
        if (bookmarkJson.isNullOrBlank()) {
            return
        }
        val bookmarks = try {
            require(bookmarkJson.trimStart().startsWith("[")) {
                "Legacy bookmark data is not a JSON array"
            }
            JSON.parseArray(bookmarkJson, Board::class.java)
                ?: throw IllegalArgumentException("Legacy bookmark data is null")
        } catch (error: Exception) {
            logError("Unable to parse legacy bookmark boards: ${error.message}")
            return
        }

        val migrated = bookmarks.mapNotNull { legacyBoard ->
            val id = generateBoardId(legacyBoard.fid, legacyBoard.stid) ?: return@mapNotNull null
            BoardEntity().apply {
                fid = legacyBoard.fid
                stid = legacyBoard.stid
                this.id = id
                name = legacyBoard.name.orEmpty()
                head = legacyBoard.boardHead
            }
        }.distinctBy { it.fid to it.stid }

        if (migrated.isEmpty()) {
            // "[]" is still an explicit legacy source. Materialize the empty
            // global file before clearing the preference so migration is both
            // one-shot and unambiguous on the next process start.
            if (persistBookmarkOrder(emptyList())) {
                clearLegacyBookmarkPreference()
            }
            return
        }

        val previous = bookmarkSnapshot()
        bookmarkBoard.children!!.addAll(migrated)
        if (persistBookmarkOrder(bookmarkSnapshot())) {
            clearLegacyBookmarkPreference()
        } else {
            restoreBookmarkOrder(previous)
        }
    }

    private fun clearLegacyBookmarkPreference() {
        PreferenceUtils.edit().remove(PreferenceKey.BOOKMARK_BOARD).apply()
    }

    @Synchronized
    fun bookmarkSnapshot(): List<BoardEntity> {
        return bookmarkBoard.children?.toList().orEmpty()
    }

    @Synchronized
    fun persistBookmarkOrder(order: List<BoardEntity> = bookmarkSnapshot()): Boolean {
        return try {
            ForumBoardRepository.writeBookmarkBoard(
                ContextUtils.getContext(), order.toList()
            )
            true
        } catch (error: Exception) {
            logError("Unable to persist bookmark boards: ${error.message}")
            false
        }
    }

    /**
     * Persist only if the candidate is still the in-memory order.  A previous
     * drag can finish on an IO thread after a newer add/remove operation; in
     * that case skipping the stale write is safer than overwriting the newer
     * global file.
     */
    @Synchronized
    internal fun persistBookmarkOrderIfCurrent(order: List<BoardEntity>): BookmarkPersistResult {
        if (!BookmarkOrder.hasSameOrder(bookmarkSnapshot(), order)) {
            return BookmarkPersistResult.SUPERSEDED
        }
        return if (persistBookmarkOrder(order)) {
            BookmarkPersistResult.SAVED
        } else {
            BookmarkPersistResult.FAILED
        }
    }

    @Synchronized
    fun reloadBookmarkBoard(): Boolean {
        val previous = bookmarkSnapshot()
        val loaded = ForumBoardRepository.loadBookmarkBoardList(ContextUtils.getContext())
        restoreBookmarkOrder(loaded.children?.toList().orEmpty())
        transferBookmarkBoards()
        hydrateIcons()
        return !BookmarkOrder.hasSameOrder(previous, bookmarkSnapshot())
    }

    @Synchronized
    fun loadBoardData(): List<BoardEntity> {
        return boardList.toList()
    }

    @Synchronized
    fun homeBoardOrderSnapshot(): List<String> {
        return boardList.drop(1).map { it.id }
    }

    @Synchronized
    fun moveHomeBoard(from: Int, to: Int): Boolean {
        val order = homeBoardOrderSnapshot().toMutableList()
        if (!HomeBoardOrderResolver.move(order, from, to)) {
            return false
        }
        applyHomeBoardOrder(order)
        return true
    }

    @Synchronized
    fun restoreHomeBoardOrder(snapshot: List<String>) {
        applyHomeBoardOrder(snapshot)
    }

    @Synchronized
    fun restoreHomeBoardOrderIfCurrent(
        expectedCurrent: List<String>,
        snapshot: List<String>,
    ): Boolean {
        val currentOrder = homeBoardOrderSnapshot().toMutableList()
        val restored = HomeBoardOrderResolver.restoreIfCurrent(
            currentOrder,
            expectedCurrent,
            snapshot,
        )
        if (!restored) {
            return false
        }
        applyHomeBoardOrder(currentOrder)
        return true
    }

    @Synchronized
    internal fun persistHomeBoardOrderIfCurrent(
        order: List<String>,
    ): HomeBoardOrderPersistResult {
        if (!HomeBoardOrderResolver.hasSameOrder(homeBoardOrderSnapshot(), order)) {
            return HomeBoardOrderPersistResult.SUPERSEDED
        }
        return try {
            if (HomeBoardOrderStore.save(defaultHomeBoardIds(), order)) {
                HomeBoardOrderPersistResult.SAVED
            } else {
                HomeBoardOrderPersistResult.FAILED
            }
        } catch (error: Exception) {
            logError("Unable to persist home board order: ${error.message}")
            HomeBoardOrderPersistResult.FAILED
        }
    }

    @Synchronized
    fun addBookmarkBoard(name: String, fid: Int, stid: Int, head: String? = null): Int {
        val id = generateBoardId(fid, stid) ?: return bookmarkBoard.children?.size ?: 0
        val boardEntity = BoardEntity().also {
            it.fid = fid
            it.stid = stid
            it.id = id
            it.name = name
            it.head = head
            icons.hydrate(listOf(it))
        }
        bookmarkBoard.children?.let {
            if (it.any { existing -> existing.fid == fid && existing.stid == stid }) {
                return it.size
            }
            val previous = it.toList()
            it.add(boardEntity)
            if (!persistBookmarkOrder(it.toList())) {
                restoreBookmarkOrder(previous)
            }
            return bookmarkBoard.children?.size ?: previous.size
        }
        return 0
    }

    @Synchronized
    fun removeBookmarkBoard(fid: Int, stid: Int): Int {
        bookmarkBoard.children?.let {
            val index = it.indexOfFirst { board -> board.fid == fid && board.stid == stid }
            if (index < 0) {
                return it.size
            }
            val previous = it.toList()
            it.removeAt(index)
            if (!persistBookmarkOrder(it.toList())) {
                restoreBookmarkOrder(previous)
            }
            return bookmarkBoard.children?.size ?: previous.size
        }
        return 0
    }

    @Synchronized
    fun removeAllBookmarkBoard(): Int? {
        return bookmarkBoard.children?.let {
            if (it.isEmpty()) {
                return 0
            }
            val previous = it.toList()
            it.clear()
            if (!persistBookmarkOrder(emptyList())) {
                restoreBookmarkOrder(previous)
            }
            return bookmarkBoard.children?.size ?: previous.size
        }
    }

    @Synchronized
    fun isBookmarkBoard(fid: Int, stid: Int): Boolean {
        bookmarkBoard.children?.let {
            it.forEach {
                if (it.fid == fid && it.stid == stid) {
                    return true
                }
            }
        }
        return false
    }

    private fun generateBoardId(fid: Int, stid: Int, parentId: String? = null): String? =
        boardId(fid, stid)

    @Synchronized
    fun moveBookmark(from: Int, to: Int): Boolean {
        return BookmarkOrder.move(bookmarkBoard.children!!, from, to)
    }

    @Synchronized
    fun restoreBookmarkOrder(snapshot: List<BoardEntity>) {
        icons.restoreBookmarks(bookmarkBoard, snapshot)
    }

    @Synchronized
    fun restoreBookmarkOrderIfCurrent(
        expectedCurrent: List<BoardEntity>,
        snapshot: List<BoardEntity>,
    ): Boolean {
        return icons.restoreBookmarks(bookmarkBoard, snapshot, expectedCurrent)
    }

    @Synchronized
    fun swapBookmark(from: Int, to: Int) {
        val previous = bookmarkSnapshot()
        if (!moveBookmark(from, to)) {
            return
        }
        if (!persistBookmarkOrder(bookmarkSnapshot())) {
            restoreBookmarkOrder(previous)
        }
    }

    @Synchronized
    internal fun needsCachedIconPrefix(): Boolean = icons.needsCachedPrefix

    @Synchronized
    internal fun applyCachedIconPrefix(prefix: String?) {
        if (icons.acceptCached(prefix)) {
            hydrateIcons()
            saveIconPrefix()
        }
    }

    @Synchronized
    internal fun applyRemoteBoardList(response: ForumsListBean): Boolean {
        // Rebuild against current membership; favorites can change during the request.
        boardMap.clear()
        boardList.forEach { initBoardMap(it, null) }
        return icons.applyRemote(
            response, localBoardList, bookmarkBoard, boardMap,
            savePrefix = ::saveIconPrefix,
            saveTree = ::saveData,
        )
    }

    private fun hydrateIcons() {
        icons.hydrate(localBoardList)
        icons.hydrate(listOf(bookmarkBoard))
    }

    private fun saveIconPrefix() {
        try {
            PreferenceUtils.putData(PreferenceKey.BOARD_ICON_URL, icons.prefix)
        } catch (_: Exception) {
            logError("Unable to persist board icon prefix")
        }
    }

    private fun saveData(snapshot: List<BoardEntity>) {
        ThreadProvider.runOnSingleThread {
            ForumBoardRepository.writeLocalBoardList(
                ContextUtils.getContext(), snapshot
            )
        }
    }

    @Synchronized
    fun getBoardName(fid: Int, stid: Int): String {
        val boardEntity = findBoard(fid, stid)
        return boardEntity?.name ?: ""
    }

    @Synchronized
    fun findBoard(fid: Int, stid: Int = 0): BoardEntity? {
        val id = generateBoardId(fid, stid)
        return boardMap[id]
    }

    private fun defaultHomeBoardIds(): List<String> = localBoardList.map { it.id }

    private fun applyHomeBoardOrder(order: List<String>) {
        val localBoardsById = localBoardList.associateBy { it.id }
        val resolved = HomeBoardOrderResolver.resolve(defaultHomeBoardIds(), order)
        boardList.subList(1, boardList.size).clear()
        resolved.forEach { id -> localBoardsById[id]?.let(boardList::add) }
    }

}
