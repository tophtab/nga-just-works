package com.justwen.androidnga.base.network

/**
 * Outcome of one network operation: either decoded [data] or a classified [error].
 *
 * The type is sealed so the two states are mutually exclusive; there is no
 * "both empty" or "both populated" instance. Use `when` at the call site:
 *
 * ```kotlin
 * when (val result = load()) {
 *     is ApiResult.Success -> render(result.data)
 *     is ApiResult.Failure -> showReason(result.error)
 * }
 * ```
 *
 * A failure is a *client* classification of the failure (see [ApiError]); it is
 * not a statement about NGA's server protocol.
 */
sealed interface ApiResult<out T> {

    /** The operation produced a usable payload. */
    data class Success<T>(val data: T) : ApiResult<T>

    /** The operation produced no usable payload; [error] explains why. */
    data class Failure(val error: ApiError) : ApiResult<Nothing>

    companion object {

        fun <T> success(data: T): ApiResult<T> = Success(data)

        fun failure(error: ApiError): ApiResult<Nothing> = Failure(error)

        /**
         * Classifies [cause] and wraps it as a failure.
         *
         * [message] replaces a blank [BusinessException] message, and is ignored
         * for every other cause.
         *
         * Cancellation is not turned into a failure here: `apiSafeCall` rethrows
         * [kotlin.coroutines.cancellation.CancellationException] before reaching
         * this factory.
         */
        fun failure(cause: Throwable, message: String? = null): ApiResult<Nothing> =
            Failure(cause.toApiError(message))
    }
}

/** `true` when this result is a [ApiResult.Success]. */
val <T> ApiResult<T>.isSuccess: Boolean
    get() = this is ApiResult.Success

/** `true` when this result is a [ApiResult.Failure]. */
val ApiResult<*>.isFailure: Boolean
    get() = this is ApiResult.Failure

/** The payload when this result [is][ApiResult.Success] a success, otherwise `null`. */
val <T> ApiResult<T>.dataOrNull: T?
    get() = (this as? ApiResult.Success)?.data

/** The classified error when this result [is][ApiResult.Failure] a failure, otherwise `null`. */
val ApiResult<*>.errorOrNull: ApiError?
    get() = (this as? ApiResult.Failure)?.error
