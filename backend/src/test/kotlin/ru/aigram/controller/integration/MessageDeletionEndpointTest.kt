package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageDeletionEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `authenticates validates and rechecks deletion scope without silently revoking for everyone`() {
        val telegram = MessageDeletionTestGateway()
        val settings = SettingsStore(directory)
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
                            val builder = HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authorized) {
                                builder.header("Cookie", "tiho=session")
                            }
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        fun delete(scope: String): HttpResponse<String> = request(
                            "/api/chats/42/messages/123",
                            "DELETE",
                            """{"scope":"$scope"}""",
                        )
                        assertEquals(
                            401,
                            request("/api/chats/42/messages/123/deletion", authorized = false).statusCode(),
                        )
                        assertEquals(
                            401,
                            request("/api/chats/42/messages/123", "DELETE", """{"scope":"self"}""", false).statusCode(),
                        )
                        assertEquals(400, request("/api/chats/999/messages/123/deletion").statusCode())
                        assertEquals(400, request("/api/chats/not-a-chat/messages/123/deletion").statusCode())
                        assertEquals(
                            400,
                            request("/api/chats/999999999999999999999/messages/123/deletion").statusCode(),
                        )
                        assertEquals(400, request("/api/chats/42/messages/0/deletion").statusCode())
                        assertEquals(400, request("/api/chats/42/messages/not-a-message/deletion").statusCode())
                        assertEquals(400, delete("invalid").statusCode())
                        assertEquals(400, request("/api/chats/42/messages/123", "DELETE", "{}").statusCode())
                        val excessive = request(
                            "/api/chats/42/messages/123",
                            "DELETE",
                            "{\"scope\":\"${"x".repeat(512 * 1024)}\"}",
                        )
                        assertEquals(400, excessive.statusCode())
                        assertTrue(excessive.body().contains("REQUEST_TOO_LARGE"))
                        val options = Json.decodeFromString<MessageDeletionOptions>(
                            request("/api/chats/42/messages/123/deletion").body(),
                        )
                        assertTrue(options.canDeleteForSelf)
                        assertFalse(options.canDeleteForEveryone)
                        assertEquals(409, delete("everyone").statusCode())
                        assertTrue(telegram.deletions.isEmpty())
                        val result = delete("self")
                        assertEquals(200, result.statusCode())
                        assertEquals(
                            MessageDeletionResult("42", "123", 100),
                            Json.decodeFromString<MessageDeletionResult>(result.body()),
                        )
                        assertEquals(listOf(Triple("42", "123", MessageDeletionScope.SELF)), telegram.deletions)
                        // Permissions can change after the confirmation dialog loaded its options.
                        telegram.permissions = MessageDeletionOptions(false, true)
                        assertEquals(409, delete("self").statusCode())
                        assertEquals(1, telegram.deletions.size)
                        telegram.failure = true
                        val failed = delete("everyone")
                        assertEquals(502, failed.statusCode())
                        assertTrue(failed.body().contains("TELEGRAM_DELETE_FAILED"))
                        assertEquals(1, telegram.deletions.size)
                    }
            }
        }
    }
}
