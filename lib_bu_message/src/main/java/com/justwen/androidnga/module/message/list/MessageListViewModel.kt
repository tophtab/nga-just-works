package com.justwen.androidnga.module.message.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Page state for the private-message list. Both appearances collect the same flow.
 *
 * Account switching asks for [refresh], which starts a new Pager (and therefore a new
 * request with the newly active account Cookie) instead of replaying cached rows.
 */
class MessageListViewModel : ViewModel() {

    private val repository = MessageListRepository()

    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messageListData: Flow<PagingData<MessageThreadPageInfo>> =
        refreshTrigger
            .flatMapLatest { repository.messageListData() }
            .cachedIn(viewModelScope)

    fun refresh() {
        refreshTrigger.value += 1
    }
}
