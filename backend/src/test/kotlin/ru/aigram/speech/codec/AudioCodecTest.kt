package ru.aigram.speech.codec

import com.sun.net.httpserver.HttpServer
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.InvalidSpeechAudioException
import ru.aigram.exception.impl.SpeechAudioLimitException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class AudioCodecTest {
    @TempDir lateinit var directory: Path
    private val codec = AudioCodec(Path.of("../build/speech-verification/codec/runtime-profile").toAbsolutePath())
    private val waves = SyntheticSpeechWave()

    @Test
    fun `real native round trip converts stereo WAV to mono Opus and sixteen kilohertz recognition WAV`() {
        val input = waves.write(directory.resolve("synthetic stereo.wav"))
        val original = Files.readAllBytes(input)
        val voice = codec.toVoiceNote(input, directory.resolve("voice.ogg"))
        val recognition = codec.toRecognitionWave(voice.path, directory.resolve("recognition.wav"))

        assertEquals(2, voice.duration)
        assertEquals(63, voice.waveform.size)
        assertTrue(voice.waveform.any { it != 0.toByte() })
        assertEquals(0, voice.waveform.last().toInt() and 0xf0)
        FFmpegFrameGrabber(voice.path.toFile()).use { grabber ->
            grabber.start()
            assertEquals(AV_CODEC_ID_OPUS, grabber.audioCodec)
            assertEquals(1, grabber.audioChannels)
            assertEquals(48000, grabber.sampleRate)
        }
        FFmpegFrameGrabber(recognition.toFile()).use { grabber ->
            grabber.start()
            assertEquals(AV_CODEC_ID_PCM_S16LE, grabber.audioCodec)
            assertEquals(1, grabber.audioChannels)
            assertEquals(16000, grabber.sampleRate)
            assertTrue(grabber.lengthInTime in 1_150_000L..1_350_000L)
            assertTrue(grabber.grabSamples().samples.isNotEmpty())
        }
        assertContentEquals(original, Files.readAllBytes(input))
        assertNoPartialFiles()
    }

    @Test
    fun `rejects oversized and excessive duration files without publishing a partial voice`() {
        val output = directory.resolve("voice.ogg")
        val oversized = directory.resolve("oversized.wav")
        Files.newByteChannel(oversized, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            channel.position(25L * 1024 * 1024)
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
        }
        assertFailsWith<SpeechAudioLimitException> { codec.toVoiceNote(oversized, output) }
        assertFailsWith<SpeechAudioLimitException> {
            codec.toVoiceNote(waves.longWave(directory.resolve("too long.wav")), output)
        }
        assertFalse(Files.exists(output))
        assertNoPartialFiles()
    }

    @Test
    fun `rejects corrupt and playlist inputs without network access or overwriting an existing result`() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        server.start()
        try {
            val source = directory.resolve("untrusted.ogg")
            Files.writeString(source, "#EXTM3U\nhttp://127.0.0.1:${server.address.port}/private\n")
            val output = directory.resolve("result.wav")
            Files.writeString(output, "previous result")
            assertFailsWith<InvalidSpeechAudioException> { codec.toRecognitionWave(source, output) }
            assertEquals("previous result", Files.readString(output))
            assertEquals(0, requests.get())
            assertFailsWith<InvalidSpeechAudioException> { codec.toRecognitionWave(source, source) }
            assertNoPartialFiles()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `cancellation preserves interruption and never creates the requested output`() {
        val output = directory.resolve("cancelled.ogg")
        val input = waves.write(directory.resolve("synthetic.wav"))
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<CancellationException> { codec.toVoiceNote(input, output) }
            assertTrue(Thread.currentThread().isInterrupted)
            assertFalse(Files.exists(output))
        } finally {
            Thread.interrupted()
        }
    }

    private fun assertNoPartialFiles() {
        Files.list(directory).use { files ->
            assertFalse(files.anyMatch { it.fileName.toString().endsWith(".part") })
        }
    }
}
