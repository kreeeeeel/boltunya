package ru.boltunya.ai

import jakarta.inject.Inject
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
        return resources.prompt(
            "ru/conversation/style-profile",
            mapOf("lowercase" to lowercase),
        )
    }

    private fun boilerplate(text: String): Boolean = resources.regex("intent/style-boilerplate").containsMatchIn(text)
}
