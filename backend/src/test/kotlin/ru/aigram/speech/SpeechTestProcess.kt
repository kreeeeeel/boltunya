package ru.aigram.speech

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class SpeechTestProcess : Process() {
    var terminated = false
    var awaited = false

    override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf())

    override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())

    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

    override fun waitFor(): Int {
        awaited = true
        return 0
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        awaited = true
        return terminated && timeout <= 5 && unit == TimeUnit.SECONDS
    }

    override fun exitValue(): Int = 0

    override fun destroy() {
        terminated = true
    }

    override fun destroyForcibly(): Process {
        destroy()
        return this
    }

    override fun isAlive(): Boolean = !terminated

    override fun descendants(): Stream<ProcessHandle> = Stream.empty()
}
