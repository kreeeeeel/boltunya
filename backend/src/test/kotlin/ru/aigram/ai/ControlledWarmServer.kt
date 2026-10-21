package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val WARM_GATE_TIMEOUT_SECONDS = 5L

class ControlledWarmServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val started = ConcurrentHashMap<String, CountDownLatch>()
    private val releases = ConcurrentHashMap<String, CountDownLatch>()
    private val codes = ConcurrentHashMap<String, Int>()
    private val requests = ConcurrentHashMap<String, AtomicInteger>()
    val runtime: OllamaRuntime get() = OllamaRuntime("http://127.0.0.1:${server.address.port}")

    fun start(): ControlledWarmServer {
        server.executor = executor
        server.createContext("/api/tags") { exchange ->
            exchange.use {
                val bytes = """{"models":[{"name":"qwen3:1.7b"},{"name":"gemma3:4b"}]}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.createContext("/api/generate") { exchange ->
            exchange.use {
                val body = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                val model = body["model"]?.jsonPrimitive?.content.orEmpty()
                val warmup = body["keep_alive"]?.jsonPrimitive?.content != "0"
                val code = if (warmup) {
                    requests.computeIfAbsent(model) { AtomicInteger() }.incrementAndGet()
                    val gate = releases.computeIfAbsent(model) { CountDownLatch(1) }
                    started.computeIfAbsent(model) { CountDownLatch(1) }.countDown()
                    if (gate.await(WARM_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) codes[model] ?: 200 else 504
                } else {
                    200
                }
                val response = if (code == 200) "{}" else """{"error":"synthetic warmup failure"}"""
                val bytes = response.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
        return this
    }

    fun awaitWarm(model: String): Boolean = started.computeIfAbsent(model) { CountDownLatch(1) }
        .await(WARM_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    fun release(model: String, code: Int = 200) {
        codes[model] = code
        releases.computeIfAbsent(model) { CountDownLatch(1) }.countDown()
    }

    fun count(model: String): Int = requests[model]?.get() ?: 0

    override fun close() {
        releases.values.stream().forEach { it.countDown() }
        server.stop(0)
        executor.shutdownNow()
    }
}
