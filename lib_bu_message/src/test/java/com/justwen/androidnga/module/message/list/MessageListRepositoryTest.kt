package com.justwen.androidnga.module.message.list

import androidx.paging.PagingSource
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Repository-boundary coverage required by the task's R9: a classified [ApiResult.Failure]
 * must reach paging as a [PagingSource.LoadResult.Error] that still carries the cause.
 *
 * The paging source takes its transport as a lambda, so these run on the JVM without the
 * Android-backed Retrofit singleton.
 */
class MessageListRepositoryTest {

    private val golden = "{\"data\":{\"0\":{\"nextPage\":3,\"currentPage\":2,\"rowsPerPage\":20," +
        "\"0\":{\"mid\":11,\"posts\":2,\"subject\":\"合成消息一\",\"from_username\":\"甲\",\"time\":0}," +
        "\"1\":{\"mid\":12,\"posts\":1,\"subject\":\"合成消息二\",\"from_username\":\"丙\",\"time\":0}}}}"

    @Test
    fun transportFailureBecomesLoadResultErrorWithTheSameCause() {
        val cause = IOException("offline")
        val source = MessageListPagingSource(request = { throw cause })

        val error = load(source, 1) as PagingSource.LoadResult.Error

        assertSame(cause, error.throwable)
    }

    @Test
    fun parsedBusinessFailureKeepsItsReason() {
        val source = MessageListPagingSource(
            request = { """{"error":{"0":"denied"}}""" },
        )

        val error = load(source, 1) as PagingSource.LoadResult.Error

        assertTrue(error.throwable is BusinessException)
        assertEquals("denied", error.throwable.message)
    }

    @Test
    fun pageCarriesLegacyKeysAndRequestParameters() {
        var query: Map<String, String>? = null
        val source = MessageListPagingSource(
            request = {
                query = it
                golden
            },
        )

        val page = load(source, 2) as PagingSource.LoadResult.Page
        val mids: List<Int> = page.data.map { it.mid }

        assertEquals(listOf(11, 12), mids)
        assertEquals(1, page.prevKey)
        assertEquals(3, page.nextKey)
        assertEquals(
            mapOf(
                "__lib" to "message",
                "__act" to "message",
                "act" to "list",
                "lite" to "js",
                "page" to "2",
            ),
            query,
        )
    }

    private fun load(
        source: PagingSource<Int, MessageThreadPageInfo>,
        page: Int,
    ): PagingSource.LoadResult<Int, MessageThreadPageInfo> = runBlocking {
        source.load(
            PagingSource.LoadParams.Refresh(
                key = page,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )
    }
}
