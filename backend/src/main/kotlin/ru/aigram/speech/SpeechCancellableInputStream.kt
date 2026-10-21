package ru.aigram.speech

import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CancellationException

/** Checks cancellation below the archive buffer, including reads performed by tar entry skipping. */
class SpeechCancellableInputStream(input: InputStream) : FilterInputStream(input) {
    override fun read(): Int {
        interrupted()
        return `in`.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        interrupted()
        return `in`.read(buffer, offset, length)
    }

    private fun interrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException()
        }
    }
}
