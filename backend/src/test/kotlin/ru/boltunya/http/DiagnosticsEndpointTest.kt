package ru.boltunya.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.EngineManager
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.diagnostics.DiagnosticReport
import ru.boltunya.diagnostics.DiagnosticSource
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.EngineUnavailableException
import ru.boltunya.memory.MemoryTelegramGateway
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SettingsStore
import ru.boltunya.storage.StorageLocationReport
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
