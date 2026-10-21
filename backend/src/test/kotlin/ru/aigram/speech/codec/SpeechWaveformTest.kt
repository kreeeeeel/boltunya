package ru.aigram.speech.codec

import org.junit.jupiter.api.Test
import java.nio.ShortBuffer
import java.util.stream.IntStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechWaveformTest {
    @Test
    fun `packs one hundred normalized five bit amplitudes without changing sample buffer positions`() {
        val samples = ShortBuffer.wrap(ShortArray(480) { Short.MIN_VALUE })
        samples.position(10)
        val waveform = SpeechWaveform(48000)
        waveform.observe(samples)
        val packed = waveform.packed()

        assertEquals(10, samples.position())
        assertEquals(63, packed.size)
        assertEquals(0, packed.last().toInt() and 0xf0)
        assertTrue(unpack(packed).all { it == 31 })
        assertContentEquals(packed, waveform.packed())
    }

    @Test
    fun `silence and empty audio have a stable zero waveform`() {
        val waveform = SpeechWaveform(16000)
        assertContentEquals(ByteArray(63), waveform.packed())
        waveform.observe(ShortBuffer.wrap(ShortArray(1600)))
        assertContentEquals(ByteArray(63), waveform.packed())
    }

    @Test
    fun `keeps separate quiet and loud windows when records arrive in multiple frames`() {
        val waveform = SpeechWaveform(100)
        waveform.observe(ShortBuffer.wrap(shortArrayOf(0, 0, 0, 0, 0)))
        waveform.observe(ShortBuffer.wrap(shortArrayOf(1000, 1000, 1000, 1000, 1000)))
        val values = unpack(waveform.packed())
        assertTrue(values.take(50).all { it == 0 })
        assertTrue(values.drop(50).all { it == 31 })
    }

    private fun unpack(packed: ByteArray): List<Int> = IntStream.range(0, 100).map { point ->
        val offset = point * 5
        val index = offset / 8
        val shift = offset % 8
        val low = packed[index].toInt() and 0xff
        val high = if (index + 1 < packed.size) {
            (packed[index + 1].toInt() and 0xff) shl 8
        } else {
            0
        }
        ((low or high) ushr shift) and 31
    }.boxed().toList()
}
