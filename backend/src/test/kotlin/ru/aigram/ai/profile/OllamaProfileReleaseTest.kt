package ru.aigram.ai.profile

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.domain.ai.profile.ModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.chat.ChatRule
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaProfileReleaseTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `unloads profile models only from the application local Ollama endpoint`() {
        val localUnloads = ConcurrentLinkedQueue<String>()
        val foreignUnloads = ConcurrentLinkedQueue<String>()
        val local = server(localUnloads)
        val foreign = server(foreignUnloads)
        try {
            val localUrl = "http://127.0.0.1:${local.address.port}"
            ProfileAiTestServices(directory, localUrl).use { services ->
                listOf(localUrl to "local:model", "http://127.0.0.1:${foreign.address.port}" to "foreign:model")
                    .stream().forEach { (url, model) ->
                        val profile = services.profiles.save(
                            null,
                            ModelProfileUpdate("Synthetic", ModelProvider.OLLAMA, baseUrl = url, model = model),
                        )
                        val result = services.completion.generate(
                            ChatRule(modelProfileId = profile.id, useDefaultModel = false),
                            listOf(UserMessage("Synthetic prompt")),
                        )
                        assertEquals("Synthetic answer", result.text)
                    }
                services.runtime.releaseModels()
                assertEquals(listOf("local:model"), localUnloads.toList())
                assertTrue(foreignUnloads.isEmpty())
                services.runtime.releaseModels()
                assertEquals(1, localUnloads.size)
            }
        } finally {
            local.stop(0)
            foreign.stop(0)
        }
    }

    private fun server(unloads: ConcurrentLinkedQueue<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            exchange.use {
                exchange.requestBody.readAllBytes()
                val bytes = """{"message":{"content":"Synthetic answer"},"done":true,"done_reason":"stop"}"""
                    .toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.createContext("/api/generate") { exchange ->
            exchange.use {
                val body = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                assertEquals("0", body["keep_alive"]?.jsonPrimitive?.content)
                unloads.add(body["model"]?.jsonPrimitive?.content.orEmpty())
                val bytes = "{}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
        return server
    }
}
