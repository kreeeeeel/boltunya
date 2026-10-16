package ru.boltunya.ai.research

fun interface SearchProvider {
    fun search(query: String): List<ResearchSource>

    fun revision(): Long = 0
}
