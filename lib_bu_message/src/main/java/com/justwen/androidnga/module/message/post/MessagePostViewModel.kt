package com.justwen.androidnga.module.message.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot outcome of a send attempt; the Activity renders it as a toast. */
sealed interface MessagePostEvent {

    data class Success(val message: String) : MessagePostEvent

    data class Failure(val message: String) : MessagePostEvent
}

/**
 * Form state for composing a private message.
 *
 * `mid`/`action`/`to`/`title` come from the navigation arguments through
 * [SavedStateHandle]; the draft text and the in-flight flag live here too. Validation,
 * duplicate-send suppression and result delivery are unchanged from the old model.
 */
class MessagePostViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val action: String = savedStateHandle.get<String>(KEY_ACTION) ?: ACTION_NEW

    private val mid: String = (savedStateHandle.get<Int>(KEY_MID) ?: 0).toString()

    val isNewPostMode: Boolean = action == ACTION_NEW

    var recipient by mutableStateOf(savedStateHandle.get<String>(KEY_TO).orEmpty())
        private set

    var subject by mutableStateOf(savedStateHandle.get<String>(KEY_TITLE).orEmpty())
        private set

    var content by mutableStateOf("")
        private set

    private var isRunning = false

    private val repository = MessagePostRepository()

    private val resultChannel = Channel<MessagePostEvent>(Channel.BUFFERED)

    val events: Flow<MessagePostEvent> = resultChannel.receiveAsFlow()

    fun onRecipientChange(value: String) {
        recipient = value
    }

    fun onSubjectChange(value: String) {
        subject = value
    }

    fun onContentChange(value: String) {
        content = value
    }

    fun send() {
        if (isNewPostMode && recipient.isEmpty()) {
            emitFailure(EMPTY_RECIPIENT)
            return
        } else if (content.length <= 5) {
            emitFailure(CONTENT_TOO_SHORT)
            return
        }

        if (isRunning) {
            emitFailure(ALREADY_SENDING)
            return
        }

        val data = MessagePostData(
            action = action,
            mid = mid,
            recipient = recipient,
            subject = subject,
            content = content,
        )
        isRunning = true
        viewModelScope.launch {
            val event = repository.send(data).toEvent()
            isRunning = false
            resultChannel.send(event)
        }
    }

    private fun emitFailure(message: String) {
        resultChannel.trySend(MessagePostEvent.Failure(message))
    }

    private fun ApiResult<String>.toEvent(): MessagePostEvent = when (this) {
        is ApiResult.Success -> MessagePostEvent.Success(data)
        is ApiResult.Failure -> MessagePostEvent.Failure(error.readableMessage())
    }

    private fun ApiError.readableMessage(): String = when (this) {
        is ApiError.Business -> message
        is ApiError.Timeout, is ApiError.NetworkUnavailable -> NETWORK_ERROR
        else -> GENERIC_FAILURE
    }

    private companion object {
        const val KEY_ACTION = "action"
        const val KEY_MID = "mid"
        const val KEY_TO = "to"
        const val KEY_TITLE = "title"

        const val ACTION_NEW = "new"

        const val EMPTY_RECIPIENT = "请输入收件人"
        const val CONTENT_TOO_SHORT = "请输入内容或者内容字数少于6"
        const val ALREADY_SENDING = "发送中，请勿重复点击"
        const val NETWORK_ERROR = "网络错误"
        const val GENERIC_FAILURE = "发送失败！"
    }
}
