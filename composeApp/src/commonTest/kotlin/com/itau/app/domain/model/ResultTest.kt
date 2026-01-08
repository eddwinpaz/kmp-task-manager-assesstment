package com.itau.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultTest {

    @Test
    fun `Success should contain value`() {
        val result = Result.Success("test value")

        assertTrue(result is Result.Success)
        assertEquals("test value", result.data)
    }

    @Test
    fun `Error should contain exception`() {
        val exception = Exception("test error")
        val result = Result.Error(exception)

        assertTrue(result is Result.Error)
        assertEquals("test error", result.exception.message)
    }

    @Test
    fun `onSuccess should be called for Success result`() {
        val result: Result<String> = Result.Success("test")
        var wasCalled = false

        result.onSuccess {
            wasCalled = true
            assertEquals("test", it)
        }

        assertTrue(wasCalled)
    }

    @Test
    fun `onSuccess should not be called for Error result`() {
        val result: Result<String> = Result.Error(Exception("error"))
        var wasCalled = false

        result.onSuccess { wasCalled = true }

        assertFalse(wasCalled)
    }

    @Test
    fun `onError should be called for Error result`() {
        val result: Result<String> = Result.Error(Exception("test error"))
        var wasCalled = false

        result.onError {
            wasCalled = true
            assertEquals("test error", it.message)
        }

        assertTrue(wasCalled)
    }

    @Test
    fun `onError should not be called for Success result`() {
        val result: Result<String> = Result.Success("test")
        var wasCalled = false

        result.onError { wasCalled = true }

        assertFalse(wasCalled)
    }

    @Test
    fun `resultOf should return Success for successful operation`() {
        val result = resultOf { "success" }

        assertTrue(result is Result.Success)
        assertEquals("success", (result as Result.Success).data)
    }

    @Test
    fun `resultOf should return Error for failed operation`() {
        val result = resultOf<String> { throw Exception("failure") }

        assertTrue(result is Result.Error)
        assertEquals("failure", (result as Result.Error).exception.message)
    }
}
