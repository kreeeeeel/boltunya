package ru.aigram.speech.codec

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.sin

class SyntheticSpeechWave {
    fun write(path: Path, sampleRate: Int = 22050, seconds: Double = 1.25, channels: Int = 2): Path {
        val samples = (sampleRate * seconds).toInt()
        val audioBytes = samples * channels * Short.SIZE_BYTES
        val data = ByteBuffer.allocate(44 + audioBytes).order(ByteOrder.LITTLE_ENDIAN)
        header(data, audioBytes, sampleRate, channels)
        IntStream.range(0, samples * channels).forEach { index ->
            val frame = index / channels
            data.putShort((sin(2 * PI * 440 * frame / sampleRate) * 16000).toInt().toShort())
        }
        Files.write(path, data.array())
        return path
    }

    fun longWave(path: Path): Path {
        val audioBytes = 16000 * 601 * Short.SIZE_BYTES
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header(header, audioBytes, 16000, 1)
        Files.write(path, header.array())
        Files.newByteChannel(path, StandardOpenOption.WRITE).use { channel ->
            channel.position(44L + audioBytes - 1)
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
        }
        return path
    }

    private fun header(data: ByteBuffer, audioBytes: Int, sampleRate: Int, channels: Int) {
        data.put("RIFF".toByteArray(Charsets.US_ASCII))
        data.putInt(36 + audioBytes)
        data.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        data.putInt(16)
        data.putShort(1)
        data.putShort(channels.toShort())
        data.putInt(sampleRate)
        data.putInt(sampleRate * channels * Short.SIZE_BYTES)
        data.putShort((channels * Short.SIZE_BYTES).toShort())
        data.putShort(16)
        data.put("data".toByteArray(Charsets.US_ASCII))
        data.putInt(audioBytes)
    }
}
