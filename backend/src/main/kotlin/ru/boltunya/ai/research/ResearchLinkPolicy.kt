package ru.boltunya.ai.research

import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.SourceLinks
import ru.boltunya.resources.ResourceCatalog

class ResearchLinkPolicy(
    private val resources: ResourceCatalog = ResourceCatalog(),
    private val urls: ResearchUrl = ResearchUrl(),
    private val terms: ResearchTerms = ResearchTerms(resources),
    private val markdown: ResearchMarkdown = ResearchMarkdown(),
) {
    fun visible(rule: ChatRule, history: List<ChatMessage>): Boolean = when (rule.sourceLinks) {
        SourceLinks.ALWAYS -> true
        SourceLinks.NEVER -> false
        SourceLinks.REQUESTED -> {
            val latest = history.lastOrNull { !it.outgoing && it.text.isNotBlank() }?.text.orEmpty()
            terms.contains("source-request", latest) && !terms.contains("source-negation", latest)
        }
    }

    fun forPrompt(rule: ChatRule, history: List<ChatMessage>, research: ResearchContext): ResearchContext =
        if (visible(rule, history)) research else research.copy(sources = research.sources.map { it.copy(url = "") })

    fun format(text: String, rule: ChatRule, history: List<ChatMessage>, research: ResearchContext): String {
        if (research.status == "none") return text
        val allowed = if (visible(rule, history)) {
            research.sources.filter { urls.canonical(it.url).isNotEmpty() }.associateBy { urls.canonical(it.url) }
        } else {
            emptyMap()
        }
        var included = false
        val cleaned = markdown.mapLinks(text) { label, destination ->
            val source = allowed[urls.canonical(destination)]
            if (source != null) {
                included = true
                source.url
            } else if (terms.only("source-label", label) || label == destination ||
                label.startsWith("www.") || label.startsWith("t.me/")
            ) {
                ""
            } else {
                label
            }
        }
        val answer = cleaned.lines().filterNot { terms.only("source-label", it) }
            .joinToString("\n").trim()
        if (answer.isBlank()) return resources.prompt("ru/research/no-answer")
        return if (!included && allowed.isNotEmpty()) answer + "\n\n" + allowed.values.first().url else answer
    }
}
