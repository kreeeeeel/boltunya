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

private val VOICE_SAMPLES = mapOf(
    "piper-en-glados" to "Hello. This is a real voice test. The system is ready.",
    "piper-ru-dmitri" to "Привет. Это проверка нового голоса. Всё работает на вашем компьютере.",
)

class LiveExpandedSpeechCatalogTest {
    @Test
    fun `expanded catalog voices install and produce audible native notes with recognizable speech`() {
        assumeTrue(System.getenv("AIGRAM_LIVE_SPEECH") == "true")
        val verified = Path.of("..", "build", "speech-verification").toAbsolutePath().normalize()
        assumeTrue(Files.isDirectory(verified))
        val directory = Files.createTempDirectory(verified, "expanded-catalog-")
        val proof = Files.createTempDirectory(verified, "catalog-proof-")
        val started = System.nanoTime()

        try {
            val assets = listOf("sherpa-runtime", "whisper-tiny") + VOICE_SAMPLES.keys
            verifiedSpeechCatalog(directory, verified, assets).use { catalog ->
                val engine = SpeechEngine(directory, catalog, SpeechWorkerProcess(Json))
                val codec = AudioCodec(directory)
                val metrics = VOICE_SAMPLES.entries.stream().map { (id, text) ->
                    val synthesisStarted = System.nanoTime()
                    val wave = engine.synthesize(text, id)

                    try {
                        val synthesisMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - synthesisStarted)
                        val voice = codec.toVoiceNote(wave.path, proof.resolve("$id.ogg"))
                        assertTrue(wave.sampleRate in 16000..48000)
                        assertTrue(wave.durationMillis in 1000..30000)
                        assertTrue(voice.duration in 1..30)
                        assertEquals(63, voice.waveform.size)
                        assertTrue(voice.waveform.any { it.toInt() != 0 })
                        assertEquals(
                            "OggS",
                            Files.newInputStream(voice.path).use { it.readNBytes(4).toString(Charsets.US_ASCII) },
                        )

                        val language = if (id == "piper-en-glados") {
                            "en"
                        } else {
                            "ru"
                        }
                        val recognition = codec.toRecognitionWave(voice.path, directory.resolve("$id.wav"))
                        val transcript = engine.transcribe(recognition, "whisper-tiny", language)
                        val greeting = if (language == "en") {
                            "hello"
                        } else {
                            "привет"
                        }
                        assertTrue(transcript.lowercase().contains(greeting), "The new voice must produce speech.")

                        buildJsonObject {
                            put("modelId", id)
                            put("sampleRate", wave.sampleRate)
                            put("durationMillis", wave.durationMillis)
                            put("synthesisMillis", synthesisMillis)
                            put("audioBytes", Files.size(voice.path))
                            put("recognizedSyntheticGreeting", true)
                        }
                    } finally {
                        Files.deleteIfExists(wave.path)
                    }
                }.toList()

                val report = buildJsonObject {
                    put("synthetic", true)
                    put("telegramMessagesSent", 0)
                    put("totalMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    put("voices", JsonArray(metrics))
                }
                Files.writeString(proof.resolve("proof.json"), report.toString())
                println("Expanded speech catalog proof: ${verified.relativize(proof)}")
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
