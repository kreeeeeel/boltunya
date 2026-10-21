package ru.aigram.ai.research

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.research.ResearchSource
import ru.aigram.domain.ai.research.SearchConnectionState
import ru.aigram.exception.impl.SearchRateLimitedException
import ru.aigram.exception.impl.WebSearchUnavailableException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SearchCredentialStore
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ExaMcpSearchClientTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `negotiates MCP over HTTP parses source text and never treats rate limit instructions as search results`() {
        val credentials = SearchCredentialStore(directory)
        val reader = PublicPageReader { arrayOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }
        TestSearchMcpServer().start().use { server ->
            val client =
                ExaMcpSearchClient(credentials, ExaResultParser(ResourceCatalog(), reader), server.origin, "/mcp")
            val result = client.search("Kotlin")
            assertEquals(2, result.size)
            assertEquals("2026-10-10", result.first().publishedAt)
            assertEquals("web_search_exa", server.tool)
            assertEquals("Kotlin", server.query)
            assertEquals(SearchConnectionState.READY, client.connection().state)
            server.limited = true
            assertFailsWith<SearchRateLimitedException> { client.search("Kotlin") }
            assertEquals(SearchConnectionState.LIMITED, client.connection().state)
            server.limited = false
            server.error = true
            assertFailsWith<WebSearchUnavailableException> { client.search("Kotlin") }
            assertEquals(SearchConnectionState.UNAVAILABLE, client.connection().state)
        }
    }

    @Test
    fun `default free search avoids Exa entirely without a configured key`() {
        val credentials = SearchCredentialStore(directory)
        val reader = PublicPageReader { arrayOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }
        TestSearchMcpServer().start().use { server ->
            val client =
                ExaMcpSearchClient(credentials, ExaResultParser(ResourceCatalog(), reader), server.origin, "/mcp")
            val source = ResearchSource("Kotlin release", "https://kotlinlang.org/docs/releases.html", "Kotlin release")
            val free = SearchProvider { listOf(source) }
            val search =
                FallbackSearchProvider(
                    free,
                    OptionalExaSearchProvider(client),
                    SearchResultSelector(),
                    java.time.Clock.systemUTC(),
                )
            assertEquals(listOf(source), search.search("Kotlin release"))
            assertEquals("", server.query)
            assertEquals(emptyList(), OptionalExaSearchProvider(client).search("Kotlin"))
            assertEquals("", server.query)
            credentials.save("example-exa-key-123")
            assertEquals(2, OptionalExaSearchProvider(client).search("Kotlin").size)
            assertEquals("Kotlin", server.query)
        }
    }

    @Test
    fun `keeps the API key encrypted at rest and sends it only in the provider header`() {
        val credentials = SearchCredentialStore(directory)
        credentials.save("example-exa-key-123")
        assertEquals("example-exa-key-123", credentials.load())
        assertFalse(String(Files.readAllBytes(directory.resolve("search.credentials"))).contains("example-exa-key-123"))
        val reader = PublicPageReader { arrayOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }
        TestSearchMcpServer().start().use { server ->
            ExaMcpSearchClient(
                credentials,
                ExaResultParser(ResourceCatalog(), reader),
                server.origin,
                "/mcp",
            ).search("Kotlin")
            assertEquals("example-exa-key-123", server.key)
            assertEquals("Kotlin", server.query)
        }
        credentials.delete()
        assertFalse(credentials.exists())
    }
}
