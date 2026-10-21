package ru.aigram.speech.codec

import java.nio.ShortBuffer
import java.util.stream.IntStream
import kotlin.math.abs

private const val WAVEFORM_POINTS = 100
private const val WAVEFORM_BITS = 5
private const val WAVEFORM_MAX = 31
private const val WINDOWS_PER_SECOND = 100

class SpeechWaveform(sampleRate: Int) {
    private val windowSize = (sampleRate / WINDOWS_PER_SECOND).coerceAtLeast(1)
    private val peaks = ArrayList<Int>()
    private var pendingSamples = 0
    private var pendingPeak = 0

    fun observe(samples: ShortBuffer) {
        val position = samples.position()
        IntStream.range(0, samples.remaining()).forEach { index ->
            pendingPeak = maxOf(pendingPeak, abs(samples.get(position + index).toInt()))
            pendingSamples++
            if (pendingSamples == windowSize) {
                peaks.add(pendingPeak)
                pendingSamples = 0
                pendingPeak = 0
            }
        }
    }

    fun packed(): ByteArray {
        val values = ArrayList(peaks)
        if (pendingSamples > 0) {
            values.add(pendingPeak)
        }
        val maximum = values.stream().mapToInt { it }.max().orElse(0).coerceAtLeast(1)
        val packed = ByteArray((WAVEFORM_POINTS * WAVEFORM_BITS + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS)
        if (values.isEmpty()) {
            return packed
        }
        IntStream.range(0, WAVEFORM_POINTS).forEach { point ->
            val start = point * values.size / WAVEFORM_POINTS
            val end = maxOf(start + 1, (point + 1) * values.size / WAVEFORM_POINTS).coerceAtMost(values.size)
            val peak = IntStream.range(start, end).map(values::get).max().orElse(0)
            val value = (peak.toLong() * WAVEFORM_MAX / maximum).toInt()
            val offset = point * WAVEFORM_BITS
            val index = offset / Byte.SIZE_BITS
            val shift = offset % Byte.SIZE_BITS
            packed[index] = (packed[index].toInt() or (value shl shift)).toByte()
            if (shift + WAVEFORM_BITS > Byte.SIZE_BITS) {
                packed[index + 1] = (packed[index + 1].toInt() or (value ushr (Byte.SIZE_BITS - shift))).toByte()
            }
        }
        return packed
    }
}
