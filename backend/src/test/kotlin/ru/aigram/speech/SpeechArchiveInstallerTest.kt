package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SpeechArchiveInstallerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `extracts required model files and preserves source provenance and licensing notices`() {
        val model = speechDefinitions().first { it.asset.id == "piper-ru-irina" }
        val root = model.archiveRoot
        val archive = archive(
            listOf(
                "$root/voice.onnx",
                "$root/MODEL_CARD",
                "$root/README.md",
                "$root/LICENSE.txt",
                "$root/COPYING",
                "$root/unused.onnx",
            ),
        )
        val target = directory.resolve("model")

        SpeechArchiveInstaller().extract(archive, target, model.copy(requiredFiles = listOf("voice.onnx")))

        assertEquals("model-data", Files.readString(target.resolve("voice.onnx")))
        assertEquals("model-data", Files.readString(target.resolve("MODEL_CARD")))
        assertEquals("model-data", Files.readString(target.resolve("README.md")))
        assertEquals("model-data", Files.readString(target.resolve("LICENSE.txt")))
        assertEquals("model-data", Files.readString(target.resolve("COPYING")))
        assertFalse(Files.exists(target.resolve("unused.onnx")))
    }

    @Test
    fun `rejects traversal and symlinks instead of following them`() {
        val model = speechDefinitions().first { it.asset.id == "piper-ru-irina" }
        val traversal = archive(listOf("${model.archiveRoot}/../../outside"))
        assertFailsWith<SpeechInstallFailedException> {
            SpeechArchiveInstaller().extract(traversal, directory.resolve("traversal"), model)
        }
        val link = archive(listOf("${model.archiveRoot}/voice.onnx"), true)
        assertFailsWith<SpeechInstallFailedException> {
            SpeechArchiveInstaller().extract(link, directory.resolve("link"), model)
        }
        assertFalse(Files.exists(directory.resolve("outside")))
    }

    private fun archive(names: List<String>, link: Boolean = false): Path {
        val archive = Files.createTempFile(directory, "model-", ".tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(Files.newOutputStream(archive))).use { output ->
            names.stream().forEach { name ->
                val bytes = "model-data".toByteArray()
                val entry = if (link) {
                    TarArchiveEntry(name, TarConstants.LF_SYMLINK)
                } else {
                    TarArchiveEntry(name)
                }
                if (link) {
                    entry.linkName = "../../outside"
                } else {
                    entry.size = bytes.size.toLong()
                }
                output.putArchiveEntry(entry)
                if (!link) {
                    output.write(bytes)
                }
                output.closeArchiveEntry()
            }
        }
        return archive
    }
}
