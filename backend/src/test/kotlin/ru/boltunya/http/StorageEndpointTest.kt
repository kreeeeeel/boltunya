package ru.boltunya.http

import it.tdlight.jni.TdApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.OllamaClient
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ReplyResult
import ru.boltunya.memory.MemoryTelegramGateway
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.MessageArchiveStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.StorageClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects cache and archive routes validates download settings and keeps deleted originals available`() {
        val settings = SettingsStore(directory)
        val source = MemoryTelegramGateway()
        source.messages =
            listOf(ChatMessage("10", "42", "Сохранить этот текст", false, 10))
        val gateway = MediaArchiveGateway(source, Files.write(directory.resolve("photo.jpg"), byteArrayOf(1)))
        val ollama = OllamaClient("http://127.0.0.1:1")
        val stats = TdApi.StorageStatistics(
            1000,
            1,
            arrayOf(
                TdApi.StorageStatisticsByChat().apply {
                    byFileType =
                        arrayOf(TdApi.StorageStatisticsByFileType(TdApi.FileTypePhoto(), 1000, 1))
                },
            ),
        )
        var cleared = false
        val client =
            StorageClient { request ->
                if (request is TdApi.OptimizeStorage) {
                    cleared = true
                    TdApi.StorageStatistics(0, 0, emptyArray())
                } else {
                    stats
                }
            }
        EngineManager(directory, ollama).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    gateway,
                    engine,
                    ollama,
                    replies,
                    "session",
                    storageClient = client,
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
                        val builder = HttpRequest.newBuilder(
                            URI.create(base + path),
                        ).method(
                            method,
                            if (body.isEmpty()) {
                                HttpRequest.BodyPublishers.noBody()
                            } else {
                                HttpRequest.BodyPublishers.ofString(
                                    body,
                                )
                            },
                        )
                        if (authenticated)builder.header("Cookie", "tiho=session")
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    assertEquals(401, request("/api/cache", authenticated = false).statusCode())
                    assertTrue(request("/api/cache").body().contains("1000"))
                    assertEquals(
                        400,
                        request("/api/settings", "PATCH", """{"autoDownloadPhotos":"false"}""").statusCode(),
                    )
                    assertEquals(400, request("/api/settings", "PATCH", """{"autoDownloadMaxMb":101}""").statusCode())
                    assertEquals(
                        200,
                        request(
                            "/api/settings",
                            "PATCH",
                            """{"autoDownloadPhotos":false,"autoDownloadFiles":true,"autoDownloadMaxMb":5}""",
                        ).statusCode(),
                    )
                    assertEquals(5, settings.get().autoDownloadMaxMb)
                    assertEquals(200, request("/api/chats/42/messages").statusCode())
                    MessageArchiveStore(
                        directory,
                        ResourceCatalog(),
                        Clock.systemUTC(),
                    ).delete("100", "42", listOf(10), true, false)
                    source.messages = emptyList()
                    val history = Json.decodeFromString<List<ChatMessage>>(request("/api/chats/42/messages").body())
                    assertEquals("Сохранить этот текст", history.single().text)
                    assertTrue(history.single().deleted)
                    assertTrue(
                        Json.decodeFromString<List<ChatMessage>>(
                            request("/api/chats/42/deleted").body(),
                        ).single().deleted,
                    )
                    assertEquals(401, request("/api/chats/42/deleted", authenticated = false).statusCode())
                    assertEquals(200, request("/api/media/7/download", "POST").statusCode())
                    assertEquals(1, gateway.downloads)
                    assertEquals(200, request("/api/cache/clear", "POST").statusCode())
                    assertTrue(cleared)
                    assertEquals(
                        "Сохранить этот текст",
                        Json.decodeFromString<List<ChatMessage>>(request("/api/chats/42/deleted").body()).single().text,
                    )
                }
            }
        }
    }
}
