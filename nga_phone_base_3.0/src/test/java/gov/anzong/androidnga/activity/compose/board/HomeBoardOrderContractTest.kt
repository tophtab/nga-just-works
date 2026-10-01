package gov.anzong.androidnga.activity.compose.board

import com.alibaba.fastjson.JSON
import gov.anzong.androidnga.core.board.data.BoardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeBoardOrderContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    @Test
    fun bundledBoardListUsesTheNewCompleteDefaultOrder() {
        val source = File(
            projectRoot,
            "nga_phone_base_3.0/src/main/assets/board_list.json",
        ).readText()
        val boards = JSON.parseArray(source, BoardEntity::class.java)

        assertEquals(
            listOf("other", "games", "wow", "bliz", "club"),
            boards.map { it.id },
        )
        assertEquals(
            listOf("网事杂谈", "游戏专版", "魔兽世界", "暴雪游戏", "国家地理俱乐部"),
            boards.map { it.name },
        )
    }

    @Test
    fun localBoardCacheVersionIsBumpedForTheNewDefault() {
        val source = File(
            projectRoot,
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt",
        ).readText()

        assertTrue(source.contains("BOARD_LOCAL_VERSION_CURRENT = 6"))
    }

    @Test
    fun modelKeepsBookmarkFixedAndPersistsOnlyTheDisplayOverlay() {
        val source = File(
            projectRoot,
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt",
        ).readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(source.indexOf("boardList.add(bookmarkBoard)") < source.indexOf("HomeBoardOrderStore.load"))
        assertTrue(normalized.contains("return boardList.drop(1).map { it.id }"))
        assertTrue(normalized.contains("persistHomeBoardOrderIfCurrent"))
        assertTrue(normalized.contains("restoreHomeBoardOrderIfCurrent"))
    }

    @Test
    fun homeWiresOnlyNonBookmarkTabsIntoTheOrderTransaction() {
        val source = File(
            projectRoot,
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt",
        ).readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(normalized.contains("tabKeys = tabKeys"))
        assertTrue(
            normalized.contains(
                "reorderableTabRange = if (boards.size > 1) 1..boards.lastIndex else null"
            )
        )
        assertTrue(normalized.contains("if (tabKey != \"bookmark\")"))
        assertTrue(normalized.contains("forumBoardViewModel.moveHomeBoard(fromIndex - 1, toIndex - 1)"))
        assertTrue(normalized.contains("beginHomeBoardReorder()"))
        assertTrue(normalized.contains("commitHomeBoardReorder"))
        assertTrue(normalized.contains("cancelHomeBoardReorder"))
    }

    @Test
    fun favoriteAndTabReordersShareOnlyThePagerGate() {
        val source = File(
            projectRoot,
            "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt",
        ).readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(
            normalized.contains(
                "userScrollEnabled = pagerUserScrollEnabled && !favoriteReorderActive && !tabReorderActive"
            )
        )
        assertTrue(normalized.contains("onTabReorderActiveChanged = { tabReorderActive = it }"))
        assertTrue(normalized.contains("currentOnFavoriteReorderActiveChanged(active)"))
        assertFalse(normalized.contains("currentOnFavoriteReorderActiveChanged(tabReorderActive)"))
        assertTrue(
            normalized.contains(
                "homeOrderSnapshot?.let(forumBoardViewModel::cancelHomeBoardReorder)"
            )
        )
    }

    @Test
    fun liveDrawerContractNamesTheNewAdjacentBoard() {
        val source = File(
            projectRoot,
            ".trellis/spec/frontend/component-guidelines.md",
        ).readText()

        assertTrue(source.contains("moves from favorites to `网事杂谈`"))
        assertFalse(source.contains("moves from favorites to `魔兽世界`"))
    }
}
