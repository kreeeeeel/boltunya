package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
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

class UploadEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `raw model uploads bypass JSON limit and stay account scoped`() {
        val settings = SettingsStore(directory)
        val gateway = MediaActionsGateway()
        gateway.delegate.messages = listOf(ChatMessage("123", "42", "Incoming", false, 0, topicId = 9))
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "media-session") {
                }.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun upload(
                        authenticated: Boolean = true,
                        mimeType: String = "application/octet-stream",
                        name: String = "report%20name.pdf",
                    ): HttpResponse<String> {
                        val builder = HttpRequest.newBuilder(
                            URI.create("$base/api/uploads"),
                        ).header("X-File-Name", name)
                            .header("Content-Type", mimeType).POST(
                                HttpRequest.BodyPublishers.ofByteArray(
                                    ByteArray(
                                        if (authenticated) {
                                            600_000
                                        } else {
                                            0
                                        },
                                    ) {
                                        1
                                    },
                                ),
                            )
                        if (authenticated) {
                            builder.header("Cookie", "tiho=media-session")
                        }
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    fun request(path: String, method: String, body: String = "{}"): HttpResponse<String> = http.send(
                        HttpRequest.newBuilder(
                            URI.create(base + path),
                        ).header(
                            "Cookie",
                            "tiho=media-session",
                        ).method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                    assertEquals(401, upload(false).statusCode())
                    val created = upload()
                    assertEquals(200, created.statusCode())
                    val file = Json.parseToJsonElement(created.body()).jsonObject
                    assertEquals("600000", file["size"]?.jsonPrimitive?.content)
                    assertFalse(created.body().contains(directory.toString()))
                    val id = file["id"]?.jsonPrimitive?.content.orEmpty()
                    val uploaded = directory.resolve("uploads").resolve(id).resolve("report name.pdf")
                    gateway.delegate.ownerId = "200"
                    assertEquals(200, request("/api/uploads/$id", "DELETE").statusCode())
                    assertTrue(Files.exists(uploaded))
                    gateway.delegate.ownerId = "100"
                    assertEquals(200, request("/api/uploads/$id", "DELETE").statusCode())
                    assertFalse(Files.exists(uploaded))
                    assertEquals(200, request("/api/uploads/$id", "DELETE").statusCode())
                }
            }
        }
    }
}
