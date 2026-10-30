package com.justwen.androidnga.module.message.list

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.base.network.apiSafeCall
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.module.message.MessageGetRequest
import com.justwen.androidnga.module.message.defaultMessageGetRequest
import kotlinx.coroutines.flow.Flow

/**
 * `MESSAGE.LIST` repository: builds the request and maps the parser result to paging.
 *
 * The transport is injectable so the paging source can be exercised on the JVM without
 * the Android-backed Retrofit singleton.
 */
class MessageListRepository(
    private val request: MessageGetRequest = defaultMessageGetRequest,
) {

    fun messageListData(): Flow<PagingData<MessageThreadPageInfo>> = Pager(
        config = PagingConfig(PAGE_SIZE),
        pagingSourceFactory = { MessageListPagingSource(request) },
    ).flow

    companion object {
        const val PAGE_SIZE = 20
    }
}

class MessageListPagingSource(
    private val request: MessageGetRequest = defaultMessageGetRequest,
) : PagingSource<Int, MessageThreadPageInfo>() {

    override fun getRefreshKey(state: PagingState<Int, MessageThreadPageInfo>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageThreadPageInfo> {
        val page = params.key ?: 1
        val query = hashMapOf(
            "__lib" to "message",
            "__act" to "message",
            "act" to "list",
            "lite" to "js",
            "page" to page.toString(),
        )
        var nextKey: Int? = null

        val result = apiSafeCall(
            request = { request(query) },
            parse = { raw ->
                when (val parsed = MessageListParser.parse(raw)) {
                    is ApiResult.Success -> {
                        val entries = parsed.data.messageEntryList.orEmpty()
                        if (entries.isEmpty()) {
                            // Empty and unparsable lists stay the same legacy paging error.
                            ApiResult.failure(BusinessException(message = RELOGIN_MESSAGE))
                        } else {
                            nextKey = if (parsed.data.get__nextPage() > 0) page + 1 else null
                            ApiResult.success(entries)
                        }
                    }
                    is ApiResult.Failure -> parsed
                }
            },
        )

        return when (result) {
            is ApiResult.Success -> LoadResult.Page(result.data, previousKey(page), nextKey)
            is ApiResult.Failure -> LoadResult.Error(result.error.cause)
        }
    }

    private fun previousKey(page: Int): Int? = if (page > 1) page - 1 else null

    private companion object {
        const val RELOGIN_MESSAGE = "请重新登录"
    }
}
