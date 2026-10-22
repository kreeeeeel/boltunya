package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechWorkerProcessTest {
    @Test
    fun `conversion protocol sends fixed quoted bootstrap through stdin and never sends the private utterance`() {
        val worker = ApplioSpeechWorker()
        val process = SpeechTestProcess()
        val request = SpeechWorkerRequest(
            "conversion",
            "synthetic-model",
            text = "Private synthetic Unicode 👋 and \"quotes\"",
        )
        SpeechWorkerProcess(Json, worker).writeRequest(process, request)
        val input = process.input.toString(Charsets.UTF_8)

        assertEquals(worker.conversionInput().toString(Charsets.UTF_8), input)
        assertTrue(input.contains("command[\"cli\"].main(args=sys.argv[2:], standalone_mode=False)"))
        assertTrue(input.contains("os.environ[\"AIGRAM_SPEECH_COMPUTE_DEVICE\"]"))
        assertFalse(input.contains(request.text))
        assertFalse(input.contains(request.modelDirectory))
    }

    @Test
    fun `nonconversion workers retain lossless UTF8 JSON stdin instead of receiving a Python bootstrap`() {
        val process = SpeechTestProcess()
        val request = SpeechWorkerRequest(
            "synthesis",
            "synthetic-model",
            text = "  Synthetic 👋\n\"quoted\" text.  ",
        )
        SpeechWorkerProcess(Json, ApplioSpeechWorker()).writeRequest(process, request)

        assertEquals(request, Json.decodeFromString<SpeechWorkerRequest>(process.input.toString(Charsets.UTF_8)))
        assertFalse(process.input.toString(Charsets.UTF_8).contains("runpy"))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `application shutdown stops its active metadata process and rejects new launches`() {
        val worker = SpeechWorkerProcess(Json, ApplioSpeechWorker())
        val entered = CountDownLatch(1)
        val owned = AtomicReference<Process>()
        val executor = Executors.newSingleThreadExecutor()
        val builder = ProcessBuilder(
            Path.of(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe").toString(),
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            "Start-Sleep -Seconds 60",
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
        try {
            val running = executor.submit<Boolean> {
                worker.run(builder) {
                    owned.set(it)
                    entered.countDown()
                    it.waitFor(10, TimeUnit.SECONDS)
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.close()
            assertTrue(running.get(5, TimeUnit.SECONDS))
            assertFalse(owned.get().isAlive)
            assertFailsWith<InterruptedException> {
                worker.run(builder) { throw AssertionError("Closed worker started") }
            }
            worker.close()
        } finally {
            worker.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

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
