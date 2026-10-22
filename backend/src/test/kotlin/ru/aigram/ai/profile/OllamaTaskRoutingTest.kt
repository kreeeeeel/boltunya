package ru.aigram.ai.profile

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ModelTaskRouting
import ru.aigram.domain.chat.ChatRule
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaTaskRoutingTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `each native task reaches its selected Ollama model and vision includes the actual image bytes`() {
        val requests = ConcurrentLinkedQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            exchange.use {
                requests.add(Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject)
                val response = """{"message":{"content":"A photo"},"done":true,"done_reason":"stop"}"""
                val bytes = response.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
        try {
            ProfileAiTestServices(directory, "http://127.0.0.1:${server.address.port}").use { services ->
                services.routing.save(
                    ModelTaskRouting(
                        enabled = true,
                        replyModel = "qwen3:1.7b",
                        memoryModel = "gemma3:4b",
                        visionModel = "qwen3-vl:2b",
                    ),
                )
                val image = byteArrayOf(1, 2, 3)
                ModelTask.entries.stream().forEach { task ->
                    val message = if (task == ModelTask.VISION) {
                        UserMessage.builder().text("Describe this image")
                            .media(Media(MimeTypeUtils.IMAGE_PNG, ByteArrayResource(image))).build()
                    } else {
                        UserMessage("A synthetic question")
                    }
                    assertEquals(
                        "A photo",
                        services.completion.generate(ChatRule(), listOf(message), task = task).text,
                    )
                }
                assertEquals(
                    listOf("qwen3:1.7b", "gemma3:4b", "qwen3-vl:2b"),
                    requests.stream().map { it["model"]?.jsonPrimitive?.content }.toList(),
                )
                val vision = assertNotNull(
                    requests.stream().filter {
                        it["model"]?.jsonPrimitive?.content == "qwen3-vl:2b"
                    }.findFirst().orElse(null),
                )
                val messages = assertNotNull(vision["messages"]).jsonArray
                val user = messages.last().jsonObject
                assertEquals("user", user["role"]?.jsonPrimitive?.content)
                assertEquals("Describe this image", user["content"]?.jsonPrimitive?.content)
                assertEquals(
                    Base64.getEncoder().encodeToString(image),
                    assertNotNull(user["images"]).jsonArray.single().jsonPrimitive.content,
                )
                assertTrue(services.targets.resolve(ModelTask.VISION, ChatRule()).capabilities.vision)
                assertEquals("", services.targets.resolve(ModelTask.VISION, ChatRule()).profileId)
            }
        } finally {
            server.stop(0)
        }
    }
}
