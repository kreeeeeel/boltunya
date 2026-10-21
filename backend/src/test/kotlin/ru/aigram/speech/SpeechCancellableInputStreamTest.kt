package ru.aigram.speech

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechCancellableInputStreamTest {
    @Test
    fun `bulk reads preserve bytes and notice cancellation before another archive buffer fill`() {
        SpeechCancellableInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3))).use { input ->
            val bytes = ByteArray(2)
            assertEquals(2, input.read(bytes, 0, 2))
            assertEquals(1, bytes[0].toInt())
            assertEquals(2, bytes[1].toInt())
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<CancellationException> { input.read(bytes, 0, 2) }
                assertFailsWith<CancellationException> { input.read() }
            } finally {
                Thread.interrupted()
            }
        }
    }
}
