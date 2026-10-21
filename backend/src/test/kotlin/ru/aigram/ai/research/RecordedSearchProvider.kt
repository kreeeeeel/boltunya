package ru.aigram.ai.research

class RecordedSearchProvider(private val delegate: SearchProvider = WebSearchClient()) : SearchProvider {
    var query = ""
    var sources: List<ResearchSource> = emptyList()

    override fun search(query: String): List<ResearchSource> {
        this.query = query
        sources = delegate.search(query)
        return sources
    }

    fun clear() {
        query = ""
        sources = emptyList()
    }
}
