package ru.boltunya.http

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.MediaFile
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MediaResponderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `supports video byte ranges and decompresses TGS stickers`() {
        val bytes = ByteArray(100) { it.toByte() }
        val video = Files.write(directory.resolve("video.mp4"), bytes)
        val sticker = directory.resolve("sticker.tgs")
        GZIPOutputStream(Files.newOutputStream(sticker)).use { it.write("{\"layers\":[]}".toByteArray()) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.use {
                val media = if (exchange.requestURI.path == "/sticker") {
                    MediaFile(sticker, "application/x-tgsticker", "sticker.tgs")
                } else {
                    MediaFile(video, "video/mp4", "video.mp4")
                }
                MediaResponder().respond(exchange, media)
            }
        }
        server.start()
        try {
            val http = HttpClient.newHttpClient()
            val base = "http://127.0.0.1:${server.address.port}"
            val request = HttpRequest.newBuilder(URI.create("$base/video")).header("Range", "bytes=10-19").build()
            val partial = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(206, partial.statusCode())
            assertEquals("bytes 10-19/100", partial.headers().firstValue("Content-Range").orElseThrow())
            assertContentEquals(bytes.copyOfRange(10, 20), partial.body())
            val invalid = HttpRequest.newBuilder(URI.create("$base/video")).header("Range", "bytes=200-").build()
            assertEquals(416, http.send(invalid, HttpResponse.BodyHandlers.ofByteArray()).statusCode())
            listOf(
                "bytes=+1-2",
                "bytes=1-2,3-4",
                "bytes=1--2",
                "bytes=99999999999999999999-",
                "bytes=-9",
            ).stream().forEach { range ->
                val malformed = HttpRequest.newBuilder(URI.create("$base/video")).header("Range", range).build()
                assertEquals(416, http.send(malformed, HttpResponse.BodyHandlers.ofByteArray()).statusCode(), range)
            }
            val animation = http.send(
                HttpRequest.newBuilder(URI.create("$base/sticker")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals("application/json", animation.headers().firstValue("Content-Type").orElseThrow())
            assertEquals("{\"layers\":[]}", animation.body())
        } finally {
            server.stop(0)
        }
    }
}
