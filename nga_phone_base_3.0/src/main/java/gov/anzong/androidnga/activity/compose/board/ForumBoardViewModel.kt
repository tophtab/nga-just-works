package gov.anzong.androidnga.activity.compose.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.arouter.ARouterConstants
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.util.ToastUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sp.phone.param.ParamKey
import sp.phone.util.ARouterUtils

object ForumBoardViewModel : ViewModel() {

    val boardLiveData: MutableLiveData<List<BoardEntity>> = MutableLiveData()

    val bookmarkSizeLiveData: MutableLiveData<Int> = MutableLiveData(0)

    val bookmarkBoardsLiveData: MutableLiveData<List<BoardEntity>> = MutableLiveData(emptyList())

    private val forumBoardModel = ForumBoardModel()

    const val BOARD_REMOTE_REQUEST_TIME_KEY = "board_remote_request_time"

    var boardContentRevision by mutableIntStateOf(0)
        private set

    private val refreshGate = BoardRefreshGate(
        readLastAttempt = { PreferenceUtils.getData(BOARD_REMOTE_REQUEST_TIME_KEY, 0L) },
        writeLastAttempt = { PreferenceUtils.putData(BOARD_REMOTE_REQUEST_TIME_KEY, it) },
    )

    init {
        boardLiveData.postValue(forumBoardModel.loadBoardData())
        bookmarkSizeLiveData.postValue(forumBoardModel.bookmarkBoard.children?.size)
        bookmarkBoardsLiveData.postValue(forumBoardModel.bookmarkSnapshot())
        if (forumBoardModel.needsCachedIconPrefix()) {
            viewModelScope.launch {
                val prefix = withContext(Dispatchers.IO) {
                    ForumBoardRepository.loadRemoteBoardList(ContextUtils.getContext())?.forum_icon_pre
                }
                forumBoardModel.applyCachedIconPrefix(prefix)
            }
        }
    }

    fun getBoardData(index: Int = 0): BoardEntity {
        return boardLiveData.value!![index]
    }

    fun addBookmarkBoard(name: String, fid: Int, stid: Int, head: String? = null) {
        bookmarkSizeLiveData.value = forumBoardModel.addBookmarkBoard(name, fid, stid,head)
        publishBookmarkBoards()
    }

    fun isBookmarkBoard(fid: Int, stid: Int): Boolean {
        return forumBoardModel.isBookmarkBoard(fid, stid)
    }

    fun getBoardName(fid: Int, stid: Int): String {
        return forumBoardModel.getBoardName(fid, stid)
    }

    fun findBoard(fid: Int, stid: Int = 0): BoardEntity? {
        return forumBoardModel.findBoard(fid, stid)
    }

    fun addBookmarkBoard(name: String, fid: String, stid: String) {
        try {
            if (name.isEmpty()) {
                addBookmarkBoard(name, fid.toInt(), stid.toInt())
            }
        } catch (e: NumberFormatException) {
            ToastUtils.show("请输入正确的版面名称、ID或者合集ID")
        }
    }

    fun removeBookmarkBoard(fid: Int, stid: Int) {
        bookmarkSizeLiveData.value = forumBoardModel.removeBookmarkBoard(fid, stid)
        publishBookmarkBoards()
    }

    fun removeAllBookmarkBoard() {
        forumBoardModel.removeAllBookmarkBoard()?.let {
            bookmarkSizeLiveData.value = it
            publishBookmarkBoards()
        }
    }

    fun beginBookmarkReorder(): List<BoardEntity> {
        return forumBoardModel.bookmarkSnapshot()
    }

    fun moveBookmark(from: Int, to: Int): Boolean {
        val moved = forumBoardModel.moveBookmark(from, to)
        if (moved) {
            publishBookmarkBoards()
        }
        return moved
    }

    fun cancelBookmarkReorder(snapshot: List<BoardEntity>) {
        forumBoardModel.restoreBookmarkOrder(snapshot)
        publishBookmarkBoards()
    }

