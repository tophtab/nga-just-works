package com.justwen.androidnga.module.message.post

import com.justwen.androidnga.base.network.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.net.URLEncoder

/**
 * Guards the `MESSAGE.SEND` wire shape and the "no shared mutable request map" rule:
 * every send builds fresh query and form maps with the same fields as before.
 */
class MessagePostRepositoryTest {

    @Test
    fun requestMapsKeepLegacyFieldsAndAreFreshPerCall() = runBlocking {
        val queries = mutableListOf<Map<String, String>>()
        val fields = mutableListOf<Map<String, String>>()
        val repository = MessagePostRepository(
            postRequest = { query, form ->
                queries += query
                fields += form
                """{"data":{"0":"操作成功"}}"""
            },
        )

        val first = repository.send(
            MessagePostData(action = "new", mid = "0", recipient = "甲，乙", subject = "", content = "你好"),
        )
        val second = repository.send(
            MessagePostData(action = "reply", mid = "42", recipient = "甲", subject = "标题", content = "回复"),
        )

        assertEquals("操作成功", (first as ApiResult.Success<String>).data)
        assertEquals("操作成功", (second as ApiResult.Success<String>).data)

        assertEquals(
            mapOf(
                "__lib" to "message",
                "__act" to "message",
                "lite" to "js",
                "charset" to "gbk",
                "act" to "new",
            ),
            queries[0],
        )
        assertEquals(
            mapOf(
                "__lib" to "message",
                "__act" to "message",
                "lite" to "js",
                "charset" to "gbk",
                "act" to "new",
                "mid" to "0",
                "to" to URLEncoder.encode("甲,乙", "gbk"),
                "content" to URLEncoder.encode("你好", "gbk"),
            ),
            fields[0],
        )
        assertEquals("reply", queries[1]["act"])
        assertEquals("42", fields[1]["mid"])
        assertEquals(URLEncoder.encode("标题", "gbk"), fields[1]["subject"])
        assertNotSame(queries[0], queries[1])
        assertNotSame(fields[0], fields[1])
    }
}
