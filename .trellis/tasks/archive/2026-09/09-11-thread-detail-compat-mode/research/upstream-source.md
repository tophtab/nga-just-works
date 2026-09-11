# Pinned upstream code for selective reuse

Source: Justwen/NGA-CLIENT-VER-OPEN-SOURCE at `2becba2acc3f6c85340424cd09bb03fa7d759db0`. These are source excerpts, not response fixtures or verified server contracts. Preserve upstream attribution and the project GPL license when adapting them.

The reviewed design controls behavior. Do not copy unchecked first-row access, truncated attachment prefixes, ignored errors/pagination, or unbound account/cache behavior from these excerpts.

## lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt

[Source](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/2becba2acc3f6c85340424cd09bb03fa7d759db0/lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt)

```kotlin
package com.client.androidnga.core.data.bean

import gov.anzong.androidnga.common.base.JavaBean

data class ThreadAppBean(
    @JvmField
    var attachPrefix: String?,
    @JvmField
    var code: Int?,
    @JvmField
    var currentPage: Int?,
    @JvmField
    var fid: Int,
    @JvmField
    var forum_bit: Int?,
    @JvmField
    var forum_name: String?,
    @JvmField
    var hot_post: String?,
    @JvmField
    var html_head_extra: String?,
    @JvmField
    var is_forum_admin: Int,
    @JvmField
    var msg: String = "",
    @JvmField
    var perPage: Int,
    @JvmField
    var result: List<Result> = mutableListOf(),
    @JvmField
    var tauthor: String?,
    @JvmField
    var tauthorid: Int = 0,
    @JvmField
    var tmisc_bit1: Int = 0,
    @JvmField
    var totalPage: Int = 0,
    @JvmField
    var tsubject: String?,
    @JvmField
    var vrows: Int = 0
) : JavaBean {
    class Author(
        @JvmField
        var __initialized__: Boolean = false,
        @JvmField
        var avatar: String? = null,
        @JvmField
        var bit_data: Int = 0,
        @JvmField
        var buffs: Map<String, String>? = null,
        @JvmField
        var conferred_title: String? = null,
        @JvmField
        var credit: Int = 0,
        @JvmField
        var gender: Int = 0,
        @JvmField
        var groupid: Int = 0,
        @JvmField
        var honor: String? = null,
        @JvmField
        var medal: List<String>? = null,
        @JvmField
        var member: String? = null,
        @JvmField
        var memberid: Int = 0,
        @JvmField
        var money: Int = 0,
        @JvmField
        var mute_status: Int = 0,
        @JvmField
        var mute_time: Int = 0,
        @JvmField
        var nickname: String? = null,
        @JvmField
        var postnum: Int = 0,
        @JvmField
        var regdate: Int = 0,
        @JvmField
        var reputation: String? = null,
        @JvmField
        var rvrc: String? = null,
        @JvmField
        var signature: String? = null,
        @JvmField
        var site: String? = null,
        @JvmField
        var thisvisit: Int = 0,
        @JvmField
        var uid: Int = 0,
        @JvmField
        var username: String? = null,
        @JvmField
        var annoy: String? = null,
        @JvmField
        var yz: Int = 0,
    ) : JavaBean

    data class Result(
        @JvmField
        var alterinfo: String?,
        @JvmField
        var attches: String?,
        @JvmField
        var author: Author? = null,
        @JvmField
        var comment_to_id: String?,
        @JvmField
        var content: String = "",
        @JvmField
        var fid: Int = 0,
        @JvmField
        var follow: Int = 0,
        @JvmField
        var from_client: String = "",
        @JvmField
        var isTieTiao: Boolean = false,
        @JvmField
        var is_user_quote: Int = 0,
        @JvmField
        var lou: Int = 0,
        @JvmField
        var pid: Int = 0,
        @JvmField
        var postdate: String?,
        @JvmField
        var postdatetimestamp: Int = 0,
        @JvmField
        var subject: String?,
        @JvmField
        var tid: Int = 0,
        @JvmField
        var type: Int = 0,
        @JvmField
        var vote: String?,
        @JvmField
        var vote_bad: Int = 0,
        @JvmField
        var vote_good: Int = 0,
    ) : JavaBean
}
```

## lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt

[Source](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/2becba2acc3f6c85340424cd09bb03fa7d759db0/lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt)

