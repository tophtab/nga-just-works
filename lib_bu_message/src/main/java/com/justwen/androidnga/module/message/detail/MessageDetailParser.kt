package com.justwen.androidnga.module.message.detail

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.justwen.androidnga.base.network.ApiError
import com.justwen.androidnga.base.network.ApiResult
import com.justwen.androidnga.base.network.BusinessException
import com.justwen.androidnga.core.data.MessageArticlePageInfo
import com.justwen.androidnga.core.data.MessageDetailInfo
import com.justwen.androidnga.module.message.LegacyMessageJson
import gov.anzong.androidnga.base.util.StringUtils
import gov.anzong.androidnga.common.util.NgaImageHost
import java.util.regex.Pattern

/**
 * Pure `MESSAGE.READ` parser: raw response text plus the requested page in, a typed page out.
 *
 * Every repair here is the retired factory's behavior: wrapper stripping, error-fill
 * truncation, non-standard numeric tokens, the invalid `__P` fragment, the relative
 * `[img]./mon_` attachment prefix, `[quote]`/`<br/>` flattening and link sectioning.
 */
internal object MessageDetailParser {

    private const val RELOGIN_MESSAGE = "请重新登录"
    private const val NETWORK_ERROR_MESSAGE = "网络错误"

    private const val INVALID_PAGE_START = "\"__P\":{\"aid\":"
    private const val INVALID_PAGE_END = "\"this_visit_rows\":"

    private val ImageRelativePrefix = Regex("\\[img\\]\\./mon_")
    private val Quote = Regex("\\[quote\\](.+?)\\[/quote\\]")
    private val UrlSplit = Pattern.compile("\\[url]")
    private val UrlCloseSplit = Pattern.compile("\\[/url]")
    private val SpaceSplit = Pattern.compile(" ")

    fun parse(raw: String?, page: Int): ApiResult<MessageDetailInfo> {
        if (raw == null) {
            return ApiResult.failure(BusinessException(message = NETWORK_ERROR_MESSAGE))
        }
        val json = prepare(raw)
        val root = try {
            JSON.parseObject(json)
        } catch (e: Exception) {
            return ApiResult.failure(e)
        } ?: return ApiResult.failure(ApiError.Unexpected(IllegalStateException("empty payload")))

        val data = root.get("data") as? JSONObject
            ?: return rejection(root.get("error") as? JSONObject)

        val pageObject = data.get("0") as? JSONObject
            ?: return ApiResult.failure(BusinessException(message = RELOGIN_MESSAGE))

        val info = MessageDetailInfo()
        info.setMessageEntryList(convertRows(pageObject, page))
        info.set__currentPage(pageObject.getIntValue("currentPage"))
        info.set__nextPage(pageObject.getIntValue("nextPage"))
        info.set_Alluser(parseAllUsers(pageObject.getString("allUsers")))
        info.set_Title(messageTitle(info))
        return ApiResult.success(info)
    }

    private fun prepare(raw: String): String {
        var json = LegacyMessageJson.stripWrapper(raw)
        json = LegacyMessageJson.truncateErrorFill(json)
        json = LegacyMessageJson.repairNumericTokens(json, includeAuthor = true)
        json = json.replace(ImageRelativePrefix, "[img]" + NgaImageHost.attachmentsPrefix() + "/mon_")
        return removeInvalidPageFragment(json)
    }

    /**
     * Some responses carry an invalid `__P` object before the real page data; the old
     * factory splices the fragment out and parses the remainder.
     */
    private fun removeInvalidPageFragment(json: String): String {
        val start = json.indexOf(INVALID_PAGE_START)
        val end = json.indexOf(INVALID_PAGE_END)
        return if (start != -1 && end > start) json.substring(0, start) + json.substring(end) else json
    }

    private fun rejection(error: JSONObject?): ApiResult<Nothing> {
        val message = error?.getString("0")?.takeIf { it.isNotEmpty() } ?: RELOGIN_MESSAGE
        return ApiResult.failure(BusinessException(message = message))
    }

    /** Message rows start at `allmsgs."0"` and run while the next numeric key exists. */
    private fun convertRows(pageObject: JSONObject, page: Int): List<MessageArticlePageInfo> {
        val rows = pageObject.get("allmsgs") as? JSONObject
        val userInfoMap = pageObject.get("userInfo") as? JSONObject
        val result = ArrayList<MessageArticlePageInfo>()
        var row = rows?.get("0") as? JSONObject
        var index = 1
        while (row != null) {
            val entry = MessageArticlePageInfo()
            entry.content = row.getString("content")
            entry.lou = 20 * (page - 1) + index
            entry.subject = row.getString("subject")
            entry.time = formatTime(row.getIntValue("time"))
            entry.from = row.getString("from")
            fillUserInfo(entry, userInfoMap)
            formatContent(entry)
            result.add(entry)
            row = rows?.get(index.toString()) as? JSONObject
            index++
        }
        return result
    }

    private fun fillUserInfo(row: MessageArticlePageInfo, userInfoMap: JSONObject?) {
        val userInfo = userInfoMap?.get(row.from) as? JSONObject ?: return
        row.author = userInfo.getString("username")
        row.js_escap_avatar = userInfo.getString("avatar")
        row.yz = userInfo.getString("yz")
        row.mute_time = userInfo.getString("mute_time")
        row.signature = userInfo.getString("signature")
    }

    private fun formatContent(row: MessageArticlePageInfo) {
        val content = row.content
            .replace(Quote) { "\n${it.groupValues[1]}\n" }
            .replace("<br/>", "\n")
        row.content = content

        val sections = row.contentSections
        for (section in UrlSplit.split(row.content)) {
            if (section.contains("[/url]")) {
                val urlSection = UrlCloseSplit.split(section)
                sections.add(android.util.Pair<String, Boolean>(urlSection[0], true))
                if (urlSection.size > 1) {
                    sections.add(android.util.Pair<String, Boolean>(urlSection[1], false))
                }
            } else {
                sections.add(android.util.Pair<String, Boolean>(section, false))
            }
        }
    }

    /**
     * `allUsers` alternates id/name; every odd slot is a participant name, joined by `,`.
     */
    private fun parseAllUsers(allUsers: String?): String {
        val normalized = allUsers.orEmpty().replace("\t", " ")
        val names = SpaceSplit.split(normalized)
        val joined = StringBuilder()
        var index = 1
        while (index < names.size) {
            joined.append(names[index]).append(',')
            index += 2
        }
        if (joined.isNotEmpty()) {
            joined.deleteCharAt(joined.length - 1)
        }
        return joined.toString()
    }

    private fun messageTitle(info: MessageDetailInfo): String {
        val first = info.messageEntryList?.firstOrNull() ?: return ""
        val title = first.subject
        return if (!StringUtils.isEmpty(title)) title else ""
    }

    private fun formatTime(seconds: Int): String =
        if (seconds > 0) StringUtils.timeStamp2Date1(seconds.toString()) else ""
}
