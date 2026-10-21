package ru.aigram.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class RemovedFeatureRoutesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `removed scenario routes are unavailable and legacy user files are never read or changed`() {
        val legacy = "Unparsed legacy user scenario data"
        Files.writeString(directory.resolve("flows.json"), legacy)
        Files.writeString(directory.resolve("flow-data.json"), legacy)
        val settings = SettingsStore(directory)
        val telegram = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, telegram, engine, ai, replies, "session") {}
                    .use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        listOf(
                            "/api/flows" to "GET",
                            "/api/flows/legacy" to "PUT",
                            "/api/flows/legacy" to "DELETE",
                            "/api/flows/legacy/test" to "POST",
                        ).stream().forEach { (path, method) ->
                            val request = HttpRequest.newBuilder(
                                URI.create(base + path),
                            ).header("Cookie", "tiho=session")
                                .method(method, HttpRequest.BodyPublishers.ofString("{}")).build()
                            assertEquals(404, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                        }
                    }
            }
        }
        assertEquals(legacy, Files.readString(directory.resolve("flows.json")))
        assertEquals(legacy, Files.readString(directory.resolve("flow-data.json")))
    }
}
