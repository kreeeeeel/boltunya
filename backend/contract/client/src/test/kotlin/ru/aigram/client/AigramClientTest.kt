package ru.aigram.client

import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import ru.aigram.client.configuration.AigramClientFactory
import ru.aigram.client.exception.AigramClientErrorCode
import ru.aigram.client.exception.impl.InvalidClientSessionException
import ru.aigram.client.exception.impl.InvalidClientUriException
import ru.aigram.client.generated.infrastructure.Serializer
import ru.aigram.client.generated.model.ActivityKind
import ru.aigram.client.generated.model.SettingsUpdate
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REQUEST_TIMEOUT = Duration.ofSeconds(5)

class AigramClientTest {
    @Test
    fun `generated enum factories preserve wire values and reject unknown values explicitly`() {
        val mapper = Serializer.jacksonObjectMapper
        assertEquals(ActivityKind.DRAFT, mapper.readValue("\"draft\"", ActivityKind::class.java))
        assertEquals("\"draft\"", mapper.writeValueAsString(ActivityKind.DRAFT))
        assertEquals(ActivityKind.DRAFT, ActivityKind.decode("DRAFT"))
        assertNull(ActivityKind.decodeOrNull("unknown"))
        assertNull(ActivityKind.encode("draft"))
        assertFailsWith<IllegalArgumentException> { ActivityKind.decode("unknown") }
        assertFailsWith<IllegalArgumentException> { ActivityKind.decode(null) }
    }

    @Test
    fun `binary media uploads and downloads preserve raw bytes filename and content type`() {
        ClientTestServer().start().use { server ->
            val bytes = ByteArray(300 * 1024) { index -> (index % 251).toByte() }
            server.response(
                "/api/uploads",
                """
                {"id":"550e8400-e29b-41d4-a716-446655440000","fileName":"picture.bin",
                 "mimeType":"application/octet-stream","size":${bytes.size}}
                """.trimIndent(),
            )
            server.binaryResponse("/api/media/7", bytes)
            AigramClientFactory().create(server.baseUri, { "synthetic-session" }).use { client ->
                val uploaded = assertNotNull(client.media.uploadFile("picture.bin", bytes).block(REQUEST_TIMEOUT))
                assertEquals("picture.bin", uploaded.fileName)
                assertEquals(bytes.size.toLong(), uploaded.propertySize)
                assertContentEquals(bytes, client.media.media(7).block(REQUEST_TIMEOUT))
                val request = server.requests.first()
                assertEquals("POST", request.method)
                assertEquals("/api/uploads", request.path)
                assertEquals("application/octet-stream", request.contentType)
                assertEquals("picture.bin", request.fileName)
                assertContentEquals(bytes, request.bytes)
                assertEquals(listOf("/api/uploads", "/api/media/7"), server.requests.map { it.path })
                assertTrue(server.requests.all { it.cookie == "tiho=synthetic-session" })
            }
        }
    }

    @Test
    fun `core typed API groups use supplied origin and resolve fresh authentication on subscription`() {
        ClientTestServer().start().use { server ->
            server.response(
                "/api/settings",
                """{"paused":true,"defaultModel":"synthetic:model","rules":{},"globalPrompt":"Typed settings"}""",
            )
            server.response("/api/stickers", """{"recent":[],"favorites":[],"sets":[]}""")
            server.response("/api/storage/locations", """{"locations":[]}""")
            server.response("/api/chats/42/messages/9007199254740993/link", """{"url":"https://t.me/example/7"}""")
            val token = AtomicReference("first-session")
            AigramClientFactory().create(server.baseUri, { token.get() }).use { client ->
                val pending = client.system.storageLocations()
                assertTrue(server.requests.isEmpty(), "Constructing a client or Mono must not send requests")
                token.set("rotated-session")
                assertTrue(assertNotNull(pending.block(REQUEST_TIMEOUT)).locations.isEmpty())
                assertTrue(assertNotNull(client.activity.cancelActivity("synthetic").block(REQUEST_TIMEOUT)).ok)
                assertEquals(
                    "https://t.me/example/7",
                    client.chats.messageLink("42", "9007199254740993").block(REQUEST_TIMEOUT)?.url,
                )
                assertTrue(assertNotNull(client.media.stickers().block(REQUEST_TIMEOUT)).sets.isEmpty())
                assertTrue(assertNotNull(client.memory.refreshChangedMemory().block(REQUEST_TIMEOUT)).ok)
                assertTrue(assertNotNull(client.models.cancelModelDownload().block(REQUEST_TIMEOUT)).ok)
                assertEquals(
                    "Typed settings",
                    client.settings.settings(SettingsUpdate(globalPrompt = "Typed settings"))
                        .block(REQUEST_TIMEOUT)?.globalPrompt,
                )
                assertTrue(assertNotNull(client.telegram.logout().block(REQUEST_TIMEOUT)).ok)
            }
            assertEquals(8, server.requests.size)
            assertTrue(server.requests.all { it.cookie == "tiho=rotated-session" })
            assertEquals("/api/chats/42/messages/9007199254740993/link", server.requests[2].path)
            assertEquals("/api/settings", server.requests[6].path)
            assertEquals("PATCH", server.requests[6].method)
            assertEquals(
                "Typed settings",
                Serializer.jacksonObjectMapper.readTree(server.requests[6].body)["globalPrompt"].asText(),
            )
        }
    }

