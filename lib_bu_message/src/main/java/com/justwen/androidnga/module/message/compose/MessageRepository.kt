package com.justwen.androidnga.module.message.compose

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.base.network.apiSafeCall
import kotlinx.coroutines.flow.Flow
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper
import com.justwen.androidnga.base.network.retrofit.RetrofitServiceKt
import com.justwen.androidnga.module.message.MessageConvertFactory

object MessageRepository {

    private const val PAGE_SIZE = 20

    fun getMessageListData(): Flow<PagingData<MessageThreadPageInfo>> {
        return Pager(
            config = PagingConfig(PAGE_SIZE),
            pagingSourceFactory = { MessagePagingSource() }).flow
    }

}

class MessagePagingSource : PagingSource<Int, MessageThreadPageInfo>() {

    private val paramMap: HashMap<String, String> =
        hashMapOf("__lib" to "message", "__act" to "message", "act" to "list", "lite" to "js")

    override fun getRefreshKey(state: PagingState<Int, MessageThreadPageInfo>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageThreadPageInfo> {
        val page = params.key ?: 1
        val preKey = if (page > 1) page - 1 else null
        paramMap["page"] = page.toString()
        val factory = MessageConvertFactory()
        var nextKey: Int? = null

        val result = apiSafeCall(
            request = {
                val netService =
                    RetrofitHelper.getInstance().getService(RetrofitServiceKt::class.java) as RetrofitServiceKt
                netService.getString(paramMap)
            },
            parse = { jsonString ->
                val info = factory.getMessageListInfo(jsonString)
                val entries = info?.messageEntryList.orEmpty()
                if (info == null || entries.isEmpty()) {
                    // Empty and unparsable lists are the same legacy paging error; the
                    // factory's own message is the readable reason when it produced one.
                    ApiResult.failure(BusinessException(message = factory.errorMsg.ifEmpty { "请重新登录" }))
                } else {
                    nextKey = if (info.__nextPage > 0) page + 1 else null
                    ApiResult.success(entries)
                }
            },
        )

        return when (result) {
            is ApiResult.Success -> LoadResult.Page(result.data, preKey, nextKey)
            is ApiResult.Failure -> LoadResult.Error(result.error.cause)
        }
    }

}
