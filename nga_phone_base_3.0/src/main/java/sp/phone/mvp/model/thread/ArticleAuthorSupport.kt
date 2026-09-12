package sp.phone.mvp.model.thread

/** Justwen ThreadParseUtils.kt, 2becba2a (GPL-3.0), shared with the retained normal parser. */
object ArticleAuthorSupport {
    @JvmStatic fun anonymousName(userName: String): String {
        val t1 = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥"
        val t2 = "王李张刘陈杨黄吴赵周徐孙马朱胡林郭何高罗郑梁谢宋唐许邓冯韩曹曾彭萧蔡潘田董袁于余叶蒋杜苏魏程吕丁沈任姚卢傅钟姜崔谭廖范汪陆金石戴贾韦夏邱方侯邹熊孟秦白江阎薛尹段雷黎史龙陶贺顾毛郝龚邵万钱严赖覃洪武莫孔汤向常温康施文牛樊葛邢安齐易乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿关兰焦俞左柳甘祝包宁尚符舒阮柯纪梅童凌毕单季裴霍涂成苗谷盛曲翁冉骆蓝路游辛靳管柴蒙鲍华喻祁蒲房滕屈饶解牟艾尤阳时穆农司卓古吉缪简车项连芦麦褚娄窦戚岑景党宫费卜冷晏席卫米柏宗瞿桂全佟应臧闵苟邬边卞姬师和仇栾隋商刁沙荣巫寇桑郎甄丛仲虞敖巩明佘池查麻苑迟邝 "
        if (!userName.startsWith("#anony_") || userName.length != 39) return "匿名用户"
        return try {
            buildString {
                var i = 6
                for (j in 0..5) {
                    if (j == 0 || j == 3) append(t1[userName.substring(i + 1, i + 2).toInt(16)])
                    else append(t2[userName.substring(i, i + 2).toInt(16)])
                    i += 2
                }
            }
        } catch (_: RuntimeException) { "匿名用户" }
    }

    @JvmStatic fun clientModel(client: String?): String? {
        if (client.isNullOrBlank()) return null
        return when (client.substringBefore(' ')) {
            "1", "7", "101" -> "ios"
            "9", "103" -> "wp"
            "8", "100" -> "android"
            else -> "unknown"
        }
    }
}
