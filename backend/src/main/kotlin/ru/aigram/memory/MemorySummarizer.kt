package ru.aigram.memory

fun interface MemorySummarizer {
    fun summarize(model: String, existing: String, sources: List<MemorySource>): String
}
