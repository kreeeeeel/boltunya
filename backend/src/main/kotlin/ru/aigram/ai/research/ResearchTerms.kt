package ru.aigram.ai.research

import kotlinx.serialization.json.Json
import ru.aigram.resources.ResourceCatalog

/** Editable literal vocabulary. Entries are words/phrases, not executable patterns. */
class ResearchTerms(
    resources: ResourceCatalog = ResourceCatalog(),
    private val words: ResearchWords = ResearchWords(),
) {
    private val vocabulary = Json.decodeFromString<List<String>>(resources.text("languages.json"))
        .flatMap { language ->
            Json.decodeFromString<Map<String, List<String>>>(resources.text("search/intents/$language.json"))
                .flatMap { (key, values) -> values.map { key to words.tokens(it) } }
        }.groupBy({ it.first }, { it.second })

    fun contains(key: String, value: String): Boolean {
        val tokens = words.tokens(value)
        return vocabulary[key].orEmpty().any { phrase ->
            phrase.isNotEmpty() && tokens.windowed(phrase.size).any { candidate ->
                candidate.zip(phrase).all { (word, prefix) -> matches(word, prefix) }
            }
        }
    }

    fun only(key: String, value: String): Boolean {
        val tokens = words.tokens(value)
        return tokens.isNotEmpty() && vocabulary[key].orEmpty().any { phrase ->
            tokens.size == phrase.size && tokens.zip(phrase).all { (word, prefix) -> matches(word, prefix) }
        }
    }

    fun locationFollowup(value: String): Boolean {
        val tokens = words.tokens(value)
        return tokens.size in 2..8 && (
            tokens.first() in setOf("в", "in", "у", "на") ||
                tokens.size >= 3 && tokens[0] in setOf("а", "and") && tokens[1] in setOf("в", "in", "у", "на")
            )
    }

    private fun matches(word: String, prefix: String): Boolean =
        if (prefix.length <= 2) word == prefix else word.startsWith(prefix)
}
