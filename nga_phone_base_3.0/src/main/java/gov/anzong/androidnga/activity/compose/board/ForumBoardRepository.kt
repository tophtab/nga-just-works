package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper
import gov.anzong.androidnga.Utils
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel.BOARD_REMOTE_REQUEST_TIME_KEY
import gov.anzong.androidnga.activity.compose.board.data.ForumsListBean
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.utils.Files
import gov.anzong.androidnga.common.util.LogUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

object ForumBoardRepository {

    private const val BOARD_FILE_NAME = "board_list.json"

    private const val BOARD_BOOKMARK_FILE_NAME = "board_bookmark.json"

    /**
     * Writes are staged next to the real file and then renamed.  A process death
     * during JSON serialization therefore leaves either the previous complete
     * list or the new complete list, rather than a truncated document.
     */
    private const val BOARD_BOOKMARK_TEMP_FILE_NAME = "$BOARD_BOOKMARK_FILE_NAME.tmp"

    private const val BOARD_BOOKMARK_BACKUP_FILE_NAME = "$BOARD_BOOKMARK_FILE_NAME.bak"

    private const val BOARD_REMOTE_FILE_NAME = "board_list_remote.json"

    private const val FORUM_URL: String = "app_api.php?__lib=home&__act=category"

    private const val BOARD_LOCAL_VERSION_CURRENT = 6

    private const val BOARD_LOCAL_VERSION_KEY = "board_local_version"

    fun loadLocalBoardList(context: Context): MutableList<BoardEntity> {
        val boardJson: String
        val fileName = BOARD_FILE_NAME
        val dataFile = File(context.filesDir, fileName)

        checkLocalDataVersion(dataFile)

        boardJson = if (!dataFile.exists()) {
            Files.readAssetString(context, fileName)
        } else {
            Files.readFile(dataFile)
        }
        return JSON.parseArray(
            boardJson, BoardEntity::class.java
        )
    }

    private fun checkLocalDataVersion(file: File) {
        val currentVersion =
            PreferenceUtils.getData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        if (currentVersion != BOARD_LOCAL_VERSION_CURRENT) {
            PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
            PreferenceUtils.putData(BOARD_REMOTE_REQUEST_TIME_KEY, 0L)
            if (file.exists()) {
                Files.delete(file)
            }
        }
    }

    @Synchronized
    fun loadBookmarkBoardList(context: Context): BoardEntity {
        val bookmarkBoard = BoardEntity().apply {
            id = "bookmark"
            name = "收藏板块"
            type = BoardEntity.BoardType.BOOKMARK
            children = mutableListOf()
        }
        bookmarkBoard.children!!.addAll(readBookmarkBoards(context.filesDir))
        return bookmarkBoard
    }

    @Synchronized
    fun writeBookmarkBoard(context: Context, boardList: List<BoardEntity>) {
        writeBookmarkBoard(context.filesDir, boardList)
    }

    @Synchronized
    internal fun writeBookmarkBoard(directory: File, boardList: List<BoardEntity>) {
        val boardJson = encodeBookmarkBoards(boardList)
        val dataFile = bookmarkFile(directory)
        val tempFile = bookmarkTempFile(directory)

        // Remove a stale staging file left by a process that was killed before
        // the rename.  It is never treated as user data on the next launch.
        if (tempFile.exists()) {
            Files.delete(tempFile)
        }

        try {
            Files.writeFile(tempFile, boardJson)
            replaceBookmarkFile(directory, tempFile, dataFile)
        } catch (error: Throwable) {
            // Keep a complete staging file for launch-time recovery. An empty
            // or malformed staging file is ignored by readBookmarkBoards().
            throw error
        }
    }

    /** Returns whether the global bookmark source exists (used by legacy migration). */
    @Synchronized
    fun bookmarkFileExists(context: Context): Boolean {
        return bookmarkFileExists(context.filesDir)
    }

    @Synchronized
    internal fun bookmarkFileExists(directory: File): Boolean {
        return bookmarkFile(directory).exists() || bookmarkTempFile(directory).exists() ||
            bookmarkBackupFile(directory).exists()
    }

    /**
     * Distinguishes an intentional empty JSON list from a damaged source.  The
     * model uses this before removing the legacy preference so a corrupt file
     * never destroys the only still-readable migration source.
     */
    @Synchronized
    fun bookmarkFileIsReadable(context: Context): Boolean {
        return bookmarkFileIsReadable(context.filesDir)
    }

