package ru.aigram.ai.research

import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import ru.aigram.domain.ai.research.ResearchSource
import ru.aigram.domain.ai.research.SearchConnection
import ru.aigram.domain.ai.research.SearchConnectionState
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.SearchRateLimitedException
import ru.aigram.exception.impl.WebSearchUnavailableException
import ru.aigram.storage.SearchCredentialStore
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

private const val EXA_ORIGIN = "https://mcp.exa.ai"
private const val EXA_ENDPOINT = "/mcp?tools=web_search_exa"
private const val SEARCH_TOOL = "web_search_exa"
private const val RESULT_COUNT = 6
private const val MAX_RESPONSE_BYTES = 256 * 1024
private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
private val REQUEST_TIMEOUT = Duration.ofSeconds(8)

class ExaMcpSearchClient(
    private val credentials: SearchCredentialStore,
    private val parser: ExaResultParser,
    private val origin: String = EXA_ORIGIN,
    private val endpoint: String = EXA_ENDPOINT,
) : SearchProvider {
    @Volatile private var state = SearchConnectionState.UNCHECKED

    fun connection(): SearchConnection = SearchConnection(credentials.exists(), state)

    fun reset() {
        state = SearchConnectionState.UNCHECKED
    }

    override fun revision(): Long = credentials.revision()

    override fun search(query: String): List<ResearchSource> {
        try {
            val request = HttpRequest.newBuilder().timeout(REQUEST_TIMEOUT)
            val key = credentials.load()
            if (key.isNotEmpty()) {
                request.header("x-api-key", key)
            }
            val transport = HttpClientStreamableHttpTransport.builder(origin)
                .endpoint(endpoint).connectTimeout(CONNECT_TIMEOUT).requestBuilder(request)
                .clientBuilder(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER))
                .maxResponseSize(MAX_RESPONSE_BYTES).build()
            McpClient.sync(
                transport,
            ).initializationTimeout(CONNECT_TIMEOUT).requestTimeout(REQUEST_TIMEOUT).build().use {
                it.initialize()
                val call = McpSchema.CallToolRequest.builder(SEARCH_TOOL)
                    .arguments(mapOf("query" to query, "numResults" to RESULT_COUNT)).build()
                val result = it.callTool(call)
                if (result.meta()?.get("ai.exa/rateLimited") == true) {
                    throw SearchRateLimitedException()
                }
                if (result.isError() == true) {
                    throw WebSearchUnavailableException()
                }
                val text = result.content().filterIsInstance<McpSchema.TextContent>().joinToString("\n") { item ->
                    item.text()
                }
                val sources = parser.parse(text)
                if (sources.isEmpty()) {
                    throw WebSearchUnavailableException()
                }
                state = SearchConnectionState.READY
                return sources
            }
        } catch (exception: SearchRateLimitedException) {
            state = SearchConnectionState.LIMITED
            throw exception
        } catch (exception: IntegrationException) {
            state = SearchConnectionState.UNAVAILABLE
            throw exception
        } catch (exception: RuntimeException) {
            state = SearchConnectionState.UNAVAILABLE
            throw WebSearchUnavailableException(exception)
        }
    }
}
