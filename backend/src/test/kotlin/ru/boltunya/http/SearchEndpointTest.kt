package ru.boltunya.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.research.ExaMcpSearchClient
import ru.boltunya.ai.research.ExaResultParser
import ru.boltunya.ai.research.PublicPageReader
import ru.boltunya.ai.research.TestSearchMcpServer
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.domain.SourceLinks
import ru.boltunya.memory.MemoryTelegramGateway
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SearchCredentialStore
import ru.boltunya.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects search key endpoints never echoes the key and persists per-chat citation modes`() {
        val settings = SettingsStore(directory)
        val gateway = MemoryTelegramGateway()
        val ollama = AiTestServices("http://127.0.0.1:1")
        TestSearchMcpServer().start().use { mcp ->
            val provider = ExaMcpSearchClient(
                SearchCredentialStore(directory),
                ExaResultParser(ResourceCatalog(), PublicPageReader()),
                mcp.origin,
                "/mcp",
            )
            EngineManager(directory, ollama.runtime).use { engine ->
                ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                    TestWebServerFactory().create(
                        directory,
                        settings,
                        gateway,
                        engine,
                        ollama,
                        replies,
                        "session",
                        search = provider,
                    ) {
                    }.use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            path: String,
                            method: String = "GET",
                            body: String = "",
                            authenticated: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authenticated) builder.header("Cookie", "tiho=session")
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        assertEquals(401, request("/api/search/connection", authenticated = false).statusCode())
                        assertEquals(401, request("/api/search/check", "POST", authenticated = false).statusCode())
                        assertEquals(
                            400,
                            request("/api/search/connection", "POST", """{"apiKey":"short"}""").statusCode(),
                        )
                        val saved = request("/api/search/connection", "POST", """{"apiKey":"fake-exa-key-123"}""")
                        assertEquals(200, saved.statusCode())
                        assertTrue(saved.body().contains("true"))
                        assertFalse(request("/api/search/connection").body().contains("fake-exa-key-123"))
                        mcp.limited = true
                        assertTrue(request("/api/search/check", "POST").body().contains("limited"))
                        assertEquals("fake-exa-key-123", mcp.key)
                        assertEquals(200, request("/api/search/connection", "DELETE").statusCode())
                        assertTrue(request("/api/search/connection").body().contains("false"))
                        val rule = Json.encodeToString(ChatRule(sourceLinks = SourceLinks.NEVER))
                        assertEquals(200, request("/api/chats/42/rule", "PUT", rule).statusCode())
                        assertEquals(SourceLinks.NEVER, settings.rule("42").sourceLinks)
                        assertEquals(
                            400,
                            request("/api/chats/42/rule", "PUT", """{"sourceLinks":"invalid"}""").statusCode(),
                        )
                    }
                }
            }
        }
    }
}
