package ru.aigram.media

import ru.aigram.exception.impl.MediaTooLargeException
import java.io.FilterInputStream
import java.io.InputStream

internal class UploadInputStream(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    private var received = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val amount = super.read(buffer, offset, length)
        if (amount > 0) count(amount)
        return amount
    }

    private fun count(amount: Int) {
        received += amount
        if (received > maximum) throw MediaTooLargeException()
    }
}
