package sp.phone.mvp.model.thread

import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic responses and fake Calls only. No socket, account store or NGA request is used. */
class ArticleByteClientTest {
    private val account = ArticleAccount.from(true, "42", "synthetic-session")
    private val full = ArticleQuery(100001, 0, 0, 0)

    private fun operation(
        source: ArticleSource = ArticleSource.APP_API,
        query: ArticleQuery = full,
        origin: String = "https://ngabbs.com",
        account: ArticleAccount = this.account,
    ) = ArticleOperation(
        ArticleRequestKey(query, 3, source, 20, account.owner, 7),
        origin, account, "Synthetic browser UA",
    )

    @Test fun bothSourcesPreserveFullAuthorAndPidQueryFields() {
        val cases = listOf(
            QueryCase(full, mapOf("page" to "7", "tid" to "100001"),
                "&page=7&__output=8&noprefix&v2&tid=100001"),
            QueryCase(ArticleQuery(100001, 0, 42, 0),
                mapOf("page" to "7", "tid" to "100001", "authorid" to "42"),
                "&page=7&__output=8&noprefix&v2&tid=100001&authorid=42"),
            QueryCase(ArticleQuery(0, 500001, 42, 1),
                mapOf("page" to "7", "pid" to "500001", "authorid" to "42"),
                "&page=7&__output=8&noprefix&v2&pid=500001&authorid=42"),
        )
        for (case in cases) for (source in ArticleSource.entries) {
            val (observer, factory) = read(TestBody("synthetic".toByteArray()), operation(source, case.query))
            observer.assertResult("synthetic")
            val request = factory.calls.single().request()
            assertEquals("ngaPassportUid=42; ngaPassportCid=synthetic-session", request.header("Cookie"))
            assertEquals("Synthetic browser UA", request.header("User-Agent"))
            assertEquals("Nga_Official", request.header("X-User-Agent"))
            if (source == ArticleSource.APP_API) {
                assertEquals("POST", request.method)
                assertEquals("https://ngabbs.com/app_api.php?__lib=post&__act=list", request.url.toString())
                val form = request.body as FormBody
                assertEquals(case.form, (0 until form.size).associate { form.name(it) to form.value(it) })
            } else {
                assertEquals("GET", request.method)
                assertEquals("https://ngabbs.com/read.php?${case.normalQuery}", request.url.toString())
                assertEquals(null, request.body)
            }
        }
    }

    @Test fun fallbackAndAlignmentKeepTheSameSnapshotAndChangeOnlySourceOrPage() {
        val original = operation(ArticleSource.READ_PHP, ArticleQuery(100001, 500001, 42, 1))
        val fallback = original.forSource(ArticleSource.APP_API)
        val aligned = fallback.forPage(13)
        assertSame(original.account, aligned.account)
        assertSame(original.key, aligned.key)
        val requests = listOf(original, fallback, aligned).map { operation ->
            val (observer, factory) = read(TestBody("ok".toByteArray()), operation)
            observer.assertResult("ok")
            factory.calls.single().request()
        }
        assertEquals(1, requests.map { it.header("Cookie") }.distinct().size)
        assertTrue(requests.all { it.url.host == "ngabbs.com" })
        val alignedForm = requests.last().body as FormBody
        assertEquals(mapOf("page" to "13", "tid" to "100001", "pid" to "500001", "authorid" to "42"),
            (0 until alignedForm.size).associate { alignedForm.name(it) to alignedForm.value(it) })
    }

    @Test fun guestHasAnExplicitEmptyCookieWithoutGlobalAccountLookup() {
        val guest = ArticleAccount.from(false, null, null)
        val (observer, factory) = read(TestBody("guest".toByteArray()), operation(account = guest))
        observer.assertResult("guest")
        assertEquals("guest", guest.owner)
        assertEquals("", factory.calls.single().request().header("Cookie"))
    }

