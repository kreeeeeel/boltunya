package ru.boltunya.memory

fun interface MemorySummarizer {
    fun summarize(model: String, existing: String, sources: List<MemorySource>): String
}
