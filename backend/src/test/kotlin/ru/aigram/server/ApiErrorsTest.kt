package ru.aigram.server

import org.junit.jupiter.api.Test
import ru.aigram.exception.impl.RequestFailedException
import kotlin.test.assertEquals

class ApiErrorsTest {
    @Test
    fun `current failures retain their safe API response without an error journal dependency`() {
        val response = ApiErrors().response(IllegalStateException("Synthetic private failure"))

        assertEquals(502, response.statusCode.value())
        assertEquals("REQUEST_FAILED", response.body?.code)
        assertEquals(RequestFailedException().message, response.body?.message)
    }
}
