package com.justwen.androidnga.module.message.list

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.core.data.MessageListInfo
import com.justwen.androidnga.core.data.MessageThreadPageInfo
import com.justwen.androidnga.module.message.LegacyMessageJson
import gov.anzong.androidnga.base.util.StringUtils

/**
 * Pure `MESSAGE.LIST` parser: raw response text in, typed page out.
 *
 * The wire shape and the JS/comment repairs are the legacy factory behavior; the model
 * is still the existing `MessageListInfo`, so paging coordinates keep their meaning.
 */
internal object MessageListParser {

    private const val RELOGIN_MESSAGE = "请重新登录"

    fun parse(raw: String?): ApiResult<MessageListInfo> {
        if (raw.isNullOrEmpty()) {
            return ApiResult.failure(ApiError.Unexpected(IllegalStateException("empty response")))
        }
        val json = LegacyMessageJson.clean(raw)
        val root = try {
            JSON.parseObject(json)
        } catch (e: Exception) {
            return ApiResult.failure(e)
        } ?: return ApiResult.failure(ApiError.Unexpected(IllegalStateException("empty payload")))

        val data = root.get("data") as? JSONObject
            ?: return rejection(root.get("error") as? JSONObject)

        val page = data.get("0") as? JSONObject
            ?: return ApiResult.failure(BusinessException(message = RELOGIN_MESSAGE))

        val info = MessageListInfo()
        info.set__nextPage(page.getIntValue("nextPage"))
        info.set__currentPage(page.getIntValue("currentPage"))
        info.set__rowsPerPage(page.getIntValue("rowsPerPage"))
        info.setMessageEntryList(parseEntries(page))
        return ApiResult.success(info)
    }

    private fun rejection(error: JSONObject?): ApiResult<Nothing> {
        val message = error?.getString("0")?.takeIf { it.isNotEmpty() } ?: RELOGIN_MESSAGE
        return ApiResult.failure(BusinessException(message = message))
    }

    /**
     * Rows start at key `0` and continue while the next numeric key exists. This is the
     * old factory's indexing; `0` also holds the page coordinates.
     */
    private fun parseEntries(page: JSONObject): List<MessageThreadPageInfo> {
        val entries = ArrayList<MessageThreadPageInfo>()
        var row = page.get("0") as? JSONObject
        var index = 1
        while (row != null) {
            val entry = MessageThreadPageInfo()
            entry.mid = row.getIntValue("mid")
            entry.posts = row.getIntValue("posts")
            entry.subject = row.getString("subject")
            entry.from_username = row.getString("from_username")
            entry.last_from_username = row.getString("last_from_username")
            entry.time = formatTime(row.getIntValue("time"))
            entry.lastTime = formatTime(row.getIntValue("last_modify"))
            entries.add(entry)
            row = page.get(index.toString()) as? JSONObject
            index++
        }
        return entries
    }

    private fun formatTime(seconds: Int): String =
        if (seconds > 0) StringUtils.timeStamp2Date1(seconds.toString()) else ""
}
