package com.justwen.androidnga.base.network

import com.alibaba.fastjson2.JSONException
import kotlin.coroutines.cancellation.CancellationException
import okio.Buffer
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Upper bound for a captured HTTP error body.
 *
 * Error bodies are kept in memory only, are truncated here, and must never be
 * logged or persisted verbatim (they can echo private content).
 */
private const val MAX_ERROR_BODY_LENGTH = 2048L

/**
 * Runs one network operation and classifies any failure.
 *
 * [request] performs the transport call and returns the raw scalar payload the
 * shared Retrofit stack already decodes; [parse] turns that payload into an
 * [ApiResult], so it may either return [ApiResult.failure] or throw (for example
 * [BusinessException]) for a site-level rejection.
 *
 * Coroutine cancellation is never converted into a failure: a
 * [CancellationException] is rethrown as-is, so cancelling a page load stays
 * cancellation instead of surfacing as a user-visible error.
 *
 * ```kotlin
 * apiSafeCall(
 *     request = { service.getString(paramMap) },
 *     parse = { body -> parser.parse(body) },
 * )
 * ```
 */
suspend fun <T : Any> apiSafeCall(
    request: suspend () -> String,
    parse: (String) -> ApiResult<T>,
): ApiResult<T> = try {
    parse(request())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.failure(e)
}

/**
 * Classifies [this] throwable as an [ApiError].
 *
 * Checked most-specific first, because [SocketTimeoutException] is an
 * [IOException]:
 *
 * 1. [BusinessException] -> [ApiError.Business]
 * 2. [SocketTimeoutException] -> [ApiError.Timeout]
 * 3. [HttpException] -> [ApiError.HttpStatus]
 * 4. [JSONException] -> [ApiError.JsonParse]
 * 5. [IOException] -> [ApiError.NetworkUnavailable]
 * 6. anything else -> [ApiError.Unexpected]
 *
 * Cancellation is intentionally absent from this mapping: `apiSafeCall` rethrows
 * it before any classification happens, so no failure path can swallow it.
 *
 * [message] only replaces a blank business message.
 */
internal fun Throwable.toApiError(message: String? = null): ApiError = when (this) {
    is BusinessException -> ApiError.Business(
        code = code,
        message = this.message.takeUnless { it.isBlank() } ?: message.orEmpty(),
        cause = this,
    )

    is SocketTimeoutException -> ApiError.Timeout(this)

    is HttpException -> ApiError.HttpStatus(
        code = code(),
        message = message(),
        errorBody = response()?.errorBody()?.readBounded(),
        cause = this,
    )

    is JSONException -> ApiError.JsonParse(this.message, this)

    is IOException -> ApiError.NetworkUnavailable(this)

    else -> ApiError.Unexpected(this)
}

/** Reads at most [MAX_ERROR_BODY_LENGTH] bytes; a body that cannot be read yields `null`. */
private fun okhttp3.ResponseBody.readBounded(): String? = try {
    val source = source()
    val buffer = Buffer()
    var remaining = MAX_ERROR_BODY_LENGTH
    while (remaining > 0) {
        val read = source.read(buffer, remaining)
        if (read == -1L) {
            break
        }
        remaining -= read
    }
    buffer.readUtf8()
} catch (e: Exception) {
    // Diagnostics must never turn a classified HTTP failure into a new throwable
    // (an already consumed or closed body reports IllegalStateException, not I/O).
    null
}
