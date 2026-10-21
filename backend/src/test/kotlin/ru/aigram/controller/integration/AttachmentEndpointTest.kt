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
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AttachmentEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `raw uploads bypass JSON limit remain account scoped and send with selected reply policy`() {
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
                    val second = Json.parseToJsonElement(
                        upload().body(),
                    ).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                    gateway.delegate.ownerId = "200"
                    assertEquals(
                        404,
                        request(
                            "/api/chats/42/attachments",
                            "POST",
                            """{"uploadIds":["$id"],"kind":"document"}""",
                        ).statusCode(),
                    )
                    gateway.delegate.ownerId = "100"
                    assertEquals(
                        400,
                        request(
                            "/api/chats/42/attachments",
                            "POST",
                            """{"uploadIds":["$id"],"kind":"document","caption":"Caption","captionEntities":[{"offset":0,"length":99,"type":"bold"}]}""",
                        ).statusCode(),
                    )
                    assertEquals(emptyList(), gateway.sent)
                    assertEquals(
                        200,
                        request(
                            "/api/chats/42/attachments",
                            "POST",
                            """{"uploadIds":["$id","$second"],"kind":"document","caption":"Caption","captionEntities":[{"offset":0,"length":7,"type":"spoiler"}]}""",
                        ).statusCode(),
                    )
                    assertEquals("123", gateway.replyTo)
                    assertEquals(9, gateway.topic)
                    assertEquals(listOf("Caption", ""), gateway.sent.stream().map { it.caption }.toList())
                    assertEquals(listOf(MessageEntity(0, 7, "spoiler")), gateway.sent.first().captionEntities)
                    assertEquals(emptyList(), gateway.sent.last().captionEntities)
                    assertFalse(gateway.sent.stream().anyMatch { Files.exists(it.path) })
                    settings.update { it.copy(deliveryMode = DeliveryMode.SEND) }
                    assertEquals(200, request("/api/chats/42/stickers", "POST", """{"stickerId":"7"}""").statusCode())
                    assertEquals("7", gateway.stickerId)
                    assertEquals("", gateway.replyTo)
                    assertEquals(200, request("/api/stickers", "GET").statusCode())
                    val webp = upload(mimeType = "image/webp", name = "photo.webp")
                    assertEquals(200, webp.statusCode())
                    val uploadedImage = Json.parseToJsonElement(webp.body()).jsonObject
                    assertEquals("image/webp", uploadedImage["mimeType"]?.jsonPrimitive?.content)
                    assertEquals("photo.webp", uploadedImage["fileName"]?.jsonPrimitive?.content)
                    val imageId = uploadedImage["id"]?.jsonPrimitive?.content.orEmpty()
                    assertEquals(200, request("/api/uploads/$imageId", "DELETE").statusCode())
                }
            }
        }
    }
}
