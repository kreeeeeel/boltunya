package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechWorkerProcessTest {
    @Test
    fun `worker launch exposes no text and discards all native output`() {
        val process = SpeechWorkerProcess(Json, ApplioSpeechWorker()).builder(
            Path.of("C:/synthetic/native.jar"),
            Path.of("C:/synthetic/result.json"),
        )

        assertTrue(process.command().contains("-Xmx512m"))
        assertTrue(process.command().contains("ru.aigram.speech.SpeechWorkerMainKt"))
        assertFalse(process.command().contains("--text"))
        assertEquals(ProcessBuilder.Redirect.DISCARD, process.redirectError())
        assertEquals(ProcessBuilder.Redirect.DISCARD, process.redirectOutput())
        assertEquals(ProcessBuilder.Redirect.PIPE, process.redirectInput())
    }

    @Test
    fun `native extraction uses the absolute caller-owned result directory`() {
        val result = Path.of("build", "synthetic", "worker", "result.json")
        val process = SpeechWorkerProcess(Json, ApplioSpeechWorker()).builder(
            Path.of("build", "synthetic", "native.jar"),
            result,
        )
        val absoluteResult = result.toAbsolutePath().normalize()
        val temporaryDirectory = process.command().stream()
            .filter { it.startsWith("-Djava.io.tmpdir=") }
            .findFirst().orElse(null)
        val expectedDirectory = "-Djava.io.tmpdir=${absoluteResult.parent}"

        assertEquals(expectedDirectory, temporaryDirectory)
        assertEquals(absoluteResult.toString(), process.command().last())
        assertTrue(process.command().indexOf(expectedDirectory) < process.command().indexOf("-cp"))
    }

    @Test
    fun `cancellation waits for the owned worker before caller file cleanup and preserves interrupt state`() {
        val worker = SpeechTestProcess()
        try {
            Thread.currentThread().interrupt()
            SpeechWorkerProcess(Json, ApplioSpeechWorker()).stop(worker)

            assertTrue(worker.terminated)
            assertTrue(worker.awaited)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
