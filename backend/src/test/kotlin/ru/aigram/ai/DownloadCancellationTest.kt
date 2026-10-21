package ru.aigram.ai

import org.junit.jupiter.api.Test
import java.io.Closeable
import java.util.concurrent.CancellationException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadCancellationTest {
    @Test
    fun `cancelling closes an active download and prevents later progress`() {
        val cancellation = DownloadCancellation()
        var closed = false
        cancellation.attach(Closeable { closed = true })
        cancellation.cancel()
        assertTrue(closed)
        assertFailsWith<CancellationException> { cancellation.check() }
        var lateClosed = false
        assertFailsWith<CancellationException> { cancellation.attach(Closeable { lateClosed = true }) }
        assertTrue(lateClosed)
    }
}
