package ru.aigram.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult
import ru.aigram.memory.MemoryDocument
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextExclusionEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `requires authentication scopes exclusions to the account and rebuilds facts while preserving manual notes`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages = (1..7).map { ChatMessage("$it", "42", "Original message $it", false, it.toLong()) }
        val bank = MemoryStore(directory, ResourceCatalog())
        bank.activate("100")
        bank.observe("42", 7, manual = true)
        val state = bank.state("42") ?: error("Missing memory state")
        assertTrue(
            bank.commit(
                state,
                bank.read("42").documentRevision,
                "Previously extracted fact",
                state.copy(headId = 7, ownerMemoryVersion = 1),
            ),
        )
        bank.edit("42", "Manual chat note", bank.read("42").revision)
        bank.edit("", "Manual owner note", bank.read("").revision)
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, telegram, engine, ai, replies, "session") {}
                    .use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            path: String,
                            method: String = "GET",
                            body: String = "",
                            authorized: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(
                                URI.create(base + path),
                            ).method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authorized) builder.header("Cookie", "tiho=session")
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        fun history(): List<ChatMessage> =
                            Json.decodeFromString(request("/api/chats/42/messages").body())
                        fun excluded(): List<String> = history().filter { it.contextExcluded }.map { it.id }
                        fun update(id: String, scope: String, excluded: Boolean = true): HttpResponse<String> = request(
                            "/api/chats/42/messages/$id/context",
                            "PUT",
                            """{"scope":"$scope","excluded":$excluded}""",
                        )
                        assertEquals(401, request("/api/chats/42/messages/4/context", "PUT", "{}", false).statusCode())
                        assertEquals(
                            401,
                            request("/api/chats/42/context-exclusions", "DELETE", authorized = false).statusCode(),
                        )
                        assertEquals(400, update("0", "message").statusCode())
                        assertEquals(400, update("4", "invalid").statusCode())
                        assertEquals(400, request("/api/chats/999/messages/4/context", "PUT", "{}").statusCode())
                        assertEquals(200, update("4", "through").statusCode())
                        assertEquals(listOf("1", "2", "3", "4"), excluded())
                        assertEquals(telegram.messages.map { it.text }, history().map { it.text })
                        val document = Json.decodeFromString<MemoryDocument>(request("/api/memory/42").body())
                        assertEquals("Manual chat note", document.notes)
                        assertEquals("", document.summary)
                        assertEquals(
                            "Manual owner note",
                            Json.decodeFromString<MemoryDocument>(request("/api/memory").body()).notes,
                        )
                        assertEquals(200, update("3", "message", false).statusCode())
                        assertEquals(listOf("1", "2", "4"), excluded())
                        assertEquals(200, update("6", "from").statusCode())
                        assertEquals(200, update("2", "through", false).statusCode())
                        assertEquals(200, update("7", "from", false).statusCode())
                        assertEquals(listOf("4", "6"), excluded())
                        telegram.ownerId = "101"
                        assertTrue(excluded().isEmpty())
                        assertEquals(200, update("7", "message").statusCode())
                        assertEquals(listOf("7"), excluded())
                        telegram.ownerId = "100"
                        assertEquals(listOf("4", "6"), excluded())
                        assertEquals(200, request("/api/chats/42/context-exclusions", "DELETE").statusCode())
                        assertTrue(excluded().isEmpty())
                        val saved = Files.readString(directory.resolve("context-exclusions/100/42.json"))
                        assertFalse(saved.contains("Original message"))
                        assertFalse(saved.contains("Previously extracted fact"))
                    }
            }
        }
    }
}
