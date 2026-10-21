package ru.aigram.configuration

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DiNetworkProbe : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    val requests = AtomicInteger()
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun start(): DiNetworkProbe {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                requests.incrementAndGet()
                val body = """{"error":"No model or runtime requests are expected during DI bootstrap"}""".toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(503, body.size.toLong())
                exchange.responseBody.write(body)
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
