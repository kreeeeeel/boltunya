package ru.aigram.ai.research

import jakarta.inject.Inject
import ru.aigram.domain.ChatMessage
import ru.aigram.resources.ResourceCatalog

class ResearchFactGuard @Inject constructor(private val resources: ResourceCatalog) {
    private val terms = ResearchTerms(resources)
    private val markdown = ResearchMarkdown()
    private val numbers = ResearchNumbers()

    fun matches(reply: String, research: ResearchContext, history: List<ChatMessage> = emptyList()): Boolean {
        if (research.requiredNumbers.isEmpty()) return true
        val values = numbers.values(markdown.mapLinks(reply) { _, _ -> "" })
        if (values.isEmpty() && onlySourceRequested(history)) return true
        return research.requiredNumbers.all { it.toBigDecimal().stripTrailingZeros() in values }
    }

    private fun onlySourceRequested(history: List<ChatMessage>): Boolean {
        val latest = history.lastOrNull { !it.outgoing }?.text.orEmpty().trim()
        return terms.only("research-reuse", latest) && terms.contains("source-request", latest) &&
            !terms.contains("source-negation", latest)
    }
}
