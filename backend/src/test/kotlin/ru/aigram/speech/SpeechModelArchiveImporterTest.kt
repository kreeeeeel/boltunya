package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.InvalidSpeechModelImportException
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SpeechModelArchiveImporterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `compatible voice retains its exact model metadata and original licence files`() {
        val archive = Files.write(directory.resolve("voice.tar.bz2"), speechImportArchive())
        val target = Files.createDirectory(directory.resolve("import"))

        val result = SpeechModelArchiveImporter().extract(archive, target)

        assertEquals("model.onnx", result.modelFile)
        assertEquals("Original licence bytes\n", Files.readString(target.resolve("legal/LICENSE.txt")))
        assertEquals("Original voice conditions", Files.readString(target.resolve("MODEL_CARD")))
    }

    @Test
    fun `compatible espeak voice names retain interior spaces without normalizing their bytes`() {
        val name = "espeak-ng-data/voices/!v/Mr serious"
        val content = "name Mr serious\n".toByteArray()
        val files = speechImportFiles() + ("voice/$name" to content)
        val archive = Files.write(directory.resolve("voice.tar.bz2"), speechImportArchive(files))
        val target = Files.createDirectory(directory.resolve("import"))

        SpeechModelArchiveImporter().extract(archive, target)

        assertContentEquals(content, Files.readAllBytes(target.resolve(name)))
    }

    @Test
    fun `space support still rejects ambiguous Windows names and case duplicate paths`() {
        val names = listOf(" leading", "trailing ", ".leading", "trailing.", "CON .txt", "LPT1.txt")
        val cases = names.stream().map { name ->
            speechImportFiles() + ("voice/espeak-ng-data/voices/!v/$name" to byteArrayOf(1))
        }.toList() + listOf(
            speechImportFiles() + mapOf(
                "voice/espeak-ng-data/voices/!v/Mr serious" to byteArrayOf(1),
                "voice/espeak-ng-data/voices/!v/mr serious" to byteArrayOf(2),
            ),
        )
        cases.stream().forEach { files ->
            val archive = Files.createTempFile(directory, "invalid-name-", ".tar.bz2")
            Files.write(archive, speechImportArchive(files))
            val target = Files.createTempDirectory(directory, "target-")

            assertFailsWith<InvalidSpeechModelImportException> {
                SpeechModelArchiveImporter().extract(archive, target)
            }
        }
    }

    @Test
    fun `rejects incomplete multiple model executable traversal and multiple root layouts`() {
        val source = speechImportFiles()
        val cases = listOf(
            source - "voice/tokens.txt",
            source + ("voice/second.onnx" to byteArrayOf(1)),
            source + ("voice/espeak-ng-data/unsafe.DLL" to byteArrayOf(1)),
            source + ("voice/../../outside" to byteArrayOf(1)),
            source + ("other/tokens.txt" to byteArrayOf(1)),
        )
        cases.stream().forEach { files ->
            val archive = Files.createTempFile(directory, "invalid-", ".tar.bz2")
            Files.write(archive, speechImportArchive(files))
            val target = Files.createTempDirectory(directory, "target-")
            assertFailsWith<InvalidSpeechModelImportException> {
                SpeechModelArchiveImporter().extract(archive, target)
            }
        }
    }

    @Test
    fun `rejects oversized declared content before allocating or extracting it`() {
        val header = ByteArray(512)
        val entry = TarArchiveEntry("voice/model.onnx")
        entry.size = 513L * 1024 * 1024
        entry.writeEntryHeader(header)
        val bytes = ByteArrayOutputStream()
        BZip2CompressorOutputStream(bytes).use {
            it.write(header)
            it.write(ByteArray(1024))
        }
        val archive = Files.write(directory.resolve("oversized.tar.bz2"), bytes.toByteArray())

        assertFailsWith<InvalidSpeechModelImportException> {
            SpeechModelArchiveImporter().extract(archive, Files.createDirectory(directory.resolve("staging")))
        }
    }

    @Test
    fun `rejects symbolic links without creating their targets`() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            val link = TarArchiveEntry("voice/model.onnx", TarConstants.LF_SYMLINK)
            link.linkName = "../../outside"
            tar.putArchiveEntry(link)
            tar.closeArchiveEntry()
        }
        val archive = Files.write(directory.resolve("link.tar.bz2"), bytes.toByteArray())
        assertFailsWith<InvalidSpeechModelImportException> {
            SpeechModelArchiveImporter().extract(archive, Files.createDirectory(directory.resolve("staging")))
        }
        assertFalse(Files.exists(directory.resolve("outside")))
    }

    @Test
    fun `a huge later entry cannot overflow the expanded byte budget`() {
        val first = TarArchiveEntry("voice/MODEL_CARD")
        first.size = 1
        val firstHeader = ByteArray(512)
        first.writeEntryHeader(firstHeader)
        val huge = TarArchiveEntry("voice/model.onnx")
        huge.size = Long.MAX_VALUE
        val hugeHeader = ByteArray(512)
        huge.writeEntryHeader(hugeHeader, ZipEncodingHelper.getZipEncoding("UTF-8"), true)
        val output = ByteArrayOutputStream()
        BZip2CompressorOutputStream(output).use {
            it.write(firstHeader)
            it.write(ByteArray(512))
            it.write(hugeHeader)
            it.write(ByteArray(1024))
        }
        val archive = Files.write(directory.resolve("overflow.tar.bz2"), output.toByteArray())

        assertFailsWith<InvalidSpeechModelImportException> {
            SpeechModelArchiveImporter().extract(archive, Files.createDirectory(directory.resolve("staging")))
        }
    }
}