    @Test fun onlyExactProjectHttpsOriginsReachTheCallFactory() {
        for (host in listOf("bbs.nga.cn", "bbs.ngacn.cc", "nga.178.com", "nga.donews.com", "ngabbs.com")) {
            val (observer, factory) = read(TestBody("ok".toByteArray()), operation(origin = "https://$host:443/"))
            observer.assertResult("ok")
            assertEquals(host, factory.calls.single().request().url.host)
        }
        for (origin in listOf(
            "http://ngabbs.com", "https://ngabbs.com:8443", "https://ngabbs.com/read.php",
            "https://ngabbs.com?x=1", "https://ngabbs.com/#fragment", "https://user@ngabbs.com",
            "https://ngabbs.com.example.invalid", "https://ngabbs.com.", "https://ngabbs.com\"",
            "https://%6egabbs.com", "//ngabbs.com", "https://ngabbs.com\\@example.invalid",
        )) {
            val (observer, factory) = read(TestBody("must not be sent".toByteArray()), operation(origin = origin))
            failure(observer, ArticleFailureKind.PROTOCOL)
            assertTrue("Rejected origin reached a Call factory: $origin", factory.calls.isEmpty())
        }
    }

    @Test fun clientHasNoRedirectRetryCookieJarOrSharedInterceptors() {
        val client = ArticleByteClient.newClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }

    @Test fun declaredCharsetsAndTheGbkDefaultPreserveOriginalTextAndCloseBodies() {
        val text = "  [b]合成正文[/b]\n"
        for ((header, encoding) in listOf(
            "application/json; charset=UTF-8" to "UTF-8",
            "text/plain; Charset=\"utf8\"" to "UTF-8",
            "application/json; charset=GBK" to "GBK",
            "application/json; charset=GB2312" to "GB2312",
            "application/json; charset=GB18030" to "GB18030",
            "application/json" to "GBK",
            null to "GBK",
        )) {
            val body = TestBody(text.toByteArray(Charset.forName(encoding)))
            read(body, contentType = header).first.assertResult(text)
            assertTrue(body.closed)
        }
    }

    @Test fun invalidCharsetDeclarationsAndMalformedBytesFailWithoutEncodingGuesses() {
        for (header in listOf(
            "text/plain; charset=UTF-16", "text/plain; charset=ISO-8859-1",
            "text/plain; charset=", "text/plain; charset", "text/plain; charset UTF-8",
            "text/plain; charset=\"UTF-8", "text/plain; charset=UTF-8; charset=GBK",
        )) {
            val body = TestBody("synthetic".toByteArray())
            val (observer, factory) = read(body, contentType = header)
            failure(observer, ArticleFailureKind.PROTOCOL)
            assertEquals(1, factory.calls.size)
            assertTrue(body.closed)
        }
        for ((header, bytes) in listOf(
            "text/plain; charset=UTF-8" to byteArrayOf(0xc3.toByte(), 0x28),
            "text/plain; charset=GBK" to byteArrayOf(0x81.toByte()),
        )) {
            val body = TestBody(bytes)
            failure(read(body, contentType = header).first, ArticleFailureKind.PROTOCOL)
            assertTrue(body.closed)
        }
    }

    @Test fun declaredAndStreamedLimitsTruncationAndEmptyBodiesAreDistinctFailures() {
        assertEquals(4 * 1024 * 1024, ArticleByteClient.MAX_BODY_BYTES)
        val exact = TestBody(ByteArray(ArticleByteClient.MAX_BODY_BYTES) { 'a'.code.toByte() })
        read(exact).first.assertValue { it.length == ArticleByteClient.MAX_BODY_BYTES && it.all { char -> char == 'a' } }
            .assertComplete().assertNoErrors()
        assertTrue(exact.closed)

        val oversizedDeclaration = TestBody(byteArrayOf(1), ArticleByteClient.MAX_BODY_BYTES.toLong() + 1)
        failure(read(oversizedDeclaration).first, ArticleFailureKind.PROTOCOL)
        assertEquals(0L, oversizedDeclaration.bytesRead)
        assertTrue(oversizedDeclaration.closed)

        for (body in listOf(
            TestBody(ByteArray(ArticleByteClient.MAX_BODY_BYTES + 1) { 1 }, -1),
            TestBody("short".toByteArray(), 10),
            TestBody("longer".toByteArray(), 2),
        )) {
            failure(read(body).first, ArticleFailureKind.PROTOCOL)
            assertTrue(body.closed)
        }
        val empty = TestBody(byteArrayOf())
        failure(read(empty).first, ArticleFailureKind.EMPTY)
        assertTrue(empty.closed)
    }

