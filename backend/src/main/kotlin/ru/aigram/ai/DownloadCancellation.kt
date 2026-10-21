package ru.aigram.ai

import java.io.Closeable
import java.util.concurrent.CancellationException

class DownloadCancellation {
    @Volatile private var cancelled = false

    @Volatile private var worker: Thread? = null
    private var stream: Closeable? = null

    fun begin() {
        worker = Thread.currentThread()
        check()
    }

    fun check() {
        if (cancelled || Thread.currentThread().isInterrupted) {
            throw CancellationException()
        }
    }

    @Synchronized
    fun attach(value: Closeable) {
        if (cancelled) {
            value.close()
            throw CancellationException()
        }
        stream = value
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        worker?.interrupt()
        stream?.close()
        stream = null
    }

    fun isCancelled(): Boolean = cancelled
}
