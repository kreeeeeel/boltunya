package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineDownloadTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `cancelling a streamed model pull stops progress and allows a subsequent download`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val workers = Executors.newCachedThreadPool()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        server.executor = workers
        server.createContext("/api/tags") { exchange ->
            val bytes = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/pull") { exchange ->
            exchange.requestBody.readAllBytes()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("""{"total":100,"completed":10}""".plus("\n").toByteArray())
                output.flush()
                started.countDown()
                finish.await(5, TimeUnit.SECONDS)
            }
        }
        server.start()
        val engine = EngineManager(directory, OllamaRuntime("http://127.0.0.1:${server.address.port}"))
        try {
            engine.pull("qwen3:0.6b")
            assertTrue(started.await(3, TimeUnit.SECONDS))
            engine.cancelDownload()
            val completed = generateSequence { engine.status() }.onEach { Thread.sleep(20) }.take(250)
                .firstOrNull { it.downloading.isEmpty() }
            assertTrue(completed != null)
            assertEquals("", completed.error)
            engine.pull("qwen3:1.7b")
            engine.cancelDownload()
        } finally {
            finish.countDown()
            engine.close()
            server.stop(0)
            workers.shutdownNow()
        }
    }
}
