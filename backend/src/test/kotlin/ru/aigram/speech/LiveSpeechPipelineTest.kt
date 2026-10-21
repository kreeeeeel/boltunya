package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.aigram.speech.codec.AudioCodec
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class LiveSpeechPipelineTest {
    @Test
    fun `verified local assets produce an opus voice that recognition transcribes through the isolated worker`() {
        assumeTrue(System.getenv("AIGRAM_LIVE_SPEECH") == "true")
        val verified = Path.of("..", "build", "speech-verification").toAbsolutePath().normalize()
        assumeTrue(Files.isDirectory(verified))
        val directory = Files.createTempDirectory(verified, "pipeline-")
        try {
            val assets = listOf("sherpa-runtime", "piper-ru-irina", "whisper-tiny")
            verifiedSpeechCatalog(directory, verified, assets).use { catalog ->
                val engine = SpeechEngine(directory, catalog, SpeechWorkerProcess(Json))
                val codec = AudioCodec(directory)
                val text = "Привет! Это проверка голосовых сообщений. Сегодня хорошая погода."
                val wave = engine.synthesize(text, "piper-ru-irina")
                try {
                    val voice = codec.toVoiceNote(wave.path, directory.resolve("synthetic.ogg"))
                    assertTrue(voice.duration in 1..30)
                    assertTrue(voice.waveform.isNotEmpty())
                    val recognition = codec.toRecognitionWave(voice.path, directory.resolve("recognition.wav"))
                    val transcript = engine.transcribe(recognition, "whisper-tiny", "ru")
                    assertContentEquals(
                        text.codePoints().filter(Character::isLetterOrDigit).map(Character::toLowerCase).toArray(),
                        transcript.codePoints().filter(
                            Character::isLetterOrDigit,
                        ).map(Character::toLowerCase).toArray(),
                        "Recognition must preserve all spoken words; punctuation is inferred by Whisper.",
                    )
                } finally {
                    Files.deleteIfExists(wave.path)
                }
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
