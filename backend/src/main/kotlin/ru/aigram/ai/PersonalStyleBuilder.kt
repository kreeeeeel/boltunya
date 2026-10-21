package ru.aigram.ai

import jakarta.inject.Inject
import ru.aigram.domain.ChatMessage
import ru.aigram.resources.ResourceCatalog

private val resources = ResourceCatalog()
private const val MAX_SAMPLE_CHARACTERS = 600
private const val MAX_STYLE_SAMPLES = 40L
private const val MIN_STYLE_SAMPLES = 3

class PersonalStyleBuilder @Inject constructor() {
    fun build(history: List<ChatMessage>): String {
        val samples = history.asReversed().stream()
            .filter { it.outgoing && !it.automated && !it.contextExcluded && it.kind == "text" && it.text.isNotBlank() }
            .map { it.text.trim() }
            .filter { it.length <= MAX_SAMPLE_CHARACTERS }
            .distinct().limit(MAX_STYLE_SAMPLES).toList()
        if (samples.size < MIN_STYLE_SAMPLES) {
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
}
