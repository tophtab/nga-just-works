package com.justwen.androidnga.base.network

import com.alibaba.fastjson2.JSONException
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Every transport/parse failure must map to its own [ApiError] subtype and keep
 * the original throwable, so callers can distinguish retryable reads from
 * rejections without inspecting exception strings.
 */
class ApiErrorMappingTest {

    private fun classify(cause: Throwable): ApiError =
        ApiResult.failure(cause).let { result ->
            assertTrue("expected a failure for $cause", result is ApiResult.Failure)
            (result as ApiResult.Failure).error
        }

    @Test
    fun businessExceptionBecomesBusinessError() {
        val cause = BusinessException(code = "42", message = "没有权限")

        val error = classify(cause)

        assertEquals(ApiError.Business::class.java, error.javaClass)
        error as ApiError.Business
        assertEquals("42", error.code)
        assertEquals("没有权限", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun businessMessageFallsBackToSuppliedMessage() {
        val cause = BusinessException(message = "")

        val error = ApiResult.failure(cause, message = "请重新登录").errorOrNull

        error as ApiError.Business
        assertEquals("请重新登录", error.message)
    }

    @Test
    fun socketTimeoutBecomesTimeout() {
        val cause = SocketTimeoutException("timeout")

        val error = classify(cause)

        assertTrue("SocketTimeoutException must not become IOException", error is ApiError.Timeout)
        assertSame(cause, error.cause)
    }

    @Test
    fun httpExceptionBecomesHttpStatusWithCodeAndBody() {
        val body = ResponseBody.create(null, "denied")
        val cause = HttpException(Response.error<String>(403, body))

        val error = classify(cause)

        error as ApiError.HttpStatus
        assertEquals(403, error.code)
        assertNotNull(error.message)
        assertEquals("denied", error.errorBody)
        assertSame(cause, error.cause)
    }

    @Test
    fun httpExceptionWithEmptyBodyHasEmptyErrorBody() {
        val error = classify(HttpException(Response.error<String>(500, ResponseBody.create(null, ""))))

        error as ApiError.HttpStatus
        assertEquals(500, error.code)
        assertEquals("", error.errorBody)
    }

    @Test
    fun httpErrorBodyIsLengthBounded() {
        val error = classify(HttpException(Response.error<String>(500, ResponseBody.create(null, "x".repeat(5000)))))

        error as ApiError.HttpStatus
        assertEquals(2048, error.errorBody?.length)
    }

    @Test
    fun httpErrorBodyIsNotRenderedByToString() {
        val error = classify(HttpException(Response.error<String>(500, ResponseBody.create(null, "private body"))))

        error as ApiError.HttpStatus
        assertEquals("private body", error.errorBody)
        assertFalse("toString must not expose the response body", error.toString().contains("private body"))
        assertTrue(error.toString().contains("code=500"))
    }

    @Test
    fun jsonExceptionBecomesJsonParse() {
        val cause = JSONException("bad token")

        val error = classify(cause)

        error as ApiError.JsonParse
        assertEquals("bad token", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun ioExceptionBecomesNetworkUnavailable() {
        val cause = IOException("offline")

        val error = classify(cause)

        assertTrue(error is ApiError.NetworkUnavailable)
        assertSame(cause, error.cause)
    }

    @Test
    fun unknownThrowableBecomesUnexpected() {
        val cause = IllegalStateException("unexpected")

        val error = classify(cause)

        assertTrue(error is ApiError.Unexpected)
        assertSame(cause, error.cause)
    }
}
