package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.WritingStyle
import ru.aigram.memory.MemoryContext
import ru.aigram.storage.SettingsStore
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectedStyleRequestTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `named style follows factual memory and final text is accepted without wording retries`() {
        val requests = mutableListOf<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            requests.add(Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject)
            val text = "Всё нормально, сегодня спокойно."
            val bytes = ("{\"message\":{\"content\":" + Json.encodeToString(text) + "}}").toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val settings = SettingsStore(directory)
            val style = WritingStyle("russian", "По-русски", "🙂", "Отвечай по-русски. Коротко и без официоза.")
            settings.update { it.copy(customStyles = listOf(style)) }
            val rule = settings.resolve(ChatRule(style = "custom", styleId = style.id, internetAccess = false))
            val result = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                rule,
                listOf(ChatMessage("1", "synthetic", "How are you doing today?", false, 0)),
                memory = MemoryContext(summary = "Собеседник — друг со школы"),
            )
            assertEquals("Всё нормально, сегодня спокойно.", result.text)
            assertEquals(1, requests.size)
            requests.stream().forEach { request ->
                val system = request.getValue(
                    "messages",
                ).jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content
                assertTrue(system.contains("Выбранный стиль диалога: " + style.instructions))
                assertTrue(system.indexOf("Выбранный стиль диалога:") > system.indexOf("друг со школы"))
                assertFalse(system.contains("Описание манеры в общем профиле"))
            }
        } finally {
            server.stop(0)
        }
    }
}
