package ru.aigram.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.aigram.speech.codec.AudioCodec
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveSpeechVoicesTest {
    @Test
    fun `all curated voices synthesize real local opus with Telegram duration and waveform`() {
        assumeTrue(System.getenv("AIGRAM_LIVE_SPEECH") == "true")
        val verified = Path.of("..", "build", "speech-verification").toAbsolutePath().normalize()
        assumeTrue(Files.isDirectory(verified))
        val reportFile = verified.resolve("voices-metrics.json")
        Files.deleteIfExists(reportFile)
        val directory = Files.createTempDirectory(verified, "voices-")
        val models = listOf("piper-ru-irina", "piper-ru-ruslan", "piper-en-amy")
        val installationStarted = System.nanoTime()
        try {
            verifiedSpeechCatalog(directory, verified, listOf("sherpa-runtime") + models).use { catalog ->
                val installationMillis = elapsedMillis(installationStarted)
                val engine = SpeechEngine(directory, catalog, SpeechWorkerProcess(Json, ApplioSpeechWorker()))
                val codec = AudioCodec(directory)
                val metrics = models.stream().map { id ->
                    val text = if (id == "piper-en-amy") {
                        "Hello! This is a local voice message test."
                    } else {
                        "Привет! Это проверка голосовых сообщений."
                    }
                    val synthesisStarted = System.nanoTime()
                    val wave = engine.synthesize(text, id)
                    val synthesisMillis = elapsedMillis(synthesisStarted)
                    try {
                        val encodingStarted = System.nanoTime()
                        val voice = codec.toVoiceNote(wave.path, directory.resolve("$id.ogg"))
                        val encodingMillis = elapsedMillis(encodingStarted)
                        assertTrue(wave.sampleRate in 16000..48000)
                        assertTrue(wave.durationMillis in 1000..30000)
                        assertTrue(voice.duration in 1..30)
                        assertEquals(63, voice.waveform.size)
                        assertTrue(voice.waveform.any { it.toInt() != 0 })
                        val header = Files.newInputStream(voice.path).use {
                            it.readNBytes(4).toString(Charsets.US_ASCII)
                        }
                        assertEquals("OggS", header)
                        buildJsonObject {
                            put("modelId", id)
                            put("synthesisMillis", synthesisMillis)
                            put("encodingMillis", encodingMillis)
                            put("durationMillis", wave.durationMillis)
                        }
                    } finally {
                        Files.deleteIfExists(wave.path)
                    }
                }.toList()
                val report = buildJsonObject {
                    put("synthetic", true)
                    put("runtimeVersion", "1.13.8")
                    put("installationMillis", installationMillis)
                    put("voices", JsonArray(metrics))
                }
                Files.writeString(reportFile, report.toString())
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun elapsedMillis(started: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
}
