package ru.aigram.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.MemoryQueueTask
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.MemoryCollectionScope
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals

class SettingsQueueEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `saving preferences preserves memory queue and validates proactive and collection settings`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { replies ->
                replies.pauseQueue(true)
                replies.enqueueMemory(
                    MemoryQueueTask(
                        "memory", "42", "100", "Chat", "", "gemma3:4b", true,
                        { true }, {}, { error("must stay queued") }, {},
                    ),
                )
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "settings-queue") {}
                    .use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun patch(body: String, path: String = "/api/settings", method: String = "PATCH"): Int =
                            http.send(
                                HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "tiho=settings-queue")
                                    .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                                HttpResponse.BodyHandlers.discarding(),
                            ).statusCode()
                        assertEquals(200, patch("""{"closeToTray":false,"memoryCollectionScope":"all-allowed"}"""))
                        assertEquals(MemoryCollectionScope.ALL_ALLOWED, settings.get().memoryCollectionScope)
                        assertEquals("queued", replies.activities().single().status)
                        assertEquals(200, patch("""{"globalPrompt":"Updated owner instructions"}"""))
                        assertEquals("queued", replies.activities().single().status)
                        assertEquals(400, patch("""{"memoryCollectionScope":"everyone"}"""))
                        assertEquals(400, patch("""{"proactiveEnabled":true,"proactiveIntervalMinutes":0}"""))
                        assertEquals(400, patch("""{"proactiveIntervalMinutes":43201}"""))
                        assertEquals(200, patch("""{"proactiveEnabled":true,"proactiveIntervalMinutes":90}"""))
                        assertEquals(true, settings.get().proactiveEnabled)
                        assertEquals(90, settings.get().proactiveIntervalMinutes)
                        assertEquals("queued", replies.activities().single().status)
                        replies.enqueueDraft("43", "gemma3:4b") { ReplyResult("draft", 0, 0.0) }
                        assertEquals(200, patch("""{"instructions":"Changed chat 42"}""", "/api/chats/42/rule", "PUT"))
                        assertEquals(2, replies.activities().count { it.status == "queued" })
                        assertEquals(200, patch("""{"instructions":"Changed chat 43"}""", "/api/chats/43/rule", "PUT"))
                        assertEquals("queued", replies.activities().single { it.kind.name == "MEMORY" }.status)
                        assertEquals("cancelled", replies.activities().single { it.kind.name == "DRAFT" }.status)
                    }
            }
        }
    }
}