    fun commitBookmarkReorder(snapshot: List<BoardEntity>) {
        // Capture the candidate before switching threads.  The model will
        // reject this write if another mutation has already changed the list.
        val candidate = forumBoardModel.bookmarkSnapshot()
        viewModelScope.launch(Dispatchers.IO) {
            val result = forumBoardModel.persistBookmarkOrderIfCurrent(candidate)
            withContext(Dispatchers.Main) {
                if (result == BookmarkPersistResult.FAILED) {
                    // Do not roll back a newer user action that happened while
                    // the disk write was in flight.
                    val restored = forumBoardModel.restoreBookmarkOrderIfCurrent(candidate, snapshot)
                    if (restored) {
                        publishBookmarkBoards()
                    }
                    ToastUtils.error(
                        if (restored) {
                            "收藏排序保存失败，已恢复原顺序"
                        } else {
                            "收藏排序保存失败，较新的操作未被覆盖"
                        }
                    )
                }
            }
        }
    }

    fun beginHomeBoardReorder(): List<String> {
        return forumBoardModel.homeBoardOrderSnapshot()
    }

    fun moveHomeBoard(from: Int, to: Int): Boolean {
        val moved = forumBoardModel.moveHomeBoard(from, to)
        if (moved) {
            publishBoardData()
        }
        return moved
    }

    fun cancelHomeBoardReorder(snapshot: List<String>) {
        forumBoardModel.restoreHomeBoardOrder(snapshot)
        publishBoardData()
    }

    fun commitHomeBoardReorder(snapshot: List<String>) {
        val candidate = forumBoardModel.homeBoardOrderSnapshot()
        viewModelScope.launch(Dispatchers.IO) {
            val result = forumBoardModel.persistHomeBoardOrderIfCurrent(candidate)
            withContext(Dispatchers.Main) {
                if (result == HomeBoardOrderPersistResult.FAILED) {
                    val restored = forumBoardModel.restoreHomeBoardOrderIfCurrent(
                        candidate,
                        snapshot,
                    )
                    if (restored) {
                        publishBoardData()
                    }
                    ToastUtils.error(
                        if (restored) {
                            "首页栏次排序保存失败，已恢复原顺序"
                        } else {
                            "首页栏次排序保存失败，较新的操作未被覆盖"
                        }
                    )
                }
            }
        }
    }

    /** Reload the shared global bookmark list after an external data change. */
    fun refreshBookmarkBoards() {
        forumBoardModel.reloadBookmarkBoard()
        bookmarkSizeLiveData.value = forumBoardModel.bookmarkSnapshot().size
        publishBookmarkBoards()
    }

    /** Alias kept concise for callers that only need to refresh the bookmarks. */
    fun refreshBookmarks() {
        refreshBookmarkBoards()
    }

    private fun publishBookmarkBoards() {
        bookmarkBoardsLiveData.value = forumBoardModel.bookmarkSnapshot()
    }

    private fun publishBoardData() {
        boardLiveData.value = forumBoardModel.loadBoardData()
    }

    fun showTopicList(board: BoardEntity) {
        val fid = board.fid
        val stid = board.stid
        ARouterUtils.build(ARouterConstants.ACTIVITY_TOPIC_LIST)
            .withInt(ParamKey.KEY_FID, fid)
            .withInt(ParamKey.KEY_STID, stid)
            .withString(ParamKey.BOARD_HEAD, board.head)
            .withString(ParamKey.KEY_TITLE, board.name)
            .navigation()
        // todo 先临时放在这里，后面再统一定时处理
        requestRemoteBoardList()
    }

    private fun requestRemoteBoardList() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            refreshGate.refresh(
                request = {
                    withContext(Dispatchers.IO) {
                        ForumBoardRepository.requestRemoteBoardList(ContextUtils.getContext())
                    }
                },
                apply = { response ->
                    if (forumBoardModel.applyRemoteBoardList(response)) {
                        boardContentRevision++
                    }
                },
            )
        }
    }
}
