package ru.aigram.controller.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.media.MediaFile
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.IntStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaContentIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `supports authenticated video byte ranges and decompresses TGS stickers through MVC`() {
        val bytes = ByteArray(100) { it.toByte() }
        val video = Files.write(directory.resolve("video.mp4"), bytes)
        val sticker = directory.resolve("sticker.tgs")
        val corrupt = directory.resolve("corrupt.tgs")
        GZIPOutputStream(Files.newOutputStream(sticker)).use { it.write("{\"layers\":[]}".toByteArray()) }
        val gateway = object : TelegramGateway by FakeTelegramGateway() {
            override fun media(fileId: Int): MediaFile = when (fileId) {
                2 -> MediaFile(sticker, "application/x-tgsticker", "sticker.tgs")
                3 -> MediaFile(corrupt, "application/x-tgsticker", "corrupt.tgs")
                else -> MediaFile(video, "video/mp4", "video.mp4")
            }
        }
        val settings = SettingsStore(directory)
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "media-session") {
                }.use { server ->
                    val http = HttpClient.newHttpClient()
                    val base = server.start().substringBefore("/?")
                    fun request(id: Int, range: String? = null): HttpResponse<ByteArray> {
                        val builder = HttpRequest.newBuilder(URI.create("$base/api/media/$id"))
                            .header("Cookie", "tiho=media-session")
                        if (range != null) {
                            builder.header("Range", range)
                        }
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                    }
                    val unauthenticated = HttpRequest.newBuilder(URI.create("$base/api/media/1")).build()
                    assertEquals(401, http.send(unauthenticated, HttpResponse.BodyHandlers.discarding()).statusCode())
                    val partial = request(1, "bytes=10-19")
                    assertEquals(206, partial.statusCode())
                    assertEquals("bytes 10-19/100", partial.headers().firstValue("Content-Range").orElseThrow())
                    assertContentEquals(bytes.copyOfRange(10, 20), partial.body())
                    listOf(
                        "bytes=200-",
                        "bytes=+1-2",
                        "bytes=1-2,3-4",
                        "bytes=1--2",
                        "bytes=99999999999999999999-",
                        "bytes=-9",
                    ).stream().forEach { assertEquals(416, request(1, it).statusCode(), it) }
                    val animation = request(2)
                    assertEquals("application/json", animation.headers().firstValue("Content-Type").orElseThrow())
                    assertEquals("{\"layers\":[]}", animation.body().toString(Charsets.UTF_8))
                    IntStream.range(0, 3).forEach {
                        Files.write(corrupt, byteArrayOf(0, 1, 2))
                        val failed = request(3)
                        assertEquals(502, failed.statusCode())
                        assertTrue(failed.body().toString(Charsets.UTF_8).contains("REQUEST_FAILED"))
                        Files.delete(corrupt)
                        assertFalse(Files.exists(corrupt))
                    }
                }
            }
        }
    }
}
