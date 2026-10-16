package ru.boltunya.ai.research

class OptionalExaSearchProvider(private val client: ExaMcpSearchClient) : SearchProvider {
    override fun revision(): Long = client.revision()

    override fun search(query: String): List<ResearchSource> =
        if (client.connection().configured) client.search(query) else emptyList()
}
