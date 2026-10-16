package ru.boltunya.memory

fun interface OwnerMemorySummarizer {
    fun summarizeOwner(model: String, existing: String, sources: List<MemorySource>): String
}
