package com.justwen.androidnga.base.network

/**
 * Client-side classification of a failed network operation.
 *
 * This is **not** a server protocol: NGA keeps answering with its legacy wrapper
 * and site messages. [ApiError] only records *why the client could not treat a
 * response as a usable payload*, so callers can show a readable reason, decide
 * whether retrying is safe, and keep the original [cause] for diagnostics.
 *
 * Every subtype keeps the original throwable in [cause]; the error object never
 * carries account, Cookie, request body or raw response content.
 */
sealed interface ApiError {

    /** The original throwable that produced this classification. */
    val cause: Throwable

    /** Transport reached the network stack but no response was obtained (I/O, DNS, disconnect). */
    data class NetworkUnavailable(override val cause: Throwable) : ApiError

    /** The request timed out before a response was obtained. Safe to retry for reads only. */
    data class Timeout(override val cause: Throwable) : ApiError

    /**
     * The server answered with a non-success HTTP status.
     *
     * [errorBody] is captured in memory only, is length-bounded, and must not be
     * logged or persisted verbatim.
     */
    data class HttpStatus(
        val code: Int,
        val message: String?,
        val errorBody: String?,
        override val cause: Throwable,
    ) : ApiError {

        /**
         * Renders the classification without [errorBody], so an accidental log of
         * this object cannot echo response content.
         */
        override fun toString(): String =
            "HttpStatus(code=$code, message=$message, " +
                "errorBodyLength=${errorBody?.length}, cause=${cause.javaClass.name})"
    }

    /** The payload could not be decoded as the JSON this operation expects. */
    data class JsonParse(val message: String?, override val cause: Throwable) : ApiError

    /**
     * HTTP and framing succeeded but the server rejected the operation, or the
     * payload carried no usable data.
     *
     * [code] is the optional server-side business code when one was parsed.
     */
    data class Business(
        val code: String?,
        val message: String,
        override val cause: Throwable,
    ) : ApiError

    /** Anything the client did not anticipate. Treat as a defect until proven otherwise. */
    data class Unexpected(override val cause: Throwable) : ApiError
}

/**
 * Signals "the response arrived, but the operation failed".
 *
 * Throw this from a parser (or return [ApiResult.failure] with an instance) when
 * NGA answers with a site error instead of data. It is classified as
 * [ApiError.Business]; the throwable message is safe to show as a bounded reason.
 */
class BusinessException(
    val code: String? = null,
    override val message: String,
) : Exception(message)
