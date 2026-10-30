package com.justwen.androidnga.module.message.detail

import androidx.paging.PagingSource
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.core.data.MessageArticlePageInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** R9 coverage for the detail repository boundary. */
class MessageDetailRepositoryTest {

    private val golden = "{\"data\":{\"0\":{\"currentPage\":1,\"nextPage\":2,\"allUsers\":\"42 甲\"," +
        "\"userInfo\":{\"42\":{\"username\":\"甲\"}}," +
        "\"allmsgs\":{\"0\":{\"content\":\"第一条\",\"subject\":\"题\",\"time\":0,\"from\":\"42\"}," +
        "\"1\":{\"content\":\"第二条\",\"subject\":\"\",\"time\":0,\"from\":\"42\"}}}}}"

    @Test
    fun transportFailureBecomesLoadResultErrorWithTheSameCause() {
        val cause = IOException("offline")
        val source = MessageDetailPagingSource("42", request = { throw cause })

        val error = load(source, 1) as PagingSource.LoadResult.Error

        assertSame(cause, error.throwable)
    }

    @Test
    fun parsedBusinessFailureKeepsItsReason() {
        val source = MessageDetailPagingSource("42", request = { """{"error":{"0":"denied"}}""" })

        val error = load(source, 1) as PagingSource.LoadResult.Error

        assertTrue(error.throwable is BusinessException)
        assertEquals("denied", error.throwable.message)
    }

    @Test
    fun pageCarriesLegacyKeysAndRequestParameters() {
        var query: Map<String, String>? = null
        val source = MessageDetailPagingSource(
            "42",
            request = {
                query = it
                golden
            },
        )

        val page = load(source, 2) as PagingSource.LoadResult.Page
        val contents: List<String> = page.data.map { it.content }

        assertEquals(listOf("第一条", "第二条"), contents)
        assertEquals(1, page.prevKey)
        assertEquals(3, page.nextKey)
        assertEquals(
            mapOf(
                "__lib" to "message",
                "__act" to "message",
                "act" to "read",
                "lite" to "js",
                "mid" to "42",
                "page" to "2",
            ),
            query,
        )
    }

    private fun load(
        source: PagingSource<Int, MessageArticlePageInfo>,
        page: Int,
    ): PagingSource.LoadResult<Int, MessageArticlePageInfo> = runBlocking {
        source.load(
            PagingSource.LoadParams.Refresh(
                key = page,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )
    }
}
