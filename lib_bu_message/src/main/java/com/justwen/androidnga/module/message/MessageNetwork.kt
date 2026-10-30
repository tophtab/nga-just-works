package com.justwen.androidnga.module.message

import com.justwen.androidnga.base.network.retrofit.RetrofitHelper
import com.justwen.androidnga.base.network.retrofit.RetrofitServiceKt

/** One private-message read: the query map in, the raw GBK-decoded body out. */
typealias MessageGetRequest = suspend (Map<String, String>) -> String

/** One private-message send: query map and form map in, the raw body out. */
typealias MessagePostRequest = suspend (Map<String, String>, Map<String, String>) -> String

/**
 * Default transport for the message repositories.
 *
 * It is a lambda rather than a direct service call so the paging sources stay
 * constructible (and testable) without the Android-backed `RetrofitHelper` singleton.
 */
internal val defaultMessageGetRequest: MessageGetRequest = { params ->
    messageService().getString(params)
}

internal val defaultMessagePostRequest: MessagePostRequest = { query, fields ->
    messageService().post(query, fields)
}

private fun messageService(): RetrofitServiceKt =
    RetrofitHelper.getInstance().getService(RetrofitServiceKt::class.java) as RetrofitServiceKt
