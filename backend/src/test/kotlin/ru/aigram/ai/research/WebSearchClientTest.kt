package ru.aigram.ai.research

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import ru.aigram.domain.ai.research.ResearchPlan
import ru.aigram.exception.IntegrationException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebSearchClientTest {
    @Test
    fun `reads embedded article facts before upcoming plans and checks redirect destinations`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/article") { exchange ->
            val bytes = """
                <html><body><nav>Navigation noise</nav><main>
                <div data-content='{&quot;microFormat&quot;:[&quot;&lt;p&gt;Latest version: 2.4.21&lt;/p&gt;&quot;]}'></div>
                <h2>Upcoming release: 2.5.0 planned for December</h2>
                </main><script>Hidden script</script></body></html>
            """.trimIndent().toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.set("Location", "http://169.254.169.254/private")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            val reader = PublicPageReader(allowedPorts = setOf(-1, 80, 443, server.address.port)) { host ->
                if (host == "127.0.0.1") {
                    arrayOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)))
                } else {
                    InetAddress.getAllByName(host)
                }
            }
            val base = "http://127.0.0.1:${server.address.port}"
            val text = reader.read("$base/article")
            assertTrue(text.indexOf("2.4.21") < text.indexOf("2.5.0"))
            assertTrue(!text.contains("Navigation noise") && !text.contains("Hidden script"))
            assertFailsWith<IntegrationException> { reader.read("$base/redirect") }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `parses search redirects enriches snippets and falls back when primary has no results`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var blocked = false
        server.createContext("/duck") { exchange ->
            val content = if (blocked) {
                "<html>challenge</html>"
            } else {
                """
                <div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Frelease">Release</a>
                <a class="result__snippet">Version &amp; date</a></div>
                <div class="result"><a class="result__a" href="javascript:alert(1)">Unsafe</a></div>
            """
            }
            val bytes = content.toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/bing") { exchange ->
            val bytes = """
                <rss><channel><item><title>Backup</title><link>https://example.com/news</link>
                <description>Fresh news</description></item></channel></rss>
            """.trimIndent().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val public = PublicPageReader { arrayOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }
            val search = WebSearchClient("$base/duck", "$base/bing", public)
            val researcher = InternetResearcher(search = search, readPage = { "Verified article body" })
            val result = researcher.lookup(ResearchPlan(kind = "search", query = "release 2026-10-15")).sources
            assertEquals(1, result.size)
            assertEquals("https://example.com/release", result.first().url)
            assertTrue(result.first().text.contains("Verified article body"))
            blocked = true
            assertEquals(
                "Backup",
                researcher.lookup(ResearchPlan(kind = "search", query = "backup")).sources.first().title,
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `rejects local metadata private IPv6 and non web addresses before reading pages`() {
        val reader = PublicPageReader()
        listOf(
            "http://127.0.0.1",
            "http://169.254.169.254",
            "http://10.0.0.1",
            "http://[::1]",
            "http://[fd00::1]",
            "file:///C:/Windows/win.ini",
            "https://user:password@example.com",
            "https://example.com:11434",
        ).stream().forEach {
            assertFailsWith<IntegrationException> { reader.validate(it) }
        }
    }
}
