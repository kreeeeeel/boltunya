package ru.aigram.memory

import ru.aigram.domain.memory.MemorySource

fun interface OwnerMemorySummarizer {
    fun summarizeOwner(model: String, existing: String, sources: List<MemorySource>): String

    fun fitsOwner(model: String, existing: String, sources: List<MemorySource>): Boolean = true
}
