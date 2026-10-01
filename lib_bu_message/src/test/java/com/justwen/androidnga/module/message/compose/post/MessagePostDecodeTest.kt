package com.justwen.androidnga.module.message.compose.post
import org.junit.Assert.*
import org.junit.Test
class MessagePostDecodeTest {
    @Test fun wrapperAndSuccessTagsRetainDataFirstSelection() {
        for (tag in listOf("发送完毕 ...", " @提醒每24小时不能超过50个", "操作成功")) {
            assertEquals(tag, MessagePostRepository.checkResult("window.script_muti_get_var_store={\"data\":{\"0\":\"$tag\"},\"error\":{\"0\":\"denied\"}}/*error fill content").getOrThrow())
        }
        assertEquals("denied", MessagePostRepository.checkResult("""{"error":{"0":"denied"}}""").exceptionOrNull()?.message)
        assertEquals("发送失败！", MessagePostRepository.checkResult("""{"data":{},"error":{"0":"denied"}}""").exceptionOrNull()?.message)
        assertEquals("发送失败！", MessagePostRepository.checkResult("{}").exceptionOrNull()?.message)
        assertThrows(RuntimeException::class.java) { MessagePostRepository.checkResult("{") }
    }
}
