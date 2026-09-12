package sp.phone.mvp.model.thread

import io.reactivex.Observable
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** THREAD.PAGE / THREAD.PAGE.APP_COMPAT only. No shared interceptors, converters or Cookie lookup. */
class ArticleByteClient @JvmOverloads constructor(private val calls: Call.Factory = newClient()) {
    fun read(operation: ArticleOperation): Observable<String> = Observable.create { emitter ->
        try {
            val call = calls.newCall(request(operation))
            emitter.setCancellable(call::cancel)
            if (emitter.isDisposed) return@create
            call.execute().use { response ->
                ArticleErrors.classifyHttp(response.code)
                val raw = decode(response)
                if (!emitter.isDisposed) {
                    emitter.onNext(raw)
                    emitter.onComplete()
                }
            }
        } catch (error: Exception) {
            if (!emitter.isDisposed) emitter.onError(ArticleErrors.failure(error))
        }
    }

    companion object {
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
        private val hosts = setOf("bbs.nga.cn", "bbs.ngacn.cc", "nga.178.com", "nga.donews.com", "ngabbs.com")

        @JvmStatic fun newClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE).build()

        @JvmStatic fun validateOrigin(origin: String): String {
            val uri = try { URI(origin) } catch (_: Exception) { throw ArticleFailure(ArticleFailureKind.PROTOCOL) }
            if (uri.scheme != "https" || uri.host?.lowercase(Locale.ROOT) !in hosts ||
                uri.rawUserInfo != null || uri.port !in listOf(-1, 443) || uri.rawQuery != null ||
                uri.rawFragment != null || uri.rawPath !in listOf("", "/") || uri.rawAuthority?.contains('%') == true) {
                throw ArticleFailure(ArticleFailureKind.PROTOCOL)
            }
            return "https://${uri.host.lowercase(Locale.ROOT)}"
        }

        @JvmStatic fun request(operation: ArticleOperation): Request {
            val origin = validateOrigin(operation.origin)
            val query = operation.key.query
            if (!query.isValid() || operation.page <= 0) throw ArticleFailure(ArticleFailureKind.CONTENT)
            val builder = Request.Builder()
                .header("Cookie", operation.account.cookieHeader())
                .header("User-Agent", operation.userAgent)
                .header("X-User-Agent", "Nga_Official")
            if (operation.source == ArticleSource.APP_API) {
                // Justwen 2becba2a ArticleListModel.loadPageWithAppApi, GPL-3.0.
                val fields = FormBody.Builder().add("page", operation.page.toString())
                if (query.tid != 0) fields.add("tid", query.tid.toString())
                if (query.pid != 0) fields.add("pid", query.pid.toString())
                if (query.authorId != 0) fields.add("authorid", query.authorId.toString())
                builder.url("$origin/app_api.php?__lib=post&__act=list").post(fields.build())
            } else {
                val url = buildString {
                    append(origin).append("/read.php?&page=").append(operation.page).append("&__output=8&noprefix&v2")
                    if (query.tid != 0) append("&tid=").append(query.tid)
                    if (query.pid != 0) append("&pid=").append(query.pid)
                    if (query.authorId != 0) append("&authorid=").append(query.authorId)
                }
                builder.url(url).get()
            }
            return builder.build()
        }

        internal fun decode(response: Response): String {
            val body = response.body ?: throw ArticleFailure(ArticleFailureKind.EMPTY)
            val declared = body.contentLength()
            if (declared > MAX_BODY_BYTES) throw ArticleFailure(ArticleFailureKind.PROTOCOL)
            val bytes = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size().toLong() + count > MAX_BODY_BYTES) throw ArticleFailure(ArticleFailureKind.PROTOCOL)
                    bytes.write(buffer, 0, count)
                }
            }
            if (declared >= 0 && declared != bytes.size().toLong()) throw ArticleFailure(ArticleFailureKind.PROTOCOL)
            if (bytes.size() == 0) throw ArticleFailure(ArticleFailureKind.EMPTY)
            val charset = charset(response.header("Content-Type"))
            return try {
                charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            } catch (_: Exception) { throw ArticleFailure(ArticleFailureKind.PROTOCOL) }
        }

        internal fun charset(contentType: String?): Charset {
            if (contentType == null) return Charset.forName("GBK")
            val pieces = contentType.split(';').drop(1).map(String::trim)
            val declarations = pieces.filter { it.substringBefore('=').trim().equals("charset", true) }
            if (declarations.isEmpty()) {
                if (pieces.any { it.startsWith("charset", true) }) throw ArticleFailure(ArticleFailureKind.PROTOCOL)
                return Charset.forName("GBK")
            }
            if (declarations.size != 1 || '=' !in declarations[0]) throw ArticleFailure(ArticleFailureKind.PROTOCOL)
            val declared = declarations[0].substringAfter('=').trim()
            val name = if (declared.startsWith('"') && declared.endsWith('"') && declared.length > 2)
                declared.substring(1, declared.length - 1) else declared
            if (name.uppercase(Locale.ROOT) !in setOf("UTF-8", "UTF8", "GBK", "GB2312", "GB18030")) {
                throw ArticleFailure(ArticleFailureKind.PROTOCOL)
            }
            return Charset.forName(name)
        }
    }
}
