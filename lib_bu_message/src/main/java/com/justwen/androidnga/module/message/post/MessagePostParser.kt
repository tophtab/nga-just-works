package com.justwen.androidnga.module.message.post

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.module.message.LegacyMessageJson

/**
 * Pure `MESSAGE.SEND` response parser.
 *
 * Success is still the server's legacy success text; anything else is a business
 * rejection with a readable reason instead of a bare throwable.
 */
internal object MessagePostParser {

    private const val GENERIC_FAILURE = "发送失败！"

    private val SUCCESS_TAGS = setOf("发送完毕 ...", " @提醒每24小时不能超过50个", "操作成功")

    fun parse(raw: String): ApiResult<String> {
        val json = LegacyMessageJson.clean(raw)
        val root = try {
            JSON.parseObject(json)
        } catch (e: Exception) {
            return ApiResult.failure(e)
        } ?: return ApiResult.failure(ApiError.Unexpected(IllegalStateException("empty payload")))

        val data = root.get("data") as? JSONObject
        if (data == null) {
            val message = (root.get("error") as? JSONObject)?.getString("0")
            return ApiResult.failure(
                BusinessException(message = message?.takeIf { it.isNotEmpty() } ?: GENERIC_FAILURE)
            )
        }

        val message = data.getString("0")
        return if (message != null && message in SUCCESS_TAGS) {
            ApiResult.success(message)
        } else {
            ApiResult.failure(BusinessException(message = GENERIC_FAILURE))
        }
    }
}
