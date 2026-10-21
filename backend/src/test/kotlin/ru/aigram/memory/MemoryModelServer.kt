package ru.aigram.memory

import com.sun.net.httpserver.HttpServer
import ru.aigram.ai.AiTestServices
import java.net.InetSocketAddress

class MemoryModelServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    fun start(): AiTestServices {
        server.createContext("/api/tags") { exchange ->
            val bytes = """{"models":[{"name":"gemma3:4b"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return AiTestServices("http://127.0.0.1:${server.address.port}")
    }
    override fun close() {
        server.stop(0)
    }
}
