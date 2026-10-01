package sp.phone.mvp.model.convert
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import org.junit.Test
import sp.phone.mvp.model.thread.ArticleRowRenderer
import sp.phone.mvp.model.thread.ArticleBlacklist
class B4ReviewerProbeTest {
 @Test fun compare() {
 val method = ArticleConvertFactory::class.java.getDeclaredMethod("buildThreadRowList", JSONObject::class.java, ArticleRowRenderer::class.java, ArticleBlacklist::class.java, Boolean::class.javaPrimitiveType)
 method.isAccessible = true
 val renderer = ArticleRowRenderer { _, _ -> }
 val blacklist = ArticleBlacklist { false }
 val cases = listOf(
 "vote" to """{"vote":"canonical","Vote":"alias"}""",
 "bad-alias" to """{"pid":"bad","Pid":9}""",
 "attachments-alias" to """{"Attachs":{"0":{"attachurl":"x"}}}""",
 "comments-bean" to """{"comments":[{"content":"nested"}]}""",
 "hotReplies-bean" to """{"hotReplies":["1","2"]}""",
 "blacklist-bean" to """{"isInBlackList":true}"""
 )
 for ((name,row) in cases) for (strict in listOf(false,true)) {
 val raw = """{"data":{"__ROWS":1,"__R__ROWS":1,"__R":{"0":$row}}}"""
 val data = JSON.parseObject(raw).getJSONObject("data")
 val old = try { JSON.toJSONString(method.invoke(null,data,renderer,blacklist,strict)) } catch (e:Exception) { "null" }
 val new = if(strict) ArticleConvertFactory.getScopedArticleInfo(raw,renderer,blacklist) else ArticleConvertFactory.getArticleInfo(raw,renderer,blacklist)
 println("PROBE $name:$strict OLD=$old NEW=${JSON.toJSONString(new?.rowList)}")
 }
 }
}