    @Test fun httpStopsAreClassifiedBeforeParsingAndNeverCreateAnotherCall() {
        for ((status, kind) in mapOf(
            401 to ArticleFailureKind.AUTH, 403 to ArticleFailureKind.ACCESS,
            429 to ArticleFailureKind.RATE_LIMIT, 302 to ArticleFailureKind.ACCESS,
            503 to ArticleFailureKind.PROTOCOL,
        )) {
            val body = TestBody("unread synthetic body".toByteArray())
            val (observer, factory) = read(body, status = status)
            failure(observer, kind)
            assertFalse((observer.errors().single() as ArticleFailure).allowsAppFallback())
            assertEquals(1, factory.calls.size)
            assertEquals(0L, body.bytesRead)
            assertTrue(body.closed)
        }
    }

    @Test fun ioFailureIsSingleAttemptAndDoesNotExposeItsOriginalMessage() {
        val factory = RecordingFactory { throw IOException("synthetic-session and private synthetic body") }
        val observer = ArticleByteClient(factory).read(operation()).test()
        failure(observer, ArticleFailureKind.NETWORK)
        val error = observer.errors().single()
        assertEquals(1, factory.calls.size)
        assertFalse(error.toString().contains("synthetic-session"))
        assertEquals(null, error.cause)
        assertFalse(account.toString().contains("synthetic-session"))
        assertFalse(operation().toString().contains("synthetic-session"))
    }

    @Test fun disposalCancelsAnInFlightCallAndSuppressesItsLateFailure() {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val factory = RecordingFactory(onCancel = { released.countDown() }) {
            started.countDown()
            try {
                if (!released.await(5, TimeUnit.SECONDS)) throw IOException("Synthetic Call was not cancelled")
                throw IOException("Synthetic cancelled Call")
            } finally {
                exited.countDown()
            }
        }
        val observer = ArticleByteClient(factory).read(operation()).subscribeOn(Schedulers.io()).test()
        try {
            assertTrue("Synthetic Call did not start", started.await(5, TimeUnit.SECONDS))
            observer.dispose()
            assertTrue(factory.calls.single().isCanceled())
            assertTrue("Synthetic Call did not exit", exited.await(5, TimeUnit.SECONDS))
            observer.assertNoValues().assertNoErrors().assertNotComplete()
        } finally {
            observer.dispose()
            released.countDown()
        }
    }

    private fun failure(observer: TestObserver<String>, kind: ArticleFailureKind) {
        observer.assertNoValues().assertNotComplete().assertError { it is ArticleFailure && it.kind == kind }
    }

    private fun read(
        body: TestBody,
        requestOperation: ArticleOperation = operation(),
        status: Int = 200,
        contentType: String? = "application/json; charset=UTF-8",
    ): Pair<TestObserver<String>, RecordingFactory> {
        val factory = RecordingFactory { request ->
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("Synthetic response").body(body).apply {
                    if (contentType != null) header("Content-Type", contentType)
                    if (status == 302) header("Location", "https://example.invalid/redirect")
                }.build()
        }
        return ArticleByteClient(factory).read(requestOperation).test() to factory
    }

    private data class QueryCase(val query: ArticleQuery, val form: Map<String, String>, val normalQuery: String)

    private class RecordingFactory(
        private val onCancel: () -> Unit = {},
        private val response: (Request) -> Response,
    ) : Call.Factory {
        val calls = mutableListOf<FakeCall>()
        override fun newCall(request: Request): Call = FakeCall(request, onCancel, response).also { calls.add(it) }
    }

    private class FakeCall(
        private val request: Request,
        private val onCancel: () -> Unit,
        private val response: (Request) -> Response,
    ) : Call {
        private val executed = AtomicBoolean()
        private val cancelled = AtomicBoolean()
        override fun request() = request
        override fun execute(): Response {
            check(executed.compareAndSet(false, true))
            return response(request)
        }
        override fun enqueue(responseCallback: Callback) {
            try { responseCallback.onResponse(this, execute()) }
            catch (error: IOException) { responseCallback.onFailure(this, error) }
        }
        override fun cancel() { cancelled.set(true); onCancel() }
        override fun isExecuted() = executed.get()
        override fun isCanceled() = cancelled.get()
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = FakeCall(request, onCancel, response)
    }

    private class TestBody(bytes: ByteArray, private val declaredLength: Long = bytes.size.toLong()) : ResponseBody() {
        var closed = false
        var bytesRead = 0L
        private val source = object : ForwardingSource(Buffer().write(bytes)) {
            override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount).also {
                if (it > 0) bytesRead += it
            }
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = declaredLength
        override fun source(): BufferedSource = source
    }
}
