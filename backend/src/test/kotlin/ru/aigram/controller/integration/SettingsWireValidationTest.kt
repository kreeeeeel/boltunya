package ru.aigram.controller.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals

class SettingsWireValidationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `accepts each wire type and rejects string coercion null and fractional delays through MVC`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "session") {
                }.use { server ->
                    val endpoint = URI.create(server.start().substringBefore("/?") + "/api/settings")
                    val http = HttpClient.newHttpClient()
                    fun update(body: String): Int = http.send(
                        HttpRequest.newBuilder(endpoint).header("Cookie", "tiho=session")
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.discarding(),
                    ).statusCode()

                    listOf(
                        """{"paused":false}""",
                        """{"replyDelaySeconds":4}""",
                        """{"globalPrompt":"hello"}""",
                        """{"customStyles":[]}""",
                    ).stream().forEach { assertEquals(200, update(it), it) }
                    listOf(
                        """{"paused":"true"}""",
                        """{"paused":null}""",
                        """{"replyDelaySeconds":4.5}""",
                        """{"replyDelaySeconds":"4"}""",
                        """{"globalPrompt":{}}""",
                        """{"customStyles":{}}""",
                    ).stream().forEach { assertEquals(400, update(it), it) }
                    assertEquals(4, settings.get().replyDelaySeconds)
                    assertEquals("hello", settings.get().globalPrompt)
                }
            }
        }
    }
}
