package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EngineWarmTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `model warmup uses the shared foreground queue and interrupts background summarization`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        val warmed = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/tags") { exchange ->
            val bytes = """{"models":[{"name":"qwen3:0.6b"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/generate") { exchange ->
            exchange.requestBody.readAllBytes()
            val bytes = "{}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            warmed.countDown()
        }
        server.start()
        val engine = EngineManager(
            directory,
            OllamaRuntime("http://127.0.0.1:${server.address.port}"),
            inference = inference,
        )
        try {
            Executors.newSingleThreadExecutor().use { worker ->
                val background = worker.submit<Boolean> {
                    assertFailsWith<BackgroundInferenceDeferredException> {
                        inference.background {
                            started.countDown()
                            CountDownLatch(1).await(5, TimeUnit.SECONDS)
                        }
                    }
                    true
                }
                assertTrue(started.await(2, TimeUnit.SECONDS))
                engine.warm("qwen3:0.6b")
                assertTrue(background.get(2, TimeUnit.SECONDS))
                assertTrue(warmed.await(2, TimeUnit.SECONDS))
            }
        } finally {
            engine.close()
            server.stop(0)
        }
    }
}
