package ru.aigram.server

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import java.io.ByteArrayInputStream

internal class ByteArrayServletInputStream(bytes: ByteArray) : ServletInputStream() {
    private val input = ByteArrayInputStream(bytes)

    override fun read(): Int = input.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = input.read(buffer, offset, length)

    override fun isFinished(): Boolean = input.available() == 0

    override fun isReady(): Boolean = true

    override fun setReadListener(listener: ReadListener) {
        try {
            if (!isFinished) {
                listener.onDataAvailable()
            }

            if (isFinished) {
                listener.onAllDataRead()
            }
        } catch (exception: Exception) {
            listener.onError(exception)
        }
    }
}
