package ru.aigram.memory

fun interface MemorySummarizer {
    fun summarize(model: String, existing: String, sources: List<MemorySource>): String

    fun fits(model: String, existing: String, sources: List<MemorySource>): Boolean = true
}
