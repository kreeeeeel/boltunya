package ru.aigram.memory

import ru.aigram.domain.memory.MemorySource

class BudgetMemorySummarizer(private val capacity: Int) : MemorySummarizer {
    override fun summarize(model: String, existing: String, sources: List<MemorySource>): String = "Collected facts"

    override fun fits(model: String, existing: String, sources: List<MemorySource>): Boolean = sources.size <= capacity
}
