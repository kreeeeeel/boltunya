package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.IntegrationException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadWriterTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reports actual bytes for known and unknown content lengths`() {
        val bytes = ByteArray(150000) { (it % 251).toByte() }
        listOf(bytes.size.toLong(), 0L).stream().forEach { total ->
            val updates = mutableListOf<Long>()
            val target = directory.resolve("engine-$total.zip")
            DownloadWriter().copy(bytes.inputStream(), target, total) { received, expected ->
                updates.add(received)
                assertEquals(total, expected)
            }
            assertTrue(updates.size > 1)
            assertEquals(bytes.size.toLong(), updates.last())
            assertContentEquals(bytes, Files.readAllBytes(target))
        }
    }

    @Test
    fun `rejects truncated downloads`() {
        assertFailsWith<IntegrationException> {
            DownloadWriter().copy(byteArrayOf(1, 2).inputStream(), directory.resolve("partial.zip"), 10) { _, _ ->
                Unit
            }
        }
    }
}
