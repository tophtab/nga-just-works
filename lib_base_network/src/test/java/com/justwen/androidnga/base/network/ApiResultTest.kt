package com.justwen.androidnga.base.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [ApiResult] has exactly one of two states, so the query helpers and factories
 * must never produce a result that is both (or neither) success and failure.
 */
class ApiResultTest {

    @Test
    fun successCarriesDataOnly() {
        val result = ApiResult.success("payload")

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("payload", result.dataOrNull)
        assertNull(result.errorOrNull)
    }

    @Test
    fun failureCarriesErrorOnly() {
        val error = ApiError.Timeout(SocketTimeoutException("timeout"))
        val result = ApiResult.failure(error)

        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.dataOrNull)
        assertSame(error, result.errorOrNull)
    }

    @Test
    fun failureFromCauseClassifiesAndKeepsCause() {
        val cause = IOException("offline")

        val result = ApiResult.failure(cause)

        assertTrue(result.isFailure)
        assertSame(cause, result.errorOrNull?.cause)
        assertTrue(result.errorOrNull is ApiError.NetworkUnavailable)
    }

    @Test
    fun successfulEmptyPayloadIsStillSuccess() {
        val result = ApiResult.success(emptyList<String>())

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), result.dataOrNull)
    }
}
