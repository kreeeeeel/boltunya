package ru.aigram.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class ClientTestServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val bodies = ConcurrentHashMap<String, String>()
    private val binaryBodies = ConcurrentHashMap<String, ByteArray>()
    private val contentTypes = ConcurrentHashMap<String, String>()
    private val statuses = ConcurrentHashMap<String, Int>()
    val requests = CopyOnWriteArrayList<RecordedClientRequest>()
    val baseUri: URI get() = URI.create("http://127.0.0.1:${server.address.port}")

    fun response(path: String, body: String, status: Int = 200) {
        bodies[path] = body
        binaryBodies.remove(path)
        contentTypes[path] = "application/json"
        statuses[path] = status
    }

    fun binaryResponse(path: String, body: ByteArray) {
        binaryBodies[path] = body.copyOf()
        contentTypes[path] = "application/octet-stream"
        statuses[path] = 200
    }

    fun start(): ClientTestServer {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                val path = exchange.requestURI.path
                val incoming = exchange.requestBody.readAllBytes()
                requests.add(
                    RecordedClientRequest(
                        exchange.requestMethod,
                        path,
                        exchange.requestURI.rawQuery,
                        exchange.requestHeaders.getFirst("Cookie").orEmpty(),
                        incoming.toString(Charsets.UTF_8),
                        incoming,
                        exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
                        exchange.requestHeaders.getFirst("X-File-Name").orEmpty(),
                    ),
                )
                val body = bodies[path] ?: """{"ok":true}"""
                val bytes = binaryBodies[path] ?: body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", contentTypes[path] ?: "application/json")
                exchange.sendResponseHeaders(statuses[path] ?: 200, bytes.size.toLong())
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
