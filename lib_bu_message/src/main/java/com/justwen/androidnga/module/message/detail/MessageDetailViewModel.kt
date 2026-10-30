package com.justwen.androidnga.module.message.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.justwen.androidnga.core.data.MessageArticlePageInfo
import kotlinx.coroutines.flow.Flow

/**
 * Page state for one private-message thread.
 *
 * `mid`, the subject and the reply recipient arrive as navigation arguments instead of
 * process-wide mutable repository fields. Missing ones (a deep link or a future entry)
 * stay empty; the reply FAB remains usable, as it was before the first page parsed.
 */
class MessageDetailViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val mid: String = (savedStateHandle.get<Int>(KEY_MID) ?: 0).toString()

    val title: String = savedStateHandle.get<String>(KEY_SUBJECT).orEmpty()

    val recipient: String = savedStateHandle.get<String>(KEY_RECIPIENT).orEmpty()

    private val repository = MessageDetailRepository()

    val messageDetailData: Flow<PagingData<MessageArticlePageInfo>> =
        repository.messageDetailData(mid).cachedIn(viewModelScope)

    private companion object {
        const val KEY_MID = "mid"
        const val KEY_SUBJECT = "subject"
        const val KEY_RECIPIENT = "recipient"
    }
}
