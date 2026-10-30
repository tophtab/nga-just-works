package com.justwen.androidnga.module.message.detail

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.base.network.apiSafeCall
import com.justwen.androidnga.core.data.MessageArticlePageInfo
import com.justwen.androidnga.module.message.MessageGetRequest
import com.justwen.androidnga.module.message.defaultMessageGetRequest
import kotlinx.coroutines.flow.Flow

/**
 * `MESSAGE.READ` repository. It owns the request and paging shape only; the page title and
 * recipient are navigation state now, so there is no mutable process-wide field here.
 */
class MessageDetailRepository(
    private val request: MessageGetRequest = defaultMessageGetRequest,
) {

    fun messageDetailData(mid: String): Flow<PagingData<MessageArticlePageInfo>> = Pager(
        config = PagingConfig(MessageDetailPagingSource.PAGE_SIZE),
        pagingSourceFactory = { MessageDetailPagingSource(mid, request) },
    ).flow
}

class MessageDetailPagingSource(
    private val mid: String,
    private val request: MessageGetRequest = defaultMessageGetRequest,
) : PagingSource<Int, MessageArticlePageInfo>() {

    override fun getRefreshKey(state: PagingState<Int, MessageArticlePageInfo>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageArticlePageInfo> {
        val page = params.key ?: 1
        val query = hashMapOf(
            "__lib" to "message",
            "__act" to "message",
            "act" to "read",
            "lite" to "js",
            "mid" to mid,
            "page" to page.toString(),
        )
        var nextKey: Int? = null

        val result = apiSafeCall(
            request = { request(query) },
            parse = { raw ->
                when (val parsed = MessageDetailParser.parse(raw, page)) {
                    is ApiResult.Success -> {
                        val entries = parsed.data.messageEntryList.orEmpty()
                        if (entries.isEmpty()) {
                            // An empty page is the same legacy paging error as a parse failure.
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

    companion object {
        const val PAGE_SIZE = 20

        private const val RELOGIN_MESSAGE = "请重新登录"
    }
}
