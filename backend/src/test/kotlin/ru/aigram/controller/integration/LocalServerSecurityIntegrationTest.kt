package ru.aigram.controller.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.storage.SettingsStore
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalServerSecurityIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `real servlet server preserves host origin static bootstrap body limits and listener cleanup`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        var port = 0
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "security-session") {
                }.use { server ->
                    val base = server.start().substringBefore("/?")
                    port = URI.create(base).port
                    val http = HttpClient.newHttpClient()
                    val bootstrap = http.send(
                        HttpRequest.newBuilder(URI.create("$base/testing-fixture/?key=security-session")).build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                    assertEquals(200, bootstrap.statusCode())
                    assertTrue(bootstrap.body().contains("Aigram integration fixture"))
                    val cookie = bootstrap.headers().firstValue("Set-Cookie").orElseThrow()
                    assertTrue(cookie.contains("tiho=security-session"))
                    assertTrue(cookie.contains("HttpOnly"))
                    assertTrue(cookie.contains("SameSite=Strict"))
                    assertEquals("nosniff", bootstrap.headers().firstValue("X-Content-Type-Options").orElseThrow())
                    assertEquals("DENY", bootstrap.headers().firstValue("X-Frame-Options").orElseThrow())
                    assertEquals("no-referrer", bootstrap.headers().firstValue("Referrer-Policy").orElseThrow())
                    assertEquals("no-store", bootstrap.headers().firstValue("Cache-Control").orElseThrow())
                    assertEquals(403, hostStatus(port, "localhost:$port"))
                    assertEquals(403, hostStatus(port, "untrusted.test:$port"))

                    fun activity(origin: String): Int = http.send(
                        HttpRequest.newBuilder(URI.create("$base/api/state"))
                            .header("Cookie", "tiho=security-session").header("Origin", origin).build(),
                        HttpResponse.BodyHandlers.discarding(),
                    ).statusCode()
                    assertEquals(200, activity(base))
                    assertEquals(403, activity("https://untrusted.test"))
                    listOf("/%61pi/state", "/api;foo/state").stream().forEach { path ->
                        val endpoint = URI.create(base + path)
                        val untrusted = HttpRequest.newBuilder(endpoint).build()
                        assertEquals(
                            401,
                            http.send(untrusted, HttpResponse.BodyHandlers.discarding()).statusCode(),
                            path,
                        )
                        val authenticated = HttpRequest.newBuilder(endpoint)
                            .header("Cookie", "tiho=security-session").build()
                        assertEquals(
                            200,
                            http.send(authenticated, HttpResponse.BodyHandlers.discarding()).statusCode(),
                            path,
                        )
                    }
                    assertEquals(
                        404,
                        http.send(
                            HttpRequest.newBuilder(
                                URI.create("$base/testing-fixture/%2e%2e/application.properties"),
                            ).build(),
                            HttpResponse.BodyHandlers.discarding(),
                        ).statusCode(),
                    )
                    val excessive = HttpRequest.newBuilder(URI.create("$base/api/telegram/submit"))
                        .header("Cookie", "tiho=security-session")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"value\":\"${"x".repeat(512 * 1024)}\"}"))
                        .build()
                    val oversized = http.send(excessive, HttpResponse.BodyHandlers.ofString())
                    assertEquals(400, oversized.statusCode())
                    assertTrue(oversized.body().contains("REQUEST_TOO_LARGE"))
                    val encodedExcessive = HttpRequest.newBuilder(URI.create("$base/%61pi/settings"))
                        .header("Cookie", "tiho=security-session")
                        .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(ByteArray(512 * 1024 + 1)))
                        .build()
                    val encodedOversized = http.send(encodedExcessive, HttpResponse.BodyHandlers.ofString())
                    assertEquals(400, encodedOversized.statusCode())
                    assertTrue(encodedOversized.body().contains("REQUEST_TOO_LARGE"))
                    val chunked = HttpRequest.newBuilder(URI.create("$base/api/telegram/submit"))
                        .header("Cookie", "tiho=security-session")
                        .POST(
                            HttpRequest.BodyPublishers.ofInputStream {
                                ByteArrayInputStream(ByteArray(512 * 1024 + 1))
                            },
                        )
                        .build()
                    val oversizedChunked = http.send(chunked, HttpResponse.BodyHandlers.ofString())
                    assertEquals(400, oversizedChunked.statusCode())
                    assertTrue(oversizedChunked.body().contains("REQUEST_TOO_LARGE"))
                    val unauthorized = HttpRequest.newBuilder(URI.create("$base/api/telegram/submit"))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(ByteArray(512 * 1024 + 1))).build()
                    assertEquals(401, http.send(unauthorized, HttpResponse.BodyHandlers.discarding()).statusCode())
                }
            }
        }
        ServerSocket().use { listener ->
            listener.reuseAddress = true
            listener.bind(InetSocketAddress("127.0.0.1", port))
        }
    }

    private fun hostStatus(port: Int, host: String): Int = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5000
        socket.getOutputStream().write(
            "GET /api/activity HTTP/1.1\r\nHost: $host\r\nCookie: tiho=security-session\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.US_ASCII),
        )
        socket.getOutputStream().flush()
        socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty().split(' ')[1].toInt()
    }
}
