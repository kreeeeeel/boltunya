package ru.aigram.component.media

import java.io.FilterInputStream
import java.io.InputStream

internal class RangedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0) {
            return -1
        }

        val value = `in`.read()
        if (value >= 0) {
            remaining -= 1
        }

        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (remaining <= 0) {
            return -1
        }

        val count = `in`.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) {
            remaining -= count
        }

        return count
    }
}
