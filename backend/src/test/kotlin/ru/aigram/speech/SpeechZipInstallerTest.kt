package ru.aigram.speech

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SpeechZipInstallerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `RVC archives preserve exact model index and author notices without unrelated executables`() {
        val model = speechDefinitions().first { it.asset.id == "rvc-alice" }
        val names = model.requiredFiles + listOf("README.md", "author-credit.txt", "run.cmd")
        val archive = archive(names)
        val target = directory.resolve("voice")

        SpeechArchiveInstaller().extract(archive, target, model)

        model.requiredFiles.stream().forEach { assertEquals("synthetic", Files.readString(target.resolve(it))) }
        assertEquals("synthetic", Files.readString(target.resolve("author-credit.txt")))
        assertFalse(Files.exists(target.resolve("run.cmd")))
    }

    @Test
    fun `portable extraction retains interpreter upstream CLI support models and their notices but never launchers`() {
        val runtime = speechDefinitions().first { it.asset.id == "applio-runtime" }
        val names = listOf(
            "env/python.exe", "env/python312.dll", "env/Lib/site-packages/torch/LICENSE",
            "rvc/infer/infer.py", "core.py", "assets/config_template.json", "TERMS_OF_USE.md",
            "run-applio.bat", "run-updater.bat",
        )
        val required = listOf("env/python.exe", "rvc/infer/infer.py", "core.py", "assets/config_template.json")
        val target = directory.resolve("runtime")
        SpeechArchiveInstaller().extract(
            archive(names),
            target,
            runtime.copy(requiredFiles = listOf("env", "rvc") + required),
        )

        required.stream().forEach { assertEquals("synthetic", Files.readString(target.resolve(it))) }
        assertEquals("synthetic", Files.readString(target.resolve("env/Lib/site-packages/torch/LICENSE")))
        assertFalse(Files.exists(target.resolve("run-applio.bat")))
        assertFalse(Files.exists(target.resolve("run-updater.bat")))
    }

    @Test
    fun `ZIP traversal Windows aliases case collisions and symlinks are rejected even when not selected`() {
        val model = speechDefinitions().first { it.asset.id == "rvc-alice" }
        listOf("../outside", "C:/outside", "env/../outside", "NUL.txt", "file.").stream().forEach { name ->
            assertFailsWith<SpeechInstallFailedException> {
                SpeechArchiveInstaller().extract(
                    archive(listOf(name)),
                    Files.createTempDirectory(directory, "bad-"),
                    model,
                )
            }
        }
        assertFailsWith<SpeechInstallFailedException> {
            SpeechArchiveInstaller().extract(
                archive(listOf("model.pth", "MODEL.pth")),
                directory.resolve("duplicate"),
                model,
            )
        }
        assertFailsWith<SpeechInstallFailedException> {
            SpeechArchiveInstaller().extract(archive(listOf("link"), true), directory.resolve("link"), model)
        }
        assertFalse(Files.exists(directory.resolve("outside")))
    }

    @Test
    fun `declared expanded model size is bounded before opening the entry stream`() {
        val archive = archive(listOf("huge.pth"))
        val bytes = Files.readAllBytes(archive)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val central = IntStream.range(0, bytes.size - 46).filter { buffer.getInt(it) == 0x02014b50 }
            .findFirst().asInt
        buffer.putInt(central + 24, 600 * 1024 * 1024)
        Files.write(archive, bytes)
        val model = speechDefinitions().first { it.asset.id == "rvc-alice" }

        assertFailsWith<SpeechInstallFailedException> {
            SpeechArchiveInstaller().extract(archive, directory.resolve("bounded"), model)
        }
        assertFalse(Files.exists(directory.resolve("bounded/huge.pth")))
    }

    private fun archive(names: List<String>, symlink: Boolean = false): Path {
        val archive = Files.createTempFile(directory, "model-", ".zip")
        ZipArchiveOutputStream(archive).use { output ->
            names.stream().forEach { name ->
                val entry = ZipArchiveEntry(name)
                if (symlink) {
                    entry.unixMode = 0xA1FF
                }
                output.putArchiveEntry(entry)
                output.write("synthetic".toByteArray())
                output.closeArchiveEntry()
            }
        }
        return archive
    }
}
