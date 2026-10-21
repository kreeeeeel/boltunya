package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechArtifactDownloaderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `accepts exact pinned bytes and reports actual progress`() {
        val bytes = ByteArray(100000) { (it % 251).toByte() }
        val source = speechDefinitions().first()
        val definition = source.copy(
            asset = source.asset.copy(sizeBytes = bytes.size.toLong()),
            sha256 = speechDigest(bytes),
        )
        val progress = mutableListOf<Long>()
        val target = directory.resolve("asset")

        SpeechArtifactDownloader().copy(definition, ByteArrayInputStream(bytes), target, progress::add)

        assertContentEquals(bytes, Files.readAllBytes(target))
        assertEquals(bytes.size.toLong(), progress.last())
    }

    @Test
    fun `rejects wrong checksum truncation and bytes above the pinned limit`() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = speechDefinitions().first()
        val expected = source.copy(asset = source.asset.copy(sizeBytes = 3), sha256 = speechDigest(bytes))
        val client = SpeechArtifactDownloader()

        assertFailsWith<SpeechInstallFailedException> {
            client.copy(expected, ByteArrayInputStream(byteArrayOf(1, 2, 4)), directory.resolve("checksum")) { }
        }
        assertFailsWith<SpeechInstallFailedException> {
            client.copy(expected, ByteArrayInputStream(byteArrayOf(1, 2)), directory.resolve("truncated")) { }
        }
        assertFailsWith<SpeechInstallFailedException> {
            client.copy(expected, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), directory.resolve("oversized")) { }
        }
        assertEquals(0L, Files.size(directory.resolve("oversized")))
    }
}
