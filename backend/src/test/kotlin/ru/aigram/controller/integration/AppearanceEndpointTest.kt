package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.settings.AppearanceSettings
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals

class AppearanceEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `appearance endpoints require local session and reject invalid saves`() {
        val settings = SettingsStore(directory)
        val telegram = MemoryTelegramGateway()
        val ollama = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ollama.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, telegram, engine, ollama, replies, "session") {}
                    .use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            method: String,
                            body: String = "",
                            authorized: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(URI.create("$base/api/appearance"))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authorized) {
                                builder.header("Cookie", "tiho=session")
                            }
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        assertEquals(401, request("GET", authorized = false).statusCode())
                        assertEquals(401, request("PUT", "{}", authorized = false).statusCode())
                        val appearance =
                            AppearanceSettings("dark", mapOf("accent" to "#ffcc00"), ".message { color: white; }")
                        assertEquals(200, request("PUT", Json.encodeToString(appearance)).statusCode())
                        assertEquals(appearance, Json.decodeFromString<AppearanceSettings>(request("GET").body()))
                        assertEquals(400, request("PUT", """{"tokens":{"accent":"invalid"}}""").statusCode())
                        assertEquals(appearance, Json.decodeFromString<AppearanceSettings>(request("GET").body()))
                    }
            }
        }
    }
}
