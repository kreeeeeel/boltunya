package ru.boltunya.ai.profile

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OpenAiStubServer(private val response: String, private val streaming: Boolean = true) : AutoCloseable {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = AtomicInteger()
    val body = AtomicReference<JsonObject>()
    val authorization = AtomicReference("")
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v1"

    fun start(): OpenAiStubServer {
        server.executor = executor
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.use {
                requests.incrementAndGet()
                body.set(Json.decodeFromString(exchange.requestBody.bufferedReader().readText()))
                authorization.set(exchange.requestHeaders.getFirst("Authorization").orEmpty())
                exchange.responseHeaders.set("Content-Type", if (streaming) "text/event-stream" else "application/json")
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
        return this
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}