```kotlin
package com.client.androidnga.core.parse

import com.alibaba.fastjson2.JSON
import com.client.androidnga.core.data.bean.ThreadAppBean
import com.client.androidnga.core.data.model.ThreadBasicInfo
import com.client.androidnga.core.data.model.ThreadInfo
import com.client.androidnga.core.data.model.ThreadPageInfo
import com.client.androidnga.core.data.model.ThreadPostInfo
import gov.anzong.androidnga.core.HtmlConvertFactory
import gov.anzong.androidnga.core.IHtmlConfigService

class ThreadInfoAppParse {

    companion object {
        @JvmStatic
        fun parse(rawData: String, config: IHtmlConfigService? = null): ThreadInfo? {
            return ThreadInfoAppParse().parseThreadInfo(rawData, config = config)
        }
    }

    private fun parseThreadInfo(
        jsStr: String,
        config: IHtmlConfigService?
    ): ThreadInfo? {
        val threadBean = JSON.parseObject(jsStr, ThreadAppBean::class.java) ?: return null
        val threadInfo = ThreadInfo().apply {
            rawData = jsStr
            basicInfo = parseBasicInfo(threadBean)
            totalRows = threadBean.vrows
            pageInfo = parsePageInfo(threadBean)
            postInfoList = parsePostInfoList(threadBean, basicInfo, config)

        }
        return threadInfo
    }

    private fun parsePostInfoList(
        threadBean: ThreadAppBean,
        basicInfo: ThreadBasicInfo?,
        iHtmlConfigService: IHtmlConfigService?,
    ): List<ThreadPostInfo> {
        val postInfoList = mutableListOf<ThreadPostInfo>()
        for (i in 0 until threadBean.result.size) {
            val result = threadBean.result[i]
            val postInfo = ThreadPostInfo().apply {
                pid = result.pid
                tid = result.tid
                fid = result.fid
                lou = result.lou
                alterInfo = result.alterinfo
                vote = result.vote
                postDate = result.postdate
                rawContent = result.content.ifEmpty {
                    result.subject
                }
                clientModel = ThreadParseUtils.parseClientModel(result.from_client)
            }
            parseUserInfo(postInfo, result, iHtmlConfigService)
            postInfo.isThreadAuthor = postInfo.authorId == threadBean.tauthorid
            postInfo.formatHtml =
                HtmlConvertFactory.convert(postInfo, basicInfo, iHtmlConfigService)

            postInfoList.add(postInfo)
        }
        return postInfoList
    }

    fun parseUserInfo(
        postInfo: ThreadPostInfo,
        userBean: ThreadAppBean.Result,
        iHtmlConfigService: IHtmlConfigService?,
    ) {

        userBean.author?.let {
            postInfo.authorId = it.uid
            postInfo.postCount = it.postnum.toString()
            postInfo.isNuked = it.yz == -1
            postInfo.isMuted = it.buffs?.containsKey("105") ?: false
            postInfo.author = it.username
            postInfo.avatarUrl = it.avatar
            postInfo.isAnonymous = it.annoy?.startsWith("#anony_") == true
            postInfo.isBlocked =
                iHtmlConfigService?.isBlocked(postInfo.authorId.toString()) ?: false
            postInfo.signature = it.signature
            postInfo.memberGroup = it.member
            postInfo.reputation = it.rvrc?.toFloatOrNull()?.div(10.0f) ?: 0f
        }
    }

    private fun parsePageInfo(threadBean: ThreadAppBean): ThreadPageInfo {
        val pageInfo = ThreadPageInfo().apply {
            authorid = threadBean.tauthorid
            subject = threadBean.tsubject
            author = threadBean.tauthor
            fid = threadBean.fid
            tid = threadBean.result[0].tid
        }
        return pageInfo
    }

    private fun parseBasicInfo(threadBean: ThreadAppBean): ThreadBasicInfo {
        val basicInfo = ThreadBasicInfo().apply {
            attachHost =
                threadBean.attachPrefix?.split("/".toRegex())?.dropLastWhile { it.isEmpty() }
                    ?.toTypedArray()[0]
        }
        return basicInfo
    }
}
```

## lib_core/src/main/java/com/client/androidnga/core/parse/ThreadParseUtils.kt

[Source](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/2becba2acc3f6c85340424cd09bb03fa7d759db0/lib_core/src/main/java/com/client/androidnga/core/parse/ThreadParseUtils.kt)

```kotlin
package com.client.androidnga.core.parse

import com.client.androidnga.core.data.model.ClientModel

object ThreadParseUtils {

    fun parseAnonymousName(userName: String): String {
        val builder = StringBuilder()
        val t1 = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥"
        val t2 =
            "王李张刘陈杨黄吴赵周徐孙马朱胡林郭何高罗郑梁谢宋唐许邓冯韩曹曾彭萧蔡潘田董袁于余叶蒋杜苏魏程吕丁沈任姚卢傅钟姜崔谭廖范汪陆金石戴贾韦夏邱方侯邹熊孟秦白江阎薛尹段雷黎史龙陶贺顾毛郝龚邵万钱严赖覃洪武莫孔汤向常温康施文牛樊葛邢安齐易乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿关兰焦俞左柳甘祝包宁尚符舒阮柯纪梅童凌毕单季裴霍涂成苗谷盛曲翁冉骆蓝路游辛靳管柴蒙鲍华喻祁蒲房滕屈饶解牟艾尤阳时穆农司卓古吉缪简车项连芦麦褚娄窦戚岑景党宫费卜冷晏席卫米柏宗瞿桂全佟应臧闵苟邬边卞姬师和仇栾隋商刁沙荣巫寇桑郎甄丛仲虞敖巩明佘池查麻苑迟邝 "
        var i = 6
        for (j in 0..5) {
            var pos: Int
            if (j == 0 || j == 3) {
                pos = userName.substring(i + 1, i + 2).toInt(16)
                builder.append(t1[pos])
            } else {
                pos = userName.substring(i, i + 2).toInt(16)
                builder.append(t2[pos])
            }
            i += 2
        }
        return builder.toString()
    }

    fun parseClientModel(client: String?): ClientModel? {
        return client?.let {
            if (it.trim().isEmpty()) {
                return null
            }
            val clientAppCode = if (it.contains(" ")) {
                it.substring(0, it.indexOf(' '))
            } else {
                it
            }
            when (clientAppCode) {
                "1", "7" -> {
                    return ClientModel.IOS
                }

                "101" -> {
                    return ClientModel.IOS_BROWSER
                }

                "8" -> {
                    return ClientModel.ANDROID
                }

                "9", "103" -> {
                    return ClientModel.WP
                }

                "100" -> {
                    return ClientModel.ANDROID_BROWSER
                }

                else -> {
                    return ClientModel.UNKNOWN_BROWSER
                }
            }
        }
    }

}
```
