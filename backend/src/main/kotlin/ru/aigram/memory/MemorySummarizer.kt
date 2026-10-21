package ru.aigram.memory

import ru.aigram.domain.memory.MemorySource

fun interface MemorySummarizer {
    fun summarize(model: String, existing: String, sources: List<MemorySource>): String

    fun fits(model: String, existing: String, sources: List<MemorySource>): Boolean = true
}
