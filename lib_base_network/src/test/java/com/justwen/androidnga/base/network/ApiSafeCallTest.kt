package com.justwen.androidnga.base.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ApiSafeCallTest {

    @Test
    fun successReturnsParsedData() = runBlocking {
        val result = apiSafeCall(
            request = { "raw payload" },
            parse = { body -> ApiResult.success(body.length) },
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(11, (result as ApiResult.Success).data)
    }

    @Test
    fun requestFailureIsClassified() = runBlocking {
        val cause = IOException("offline")

        val result = apiSafeCall<Unit>(
            request = { throw cause },
            parse = { ApiResult.success(Unit) },
        )

        result as ApiResult.Failure
        assertTrue(result.error is ApiError.NetworkUnavailable)
        assertSame(cause, result.error.cause)
    }

    @Test
    fun parseFailureIsClassified() = runBlocking {
        val cause = BusinessException(code = "7", message = "请重新登录")

        val result = apiSafeCall<Unit>(
            request = { "error payload" },
            parse = { throw cause },
        )

        result as ApiResult.Failure
        val error = result.error as ApiError.Business
        assertEquals("7", error.code)
        assertEquals("请重新登录", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun parseReturnedFailureIsPassedThrough() = runBlocking {
        val error = ApiError.JsonParse("bad json", IllegalStateException("bad json"))

        val result = apiSafeCall<Unit>(
            request = { "not json" },
            parse = { ApiResult.failure(error) },
        )

        result as ApiResult.Failure
        assertSame(error, result.error)
    }

    @Test
    fun cancellationIsRethrownInsteadOfBecomingFailure() {
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            runBlocking {
                apiSafeCall<Unit>(
                    request = { throw cancellation },
                    parse = { ApiResult.success(Unit) },
                )
            }
        }.exceptionOrNull()

        assertTrue("cancellation must escape apiSafeCall, got $thrown", thrown is CancellationException)
        assertEquals("cancelled", thrown?.message)
    }

    @Test
    fun cancellationFromParseIsRethrown() {
        val cancellation = CancellationException("parse cancelled")

        val thrown = runCatching {
            runBlocking {
                apiSafeCall<Unit>(
                    request = { "payload" },
                    parse = { throw cancellation },
                )
            }
        }.exceptionOrNull()

        assertTrue("cancellation must escape apiSafeCall, got $thrown", thrown is CancellationException)
    }
}
