package ru.aigram.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessagesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `history marks AI locally and manual send replies to the incoming forum message`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        gateway.messages = listOf(
            ChatMessage("10", "42", "Вопрос", false, 1, topicId = 77),
            ChatMessage("11", "42", "Ответ без служебного суффикса", true, 2),
        )
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "11")
        val ollama = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ollama.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { coordinator ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    gateway,
                    engine,
                    ollama,
                    coordinator,
                    "session",
                    automated,
                ) {
                }.use { server ->
                    val endpoint = URI.create(server.start().substringBefore("/?") + "/api/chats/42/messages")
                    val http = HttpClient.newHttpClient()
                    val request = HttpRequest.newBuilder(endpoint).header("Cookie", "tiho=session").build()
                    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                    assertEquals(200, response.statusCode())
                    val history = Json.decodeFromString<List<ChatMessage>>(response.body())
                    assertFalse(history.first().automated)
                    assertTrue(history.last().automated)
                    assertEquals("Ответ без служебного суффикса", history.last().text)
                    val send = HttpRequest.newBuilder(endpoint).header("Cookie", "tiho=session")
                        .POST(HttpRequest.BodyPublishers.ofString("""{"text":"Ручной ответ"}""")).build()
                    assertEquals(200, http.send(send, HttpResponse.BodyHandlers.ofString()).statusCode())
                    assertEquals(listOf("Ручной ответ"), gateway.sent)
                    assertEquals(listOf("10"), gateway.replyIds)
                    assertEquals(listOf(77), gateway.topicIds)
                }
            }
        }
    }
}
