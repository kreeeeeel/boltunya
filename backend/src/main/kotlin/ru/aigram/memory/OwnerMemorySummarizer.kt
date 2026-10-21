package ru.aigram.memory

fun interface OwnerMemorySummarizer {
    fun summarizeOwner(model: String, existing: String, sources: List<MemorySource>): String
}
