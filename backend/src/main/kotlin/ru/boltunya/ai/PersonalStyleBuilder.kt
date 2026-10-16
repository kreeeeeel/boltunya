package ru.boltunya.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog

private val resources = ResourceCatalog()

class PersonalStyleBuilder @Inject constructor() {
    fun build(history: List<ChatMessage>): String {
        val samples = history.asReversed().stream()
            .filter { it.outgoing && !it.automated && it.kind == "text" && it.text.isNotBlank() }
            .map { it.text.trim() }
            .filter { it.length <= 600 && !boilerplate(it) }
            .distinct().limit(40).toList()
        if (samples.size < 3) {
            return resources.prompt("ru/conversation/style-fallback")
        }
        val lowercase = samples.stream().filter { text ->
            text.firstOrNull { it.isLetter() }?.isLowerCase() == true
        }.count() * 100 / samples.size
        val length = samples.map { it.length }.sorted()[samples.size / 2]
        val examples = Json.encodeToString(samples.take(12).map { it.take(180) })
        return resources.prompt(
            "ru/conversation/style-examples",
            mapOf(
                "length" to (length),
                "lowercase" to (lowercase),
                "examples" to (examples),
            ),
        )
    }

    private fun boilerplate(text: String): Boolean = resources.regex("intent/style-boilerplate").containsMatchIn(text)
}
