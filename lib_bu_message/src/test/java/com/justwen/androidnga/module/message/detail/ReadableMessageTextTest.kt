package com.justwen.androidnga.module.message.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableMessageTextTest {

    @Test
    fun stripsBbCodeLeftInSystemMessages() {
        val raw = "你因在主题 [url=/read.php?tid=1][b]库勒调谐棱柱哪里刷快[/b] 中的 [url=/read.php?pid=2][b]回复[/b]"
        assertEquals("你因在主题 库勒调谐棱柱哪里刷快 中的 回复", readableMessageText(raw))
    }

    @Test
    fun collapsesRunsOfBlankLines() {
        assertEquals("你完成了任务\n\n每日签到得N币", readableMessageText("你完成了任务\n\n\n\n\n每日签到得N币"))
    }

    @Test
    fun linkSectionsShowTheirLabel() {
        assertEquals("库勒调谐棱柱哪里刷快", readableLinkLabel("=/read.php?tid=1][b]库勒调谐棱柱哪里刷快[/b]"))
        assertEquals("https://bbs.nga.cn/x", readableLinkLabel("https://bbs.nga.cn/x"))
    }

    @Test
    fun keepsOrdinaryTextAndBrackets() {
        assertEquals("[篮球] 今天 [1] 楼", readableMessageText("[篮球] 今天 [1] 楼"))
    }
}
