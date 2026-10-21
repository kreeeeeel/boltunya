package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RegistryBlobDownloaderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `resolves CDN redirect before requesting ranges and verifies the complete file`() {
        val bytes = ByteArray(4 * 1024 * 1024 + 73) { (it % 251).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val ranges = AtomicLong()
        server.createContext("/registry") { exchange ->
            exchange.use {
                assertEquals(null, exchange.requestHeaders.getFirst("Range"))
                exchange.responseHeaders.set("Location", "/cdn")
                exchange.sendResponseHeaders(307, -1)
            }
        }
        server.createContext("/cdn") { exchange ->
            exchange.use {
                val range = exchange.requestHeaders.getFirst("Range").removePrefix("bytes=").split('-')
                val start = range[0].toInt()
                val end = range[1].toInt()
                ranges.incrementAndGet()
                exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${bytes.size}")
                exchange.sendResponseHeaders(206, (end - start + 1).toLong())
                exchange.responseBody.write(bytes, start, end - start + 1)
            }
        }
        server.start()
        try {
            val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
            val checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            val received = AtomicLong()
            val target = directory.resolve("model")
            RegistryBlobDownloader(http).download(
                URI.create("http://127.0.0.1:${server.address.port}/registry"),
                target,
                "sha256:$checksum",
                bytes.size.toLong(),
            ) { received.addAndGet(it) }
            assertContentEquals(bytes, Files.readAllBytes(target))
            assertEquals(bytes.size.toLong(), received.get())
            assertEquals(2, ranges.get())
        } finally {
            server.stop(0)
        }
    }
}
