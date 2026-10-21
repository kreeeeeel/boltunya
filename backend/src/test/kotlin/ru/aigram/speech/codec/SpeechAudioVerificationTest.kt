package ru.aigram.speech.codec

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledIfSystemProperty(named = "aigram.speech.verification", matches = ".+")
class SpeechAudioVerificationTest {
    @Test
    fun `real synthesized Russian speech remains audible Opus and recognition PCM after transcoding`() {
        val root = Path.of(System.getProperty("aigram.speech.verification")).toAbsolutePath().normalize()
        val output = root.resolve("codec")
        Files.createDirectories(output)
        val codec = AudioCodec(output.resolve("runtime-profile"))
        val started = System.nanoTime()
        val voice = codec.toVoiceNote(root.resolve("synthetic-ru.wav"), output.resolve("synthetic-ru.ogg"))
        val recognition = codec.toRecognitionWave(voice.path, output.resolve("synthetic-ru-recognition.wav"))

        assertTrue(voice.duration in 4..6)
        assertTrue(Files.size(voice.path) in 1L..(25L * 1024 * 1024))
        assertEquals(63, voice.waveform.size)
        assertTrue(voice.waveform.any { it != 0.toByte() })
        FFmpegFrameGrabber(voice.path.toFile()).use { grabber ->
            grabber.start()
            assertEquals(AV_CODEC_ID_OPUS, grabber.audioCodec)
            assertEquals(1, grabber.audioChannels)
            assertEquals(48000, grabber.sampleRate)
        }
        val milliseconds = FFmpegFrameGrabber(recognition.toFile()).use { grabber ->
            grabber.start()
            assertEquals(AV_CODEC_ID_PCM_S16LE, grabber.audioCodec)
            assertEquals(1, grabber.audioChannels)
            assertEquals(16000, grabber.sampleRate)
            val duration = grabber.lengthInTime / 1000
            assertTrue(duration in 4000..6000)
            assertTrue(hasSignal(grabber))
            duration
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val metrics = buildJsonObject {
            put("synthetic", true)
            put("codecElapsedMs", elapsed)
            put("voiceDurationSeconds", voice.duration)
            put("recognitionDurationMs", milliseconds)
            put("waveformBytes", voice.waveform.size)
            put("opusBytes", Files.size(voice.path))
        }
        Files.writeString(output.resolve("metrics.json"), metrics.toString())
    }

    private fun hasSignal(grabber: FFmpegFrameGrabber): Boolean = Stream.generate { grabber.grabSamples() }
        .takeWhile { it != null }.anyMatch { frame ->
            val samples = frame?.samples?.firstOrNull() as? ShortBuffer
            samples != null && IntStream.range(samples.position(), samples.limit())
                .anyMatch { abs(samples.get(it).toInt()) > 100 }
        }
}
