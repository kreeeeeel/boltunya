package ru.boltunya.ai.research

import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.SourceLinks
import ru.boltunya.resources.ResourceCatalog

private const val MAX_TRAILING_PUNCTUATION = 8
private val TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', ')', '}', '>')

class ResearchLinkPolicy(
    private val resources: ResourceCatalog = ResourceCatalog(),
    private val urls: ResearchUrl = ResearchUrl(),
) {
    fun visible(rule: ChatRule, history: List<ChatMessage>): Boolean = when (rule.sourceLinks) {
        SourceLinks.ALWAYS -> true
        SourceLinks.NEVER -> false
        SourceLinks.REQUESTED -> {
            val latest = history.lastOrNull { !it.outgoing && it.text.isNotBlank() }?.text.orEmpty()
            resources.regex("intent/source-request").containsMatchIn(latest) &&
                !resources.regex("intent/source-negation").containsMatchIn(latest)
        }
    }

    fun forPrompt(rule: ChatRule, history: List<ChatMessage>, research: ResearchContext): ResearchContext =
        if (visible(rule, history)) research else research.copy(sources = research.sources.map { it.copy(url = "") })

    fun format(text: String, rule: ChatRule, history: List<ChatMessage>, research: ResearchContext): String {
        if (research.status == "none") return text
        val allowed = if (visible(rule, history)) {
            research.sources.filter { urls.canonical(it.url).isNotEmpty() }.associateBy {
                urls.canonical(it.url)
            }
        } else {
            emptyMap()
        }
        val markdown = resources.regex("common/research-markdown-link").replace(text) { match ->
            val source = allowed[canonical(match.groupValues[2])]
            if (source == null) {
                match.groupValues[1].takeUnless { resources.regex("intent/source-label").matches(it) }.orEmpty()
            } else {
                source.url
            }
        }
        var included = false
        val cleaned = resources.regex("common/research-url").replace(markdown) { match ->
            val candidate = generateSequence(match.value) { value ->
                if (value.lastOrNull() in TRAILING_PUNCTUATION) value.dropLast(1) else null
            }.take(MAX_TRAILING_PUNCTUATION).firstOrNull { canonical(it) in allowed }
            if (candidate == null) {
                match.value.takeLastWhile { it in TRAILING_PUNCTUATION }
            } else {
                included = true
                allowed.getValue(canonical(candidate)).url + match.value.removePrefix(candidate)
            }
        }
        val answer = resources.regex("common/research-citation").replace(cleaned, "")
            .lines().filterNot { resources.regex("intent/source-label").matches(it.trim()) }.joinToString("\n")
            .replace(resources.regex("common/research-empty-line"), "").trim()
        if (answer.isBlank()) return resources.prompt("ru/research/no-answer")
        return if (!included && allowed.isNotEmpty()) {
            answer + "\n\n" + allowed.values.first().url
        } else {
            answer
        }
    }

    private fun canonical(value: String): String = urls.canonical(
        if (value.startsWith("www.", ignoreCase = true) ||
            value.startsWith("t.me/", ignoreCase = true)
        ) {
            "https://$value"
        } else {
            value
        },
    )
}
