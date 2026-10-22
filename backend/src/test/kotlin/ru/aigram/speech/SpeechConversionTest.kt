package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.SpeechInferenceFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechConversionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `RVC synthesis converts the selected local base voice and deletes the intermediate wave`() {
        prepare()
        val requests = CopyOnWriteArrayList<SpeechWorkerRequest>()
        val executor = SpeechProcessExecutor { _, request, _ ->
            requests.add(request)
            if (request.operation == "synthesis") {
                assertEquals("Синтетическая проверка.", request.text)
                assertEquals(1.25f, request.speed)
                assertEquals(directory.resolve("speech/piper-ru-irina").toString(), request.modelDirectory)
            } else {
                assertEquals("conversion", request.operation)
                assertEquals("", request.text)
                assertEquals(directory.resolve("speech/applio-runtime").toString(), request.runtimeDirectory)
                assertTrue(Files.isRegularFile(Path.of(request.input)))
                assertEquals("YandexAlice.pth", request.modelFile)
                assertEquals("added_IVF130_Flat_nprobe_1_YandexAlice_v2.index", request.indexFile)
            }
            Files.write(Path.of(request.output), byteArrayOf(1, 2, 3))
            SpeechWorkerResult(sampleRate = 40000, samples = 80000)
        }
        speechCatalog(directory, SpeechDownloadClient { _, _, _ -> error("Offline") }).use { catalog ->
            val engine = SpeechEngine(directory, catalog, executor)
            val wave = engine.synthesize("Синтетическая проверка.", "rvc-alice", 1.25)

            assertEquals(listOf("synthesis", "conversion"), requests.map { it.operation })
            assertEquals(2000L, wave.durationMillis)
            assertTrue(Files.isRegularFile(wave.path))
            assertFalse(Files.exists(Path.of(requests.last().input)))
            Files.list(directory.resolve("speech/work")).use { paths ->
                assertEquals(listOf(wave.path), paths.toList())
            }
        }
    }

    @Test
    fun `failed conversion never returns the base voice as if it were the requested character`() {
        prepare()
        val executor = SpeechProcessExecutor { _, request, _ ->
            if (request.operation == "conversion") {
                throw SpeechInferenceFailedException()
            }
            Files.write(Path.of(request.output), byteArrayOf(1))
            SpeechWorkerResult(sampleRate = 22050, samples = 22050)
        }
        speechCatalog(directory, SpeechDownloadClient { _, _, _ -> error("Offline") }).use { catalog ->
            assertFailsWith<SpeechInferenceFailedException> {
                SpeechEngine(directory, catalog, executor).synthesize("Проверка.", "rvc-alice")
            }
            Files.list(directory.resolve("speech/work")).use { paths -> assertEquals(0L, paths.count()) }
        }
    }

    private fun prepare() {
        listOf("sherpa-runtime", "applio-runtime", "piper-ru-irina", "rvc-alice").stream().forEach {
            installSpeechFixture(directory, it)
        }
    }
}
