package ru.aigram.ai.research

import ru.aigram.domain.ai.research.ResearchSource

fun interface SearchProvider {
    fun search(query: String): List<ResearchSource>

    fun revision(): Long = 0
}
