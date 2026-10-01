package gov.anzong.androidnga.activity.compose.board

import gov.anzong.androidnga.core.board.data.BoardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForumBoardBookmarkPersistenceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun moveBookmarkPreservesRelativeOrder() {
        val boards = listOf("a", "b", "c", "d").map(::board).toMutableList()

        assertTrue(BookmarkOrder.move(boards, from = 0, to = 2))

        assertEquals(listOf("b", "c", "a", "d"), boards.map { it.id })
    }

    @Test
    fun invalidMoveDoesNotMutateOrder() {
        val boards = listOf("a", "b").map(::board).toMutableList()
        val before = boards.map { it.id }

        assertFalse(BookmarkOrder.move(boards, from = -1, to = 1))
        assertFalse(BookmarkOrder.move(boards, from = 0, to = 2))
        assertEquals(before, boards.map { it.id })
    }

    @Test
    fun stableKeyUsesFidAndStidInsteadOfHistoricalId() {
        val first = board("first")
        val sameBoard = board("other-id").apply {
            fid = first.fid
            stid = first.stid
        }
        val sameHistoricalIdButDifferentBoard = board("first").apply {
            fid += 1
        }

        assertEquals(bookmarkStableKey(first), bookmarkStableKey(sameBoard))
        assertFalse(bookmarkStableKey(first) == bookmarkStableKey(sameHistoricalIdButDifferentBoard))
    }

    @Test
    fun failedOlderWriteCannotRestoreOverNewerMutation() {
        val boards = listOf("a", "b").map(::board).toMutableList()
        val candidate = listOf("b", "a").map(::board)
        BookmarkOrder.move(boards, from = 0, to = 1)

        // A newer action adds a board while the older write is in flight.
        boards.add(board("c"))
        assertFalse(BookmarkOrder.restoreIfCurrent(boards, candidate, listOf("a", "b").map(::board)))
        assertEquals(listOf("b", "a", "c"), boards.map { it.id })

        assertTrue(BookmarkOrder.restoreIfCurrent(boards, boards.toList(), listOf("x").map(::board)))
        assertEquals(listOf("x"), boards.map { it.id })
    }

    @Test
    fun onlyJsonArrayIsAnAuthoritativeEmptyList() {
        assertTrue(ForumBoardRepository.decodeBookmarkBoards("[]").isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            ForumBoardRepository.decodeBookmarkBoards("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ForumBoardRepository.decodeBookmarkBoards("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ForumBoardRepository.decodeBookmarkBoards("null")
        }
    }

    @Test
    fun duplicateStableKeysKeepTheirFirstPosition() {
        val first = board("first")
        val duplicate = board("duplicate").apply {
            fid = first.fid
            stid = first.stid
        }
        val last = board("last")

        val restored = ForumBoardRepository.decodeBookmarkBoards(
            ForumBoardRepository.encodeBookmarkBoards(listOf(first, duplicate, last))
        )

        assertEquals(listOf("first", "last"), restored.map { it.id })
    }

    @Test
    fun fileRoundTripRestoresOrderAfterReload() {
        val directory = temporaryFolder.newFolder("bookmarks")
        val source = listOf("first", "second", "third").map(::board)

        ForumBoardRepository.writeBookmarkBoard(directory, source)
        val restored = ForumBoardRepository.readBookmarkBoards(directory)

        assertEquals(source.map { it.id }, restored.map { it.id })
    }

    @Test
    fun corruptFileFallsBackToEmptyWithoutDeletingEvidence() {
        val directory = temporaryFolder.newFolder("corrupt-bookmarks")
        val dataFile = directory.resolve("board_bookmark.json")
        dataFile.writeText("   ")

        val restored = ForumBoardRepository.readBookmarkBoards(directory)

        assertTrue(restored.isEmpty())
        assertTrue(dataFile.exists())
        assertFalse(ForumBoardRepository.bookmarkFileIsReadable(directory))
    }

    @Test
    fun validBackupRecoversWhenPrimaryIsCorrupt() {
        val directory = temporaryFolder.newFolder("backup-bookmarks")
        val source = listOf("kept", "in", "order").map(::board)
        ForumBoardRepository.writeBookmarkBoard(directory, source)
        val dataFile = directory.resolve("board_bookmark.json")
        val backupFile = directory.resolve("board_bookmark.json.bak")
        check(dataFile.renameTo(backupFile))
        dataFile.writeText("broken")

        val restored = ForumBoardRepository.readBookmarkBoards(directory)

        assertEquals(source.map { it.id }, restored.map { it.id })
        assertTrue(dataFile.exists())
    }

    private fun board(id: String): BoardEntity {
        return BoardEntity().apply {
            this.id = id
            name = id
            fid = id.hashCode().takeIf { it != 0 } ?: 1
            stid = id.reversed().hashCode().takeIf { it != 0 } ?: 1
        }
    }
}
