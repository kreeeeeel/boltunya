package ru.boltunya.ai.research

import jakarta.inject.Inject
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog

class ResearchFactGuard @Inject constructor(private val resources: ResourceCatalog) {
    fun matches(reply: String, research: ResearchContext, history: List<ChatMessage> = emptyList()): Boolean {
        if (research.requiredNumbers.isEmpty()) return true
        val text = reply.replace(resources.regex("common/research-url"), "")
            .replace(resources.regex("common/research-numeric-date"), "")
        val numbers = resources.regex("common/research-number").findAll(text)
            .map { it.value.filterNot(Char::isWhitespace).replace(',', '.').toBigDecimal().stripTrailingZeros() }
            .toSet()
        if (numbers.isEmpty() && onlySourceRequested(history)) return true
        return research.requiredNumbers.all { it.toBigDecimal().stripTrailingZeros() in numbers }
    }

    private fun onlySourceRequested(history: List<ChatMessage>): Boolean {
        val latest = history.lastOrNull { !it.outgoing }?.text.orEmpty().trim()
        return resources.regex("intent/research-reuse").matches(latest) &&
            resources.regex("intent/source-request").containsMatchIn(latest) &&
            !resources.regex("intent/source-negation").containsMatchIn(latest)
    }
}
