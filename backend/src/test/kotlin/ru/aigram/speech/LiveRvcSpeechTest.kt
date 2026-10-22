package ru.aigram.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.aigram.speech.codec.AudioCodec
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val RVC_VERIFIED_ARTIFACTS = listOf(
    "ApplioV3.7.0.zip",
    "YandexAlice.zip",
    "sherpa-onnx-native-lib-win-x64-1.13.8.jar",
    "vits-piper-ru_RU-irina-medium-int8.tar.bz2",
    "sherpa-onnx-whisper-tiny.tar.bz2",
)

class LiveRvcSpeechTest {
    @Test
    fun `cached Alice converts real Piper speech through the pinned portable CPU runtime into an Opus voice note`() {
        assumeTrue(System.getenv("AIGRAM_LIVE_RVC") == "true")
        val verified = Path.of("..", "build", "speech-verification").toAbsolutePath().normalize()
        assumeTrue(RVC_VERIFIED_ARTIFACTS.stream().allMatch { Files.isRegularFile(verified.resolve(it)) })
        val proof = Files.createTempDirectory(verified, "rvc-proof-")
        val profile = Files.createDirectory(proof.resolve("profile"))
        val started = System.nanoTime()
        val assets = listOf("sherpa-runtime", "piper-ru-irina", "applio-runtime", "rvc-alice", "whisper-tiny")
        val installationStarted = System.nanoTime()

        // Cached original archives still pass the production byte-count and SHA checks before extraction.
        verifiedSpeechCatalog(profile, verified, assets, 1200).use { catalog ->
            val installationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - installationStarted)
            val operations = CopyOnWriteArrayList<String>()
            val baseDigest = AtomicReference<String>()
            val convertedDigest = AtomicReference<String>()
            val worker = SpeechWorkerProcess(Json, ApplioSpeechWorker())
            val process = SpeechProcessExecutor { nativeJar, request, result ->
                val response = worker.execute(nativeJar, request, result)
                operations.add(request.operation)
                if (request.operation == "synthesis") {
                    baseDigest.set(speechDigest(Files.readAllBytes(Path.of(request.output))))
                }
                if (request.operation == "conversion") {
                    convertedDigest.set(speechDigest(Files.readAllBytes(Path.of(request.output))))
                }
                response
            }
            val engine = SpeechEngine(profile, catalog, process)
            val synthesisStarted = System.nanoTime()
            val wave = engine.synthesize("Привет! Это настоящая проверка голоса Алисы.", "rvc-alice")
            try {
                val synthesisMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - synthesisStarted)
                val codec = AudioCodec(profile)
                val voice = codec.toVoiceNote(wave.path, proof.resolve("alice.ogg"))
                assertTrue(wave.sampleRate in 16000..48000)
                assertTrue(wave.durationMillis in 1000..30000)
                assertTrue(voice.duration in 1..30)
                assertEquals(63, voice.waveform.size)
                assertTrue(voice.waveform.any { it.toInt() != 0 })
                assertEquals(
                    "OggS",
                    Files.newInputStream(voice.path).use { it.readNBytes(4).toString(Charsets.US_ASCII) },
                )
                assertTrue(Files.size(voice.path) > 1024)
                assertNotEquals(baseDigest.get(), convertedDigest.get(), "Conversion must not return the base voice.")
                val recognition = codec.toRecognitionWave(voice.path, proof.resolve("alice-recognition.wav"))
                val transcript = engine.transcribe(recognition, "whisper-tiny", "ru").lowercase()
                assertTrue(transcript.contains("провер") && transcript.contains("голос") && transcript.contains("алис"))
                assertEquals(listOf("synthesis", "conversion", "recognition"), operations.toList())

                val report = buildJsonObject {
                    put("synthetic", true)
                    put("modelId", "rvc-alice")
                    put("engine", "applio-rvc")
                    put("runtimeVersion", "3.7.0")
                    put("cpuThreads", 2)
                    put("verifiedCachedArtifacts", true)
                    put("telegramMessagesSent", 0)
                    put("llmInferenceRequests", 0)
                    put("installationMillis", installationMillis)
                    put("synthesisMillis", synthesisMillis)
                    put("totalMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    put("sampleRate", wave.sampleRate)
                    put("durationMillis", wave.durationMillis)
                    put("audioBytes", Files.size(voice.path))
                    put("waveformNonzero", true)
                    put("convertedWaveDiffersFromBase", true)
                    put("recognizedSyntheticMeaning", true)
                    put("opusHeader", "OggS")
                }
                Files.writeString(proof.resolve("proof.json"), report.toString())
                println("Isolated RVC speech proof: ${verified.relativize(proof)}")
            } finally {
                Files.deleteIfExists(wave.path)
            }
        }
    }
}
