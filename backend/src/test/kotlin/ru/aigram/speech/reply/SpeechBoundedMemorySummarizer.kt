package ru.aigram.speech.reply

import ru.aigram.domain.memory.MemorySource
import ru.aigram.memory.MemorySummarizer

class SpeechBoundedMemorySummarizer(private val characters: Int) : MemorySummarizer {
    val seen = mutableListOf<MemorySource>()

    override fun fits(model: String, existing: String, sources: List<MemorySource>): Boolean =
        sources.stream().mapToInt { it.text.length }.sum() <= characters

    override fun summarize(model: String, existing: String, sources: List<MemorySource>): String {
        seen.addAll(sources)
        return "Synthetic confirmed facts"
    }
}
