package ru.aigram.ai.research

import ru.aigram.domain.ai.research.ResearchSource

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
