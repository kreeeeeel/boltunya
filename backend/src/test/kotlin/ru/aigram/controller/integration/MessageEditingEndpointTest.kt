package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageEditingEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `edit API authenticates validates conflicts preserves draft text and returns enriched provenance`() {
        val telegram = MessageEditingTestGateway()
        val settings = SettingsStore(directory)
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "123")
        ContextExclusionStore(directory).update("100", "42", "123", ContextExclusionUpdate())
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    telegram,
                    engine,
                    ai,
                    replies,
                    "session",
                    automated,
                ) {}.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    val path = "/api/chats/42/messages/123/edit"
                    fun request(
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
                    fun body(revision: String, text: String, entities: String = "[]"): String =
                        """{"revision":"$revision","text":${Json.encodeToString(text)},"entities":$entities}"""

                    assertEquals(401, request(authorized = false).statusCode())
                    assertEquals(401, request("PUT", "{}", false).statusCode())
                    assertEquals(0, telegram.optionsReads)
                    val options = request()
                    assertEquals(200, options.statusCode())
                    val revision = Json.parseToJsonElement(options.body()).jsonObject
                        .getValue("revision").jsonPrimitive.content
                    assertEquals(400, request("PUT", "{}").statusCode())
                    val invalidEntities = """[{"offset":-1,"length":1,"kind":"bold"}]"""
                    assertEquals(400, request("PUT", body(revision, "Text", invalidEntities)).statusCode())
                    assertEquals(409, request("PUT", body("0".repeat(64), "Stale")).statusCode())
                    telegram.allowed = false
                    assertEquals(409, request("PUT", body(revision, "Permission changed")).statusCode())
                    telegram.allowed = true
                    val exact = "  Edited **plain text** 😀\n"
                    val saved = request("PUT", body(revision, exact))
                    assertEquals(200, saved.statusCode())
                    val message = Json.parseToJsonElement(saved.body()).jsonObject
                    assertEquals(JsonPrimitive(exact), message.getValue("text"))
                    assertEquals(JsonPrimitive(true), message.getValue("automated"))
                    assertEquals(JsonPrimitive(true), message.getValue("contextExcluded"))
                    assertEquals(JsonPrimitive(101), message.getValue("editedAt"))
                    assertEquals(1, telegram.edits)
                    assertEquals(409, request("PUT", body(revision, "Overwrite newer edit")).statusCode())
                    val fresh = Json.parseToJsonElement(request().body()).jsonObject
                        .getValue("revision").jsonPrimitive.content
                    assertEquals(200, request("PUT", body(fresh, exact)).statusCode())
                    assertEquals(1, telegram.edits)
                    assertEquals(0, telegram.sends)
                    assertTrue(automated.contains("42", "123"))
                }
            }
        }
    }
}
