package com.justwen.androidnga.module.message.post

import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.apiSafeCall
import com.justwen.androidnga.module.message.MessagePostRequest
import com.justwen.androidnga.module.message.defaultMessagePostRequest
import java.net.URLEncoder

/** One private-message submission snapshot. The ViewModel builds a fresh value per send. */
data class MessagePostData(
    val action: String,
    val mid: String,
    val recipient: String,
    val subject: String,
    val content: String,
)

/**
 * `MESSAGE.SEND` repository.
 *
 * Every call builds new query and form maps; the old shared mutable map is gone, so a
 * concurrent or repeated send can never inherit a previous call's `act` or fields.
 */
class MessagePostRepository(
    private val postRequest: MessagePostRequest = defaultMessagePostRequest,
) {

    suspend fun send(data: MessagePostData): ApiResult<String> = apiSafeCall(
        request = { postRequest(queryParams(data.action), fieldParams(data)) },
        parse = { raw -> MessagePostParser.parse(raw) },
    )

    private fun queryParams(action: String): Map<String, String> = hashMapOf(
        "__lib" to "message",
        "__act" to "message",
        "lite" to "js",
        "charset" to "gbk",
        "act" to action,
    )

    /**
     * The legacy body repeats the shared query fields and adds the message fields.
     * Recipient and text are GBK-encoded; full-width commas become half-width first.
     */
    private fun fieldParams(data: MessagePostData): Map<String, String> =
        HashMap(queryParams(data.action)).apply {
            put("mid", data.mid)
            put("to", encode(data.recipient.replace("，", ",")))
            if (data.subject.isNotEmpty()) {
                put("subject", encode(data.subject))
            }
            put("content", encode(data.content))
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "gbk")
}
