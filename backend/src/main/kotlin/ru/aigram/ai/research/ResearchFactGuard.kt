package ru.aigram.ai.research

import org.springframework.stereotype.Component
import ru.aigram.domain.ai.research.ResearchContext
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.resources.ResourceCatalog

@Component
class ResearchFactGuard(private val resources: ResourceCatalog) {
    private val terms = ResearchTerms(resources)
    private val markdown = ResearchMarkdown()
    private val numbers = ResearchNumbers()

    fun matches(reply: String, research: ResearchContext, history: List<ChatMessage> = emptyList()): Boolean {
        if (research.requiredNumbers.isEmpty()) {
            return true
        }
        val values = numbers.values(markdown.mapLinks(reply) { _, _ -> "" })
        if (values.isEmpty() && onlySourceRequested(history)) {
            return true
        }
        return research.requiredNumbers.all { it.toBigDecimal().stripTrailingZeros() in values }
    }

    private fun onlySourceRequested(history: List<ChatMessage>): Boolean {
        val latest = history.lastOrNull { !it.outgoing }?.text.orEmpty().trim()
        return terms.only("research-reuse", latest) && terms.contains("source-request", latest) &&
            !terms.contains("source-negation", latest)
    }
}
