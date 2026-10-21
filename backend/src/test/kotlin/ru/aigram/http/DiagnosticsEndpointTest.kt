package ru.aigram.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticReport
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.domain.ReplyResult
import ru.aigram.exception.impl.EngineUnavailableException
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.StorageLocationReport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects error history and file locations and clears persisted errors`() {
        val settings = SettingsStore(directory)
        val telegram = MemoryTelegramGateway()
        val ollama = AiTestServices("http://127.0.0.1:1")
        val diagnostics = DiagnosticLog(directory, Clock.systemUTC(), ResourceCatalog())
        diagnostics.record(DiagnosticSource.ENGINE, EngineUnavailableException())
        EngineManager(directory, ollama.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    telegram,
                    engine,
                    ollama,
                    replies,
                    "session",
                    diagnostics = diagnostics,
                ) {}.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun request(
                        path: String,
                        method: String = "GET",
                        authorized: Boolean = true,
                    ): HttpResponse<String> {
                        val builder = HttpRequest.newBuilder(URI.create(base + path))
                            .method(method, HttpRequest.BodyPublishers.noBody())
                        if (authorized) builder.header("Cookie", "tiho=session")
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    assertEquals(401, request("/api/diagnostics/errors", authorized = false).statusCode())
                    assertEquals(401, request("/api/storage/locations", authorized = false).statusCode())
                    assertEquals(
                        "ENGINE_UNAVAILABLE",
                        Json.decodeFromString<DiagnosticReport>(request("/api/diagnostics/errors").body())
                            .errors.single().code,
                    )
                    val locations = Json.decodeFromString<StorageLocationReport>(
                        request("/api/storage/locations").body(),
                    )
                    assertTrue(locations.locations.any { it.id == "memory" })
                    assertEquals(200, request("/api/diagnostics/errors", "DELETE").statusCode())
                    assertTrue(diagnostics.report().errors.isEmpty())
                }
            }
        }
    }
}