    @Test
    fun `history preserves large Telegram identifiers and optional nullable fields`() {
        ClientTestServer().start().use { server ->
            val chatId = "-1009007199254740993"
            val messageId = "9007199254740993"
            server.response(
                "/api/chats/$chatId/messages",
                """
                [{"id":"$messageId","chatId":"$chatId","text":"РџСЂРёРІРµС‚",
                  "outgoing":false,"date":1,"kind":"text","media":null}]
                """.trimIndent(),
            )
            AigramClientFactory().create(server.baseUri, { "synthetic-session" }).use { client ->
                val message = assertNotNull(client.chats.history(chatId, messageId).block(REQUEST_TIMEOUT)).single()
                assertEquals(messageId, message.id)
                assertEquals(chatId, message.chatId)
                assertEquals("РџСЂРёРІРµС‚", message.text)
                assertNull(message.media)
                assertEquals("before=$messageId", server.requests.single().query)
            }
        }
    }

    @Test
    fun `separate clients do not share session state and each request reads the current token`() {
        ClientTestServer().start().use { server ->
            server.response("/api/storage/locations", """{"locations":[]}""")
            val first = AtomicReference("owner-one")
            AigramClientFactory().create(server.baseUri, { first.get() }).use { one ->
                AigramClientFactory().create(server.baseUri, { "owner-two" }).use { two ->
                    one.system.storageLocations().block(REQUEST_TIMEOUT)
                    two.system.storageLocations().block(REQUEST_TIMEOUT)
                    first.set("owner-one-new")
                    one.system.storageLocations().block(REQUEST_TIMEOUT)
                }
            }
            assertEquals(
                listOf("tiho=owner-one", "tiho=owner-two", "tiho=owner-one-new"),
                server.requests.map { it.cookie },
            )
        }
    }

    @Test
    fun `error responses retain status and body without retries while absent patch fields stay omitted`() {
        ClientTestServer().start().use { server ->
            val response = """{"code":"SYNTHETIC_FAILURE","message":"Synthetic failure"}"""
            server.response("/api/settings", response, 503)
            AigramClientFactory().create(server.baseUri, { "synthetic-session" }).use { client ->
                val failure = assertFailsWith<WebClientResponseException> {
                    client.settings.settings(SettingsUpdate(globalPrompt = "Synthetic prompt")).block(REQUEST_TIMEOUT)
                }
                assertEquals(503, failure.statusCode.value())
                assertEquals(response, failure.responseBodyAsString)
                assertEquals(1, server.requests.size)
                val submitted = Serializer.jacksonObjectMapper.readTree(server.requests.single().body)
                assertEquals("Synthetic prompt", submitted["globalPrompt"].asText())
                assertFalse(submitted.has("defaultModelProfileId"))
                assertFalse(submitted.has("memoryIntervalHours"))
            }
        }
    }

    @Test
    fun `an empty required response fails clearly instead of a null assertion`() {
        ClientTestServer().start().use { server ->
            server.response("/api/storage/locations", "")
            AigramClientFactory().create(server.baseUri, { "synthetic-session" }).use { client ->
                val failure = assertFailsWith<IllegalStateException> {
                    client.system.storageLocations().block(REQUEST_TIMEOUT)
                }
                assertEquals("Required response body is empty", failure.message)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun `invalid origins and unsafe session cookies fail before contacting any server`() {
        val factory = AigramClientFactory()
        listOf(
            "file:///tmp/aigram",
            "http://example.test/path",
            "http://user@example.test",
            "http://example.test?key=secret",
            "http://example.test#fragment",
            "http://example.test:0",
        ).stream().forEach { value ->
            val failure = assertFailsWith<InvalidClientUriException> {
                factory.create(URI.create(value), { "token" })
            }
            assertEquals(AigramClientErrorCode.INVALID_URI, failure.code)
            assertEquals("An explicit HTTP or HTTPS API origin is required", failure.message)
        }
        ClientTestServer().start().use { server ->
            listOf("", "one; other=two", "one\r\nInjected: value").stream().forEach { token ->
                factory.create(server.baseUri, { token }).use { client ->
                    val failure = assertFailsWith<InvalidClientSessionException> {
                        client.system.storageLocations().block(REQUEST_TIMEOUT)
                    }
                    assertEquals(AigramClientErrorCode.INVALID_SESSION, failure.code)
                    assertEquals("A valid API session token is required", failure.message)
                }
            }
            assertTrue(server.requests.isEmpty())
        }
    }
}