    @Synchronized
    internal fun bookmarkFileIsReadable(directory: File): Boolean {
        val candidates = listOf(
            bookmarkFile(directory),
            bookmarkTempFile(directory),
            bookmarkBackupFile(directory),
        ).filter(File::exists)
        return candidates.any { candidate ->
            try {
                decodeBookmarkBoards(Files.readFile(candidate))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    internal fun encodeBookmarkBoards(boardList: List<BoardEntity>): String {
        return JSON.toJSONString(boardList)
    }

    internal fun decodeBookmarkBoards(boardJson: String): MutableList<BoardEntity> {
        require(boardJson.isNotBlank()) { "Bookmark data is blank" }
        return (JSON.parseArray(boardJson, BoardEntity::class.java)
            ?: throw IllegalArgumentException("Bookmark data is not a JSON array"))
            .distinctBy { bookmarkStableKey(it) }
            .toMutableList()
    }

    @Synchronized
    internal fun readBookmarkBoards(directory: File): MutableList<BoardEntity> {
        val dataFile = bookmarkFile(directory)
        val tempFile = bookmarkTempFile(directory)
        val backupFile = bookmarkBackupFile(directory)
        val candidates = listOf(dataFile, tempFile, backupFile).filter(File::exists)
        if (candidates.isEmpty()) {
            return mutableListOf()
        }

        var firstFailure: Exception? = null
        candidates.forEach { candidate ->
            try {
                val boardJson = Files.readFile(candidate)
                val boards = decodeBookmarkBoards(boardJson)
                if (candidate != dataFile) {
                    recoverBookmarkFile(candidate, dataFile, boardJson)
                }
                if (dataFile.exists()) {
                    if (tempFile.exists()) Files.delete(tempFile)
                    if (backupFile.exists()) Files.delete(backupFile)
                }
                return boards
            } catch (error: Exception) {
                if (firstFailure == null) {
                    firstFailure = error
                }
            }
        }
        // A damaged cache must not prevent the app from starting.  Keep all
        // candidates in place so a later repair/export can still recover them;
        // bookmarkFileIsReadable() prevents legacy data from being discarded.
        firstFailure?.let {
            // Local JVM tests do not provide android.util.Log.  Diagnostics are
            // best-effort and must never turn a recoverable cache failure into
            // an app-start crash.
            try {
                LogUtils.e("ForumBoardRepository", "Unable to read bookmark data: ${it.message}")
            } catch (_: Throwable) {
                // Ignore logging failures; the evidence files remain intact.
            }
        }
        return mutableListOf()
    }

    private fun replaceBookmarkFile(directory: File, tempFile: File, dataFile: File) {
        if (tempFile.renameTo(dataFile)) {
            Files.delete(bookmarkBackupFile(directory))
            return
        }

        val backupFile = bookmarkBackupFile(directory)
        if (backupFile.exists()) {
            Files.delete(backupFile)
        }
        if (dataFile.exists() && !dataFile.renameTo(backupFile)) {
            throw IOException("Unable to stage previous bookmark data")
        }
        if (tempFile.renameTo(dataFile)) {
            Files.delete(backupFile)
            return
        }

        if (backupFile.exists()) {
            backupFile.renameTo(dataFile)
        }
        throw IOException("Unable to replace bookmark data")
    }

    private fun recoverBookmarkFile(source: File, dataFile: File, boardJson: String) {
        if (dataFile.exists()) {
            Files.delete(dataFile)
        }
        if (!source.renameTo(dataFile)) {
            Files.writeFile(dataFile, boardJson)
            Files.delete(source)
        }
    }

    private fun bookmarkFile(directory: File): File = File(directory, BOARD_BOOKMARK_FILE_NAME)

    private fun bookmarkTempFile(directory: File): File =
        File(directory, BOARD_BOOKMARK_TEMP_FILE_NAME)

    private fun bookmarkBackupFile(directory: File): File =
        File(directory, BOARD_BOOKMARK_BACKUP_FILE_NAME)


    fun writeLocalBoardList(context: Context, boardList: List<BoardEntity>) {
        PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        val boardJson = JSON.toJSONString(boardList)
        val fileName = BOARD_FILE_NAME
        val dataFile = File(context.filesDir, fileName)
        Files.writeFile(dataFile, boardJson)
    }

    internal fun decodeRemoteBoardList(raw: String): ForumsListBean? {
        return try {
            val envelope = JSON.parse(raw) as? JSONObject ?: return null
            val categories = envelope["result"] as? JSONArray ?: return null
            if (!hasValidRemoteMembers(categories)) return null
            // Validate the raw field before Fastjson can coerce numbers/objects to String.
            val prefix = BoardIconUrlResolver.normalize(envelope.remove("forum_icon_pre"))
            JSON.toJavaObject(envelope, ForumsListBean::class.java)?.apply {
                forum_icon_pre = prefix
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Fastjson permits null list members despite Kotlin's non-null element types. */
    private fun hasValidRemoteMembers(categories: JSONArray): Boolean {
        categories.forEach { categoryValue ->
            val category = categoryValue as? JSONObject ?: return false
            val groupsValue = category["groups"] ?: return@forEach
            val groups = groupsValue as? JSONArray ?: return false
            groups.forEach groupLoop@ { groupValue ->
                val group = groupValue as? JSONObject ?: return false
                val forumsValue = group["forums"] ?: return@groupLoop
                val forums = forumsValue as? JSONArray ?: return false
                if (forums.any { it !is JSONObject }) return false
            }
        }
        return true
    }

    suspend fun requestRemoteBoardList(context: Context): ForumsListBean? {
        return try {
            val url = Utils.getNGAHost() + FORUM_URL
            val result = RetrofitHelper.getInstance().serviceKt.getString(url)
            val bean = decodeRemoteBoardList(result)
            currentCoroutineContext().ensureActive()
            if (bean != null) {
                writeRemoteBoardList(context, result)
            }
            bean
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not log raw network/parser exception messages.
            null
        }
    }

    fun loadRemoteBoardList(context: Context): ForumsListBean? =
        loadRemoteBoardList(context.filesDir)

    internal fun loadRemoteBoardList(directory: File): ForumsListBean? {
        return try {
            val dataFile = File(directory, BOARD_REMOTE_FILE_NAME)
            if (!dataFile.exists()) null else decodeRemoteBoardList(Files.readFile(dataFile))
        } catch (_: Exception) {
            null
        }
    }

    private fun writeRemoteBoardList(context: Context, boardJson: String) {
        val fileName = BOARD_REMOTE_FILE_NAME
        val dataFile = File(context.filesDir, fileName)
        PreferenceUtils.putData(BOARD_LOCAL_VERSION_KEY, BOARD_LOCAL_VERSION_CURRENT)
        Files.writeFile(dataFile, boardJson)
    }

}
