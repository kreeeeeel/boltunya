package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechNotInstalledException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpeechEngineTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `synthesis preserves exact Unicode text and returns a caller owned wave without protocol files`() {
        installSpeechFixture(directory, "sherpa-runtime")
        installSpeechFixture(directory, "piper-ru-irina")
        val text = "  Привет 👋\nВторая строка.  "
        val execute = SpeechProcessExecutor { nativeJar, request, _ ->
            assertEquals(directory.resolve("speech/sherpa-runtime/native.jar"), nativeJar)
            assertEquals(text, request.text)
            assertEquals(1.25f, request.speed)
            Files.write(Path.of(request.output), byteArrayOf(1, 2, 3))
            SpeechWorkerResult(sampleRate = 22050, samples = 44100)
        }
        val downloader = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, downloader).use { catalog ->
            val engine = SpeechEngine(directory, catalog, execute)
            val wave = engine.synthesize(text, "piper-ru-irina", 1.25)
            assertEquals(22050, wave.sampleRate)
            assertEquals(2000L, wave.durationMillis)
            assertTrue(Files.isRegularFile(wave.path))
            Files.list(directory.resolve("speech/work")).use { files ->
                assertEquals(listOf(wave.path), files.toList())
            }
            Files.delete(wave.path)
        }
    }

    @Test
    fun `missing assets never trigger downloads and failed inference removes generated wave`() {
        val execute = SpeechProcessExecutor { _, _, _ -> throw SpeechInferenceFailedException() }
        val downloader = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, downloader).use { catalog ->
            val engine = SpeechEngine(directory, catalog, execute)
            assertFailsWith<SpeechNotInstalledException> { engine.synthesize("Привет", "piper-ru-irina") }
            installSpeechFixture(directory, "sherpa-runtime")
            installSpeechFixture(directory, "piper-ru-irina")
            assertFailsWith<SpeechInferenceFailedException> { engine.synthesize("Привет", "piper-ru-irina") }
            Files.list(directory.resolve("speech/work")).use { files -> assertEquals(0L, files.count()) }
        }
    }

    @Test
    fun `recognition uses only selected local model language and caller keeps the input`() {
        installSpeechFixture(directory, "sherpa-runtime")
        installSpeechFixture(directory, "whisper-tiny")
        val input = Files.write(directory.resolve("input.wav"), byteArrayOf(1))
        val execute = SpeechProcessExecutor { _, request, _ ->
            assertEquals("recognition", request.operation)
            assertEquals("uk", request.language)
            assertEquals(input.toString(), request.input)
            assertEquals("", request.text)
            SpeechWorkerResult(text = "Перевірка")
        }
        val downloader = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, downloader).use { catalog ->
            val engine = SpeechEngine(directory, catalog, execute)
            assertEquals("Перевірка", engine.transcribe(input, "whisper-tiny", "uk"))
            assertTrue(Files.isRegularFile(input))
            assertFailsWith<InvalidSpeechRequestException> {
                engine.transcribe(input, "whisper-tiny", "not-a-language")
            }
        }
    }
}
