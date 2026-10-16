package ru.boltunya.ai.model

import jakarta.inject.Inject
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.converter.ThinkingTagCleaner
import ru.boltunya.resources.ResourceCatalog
import java.time.Duration

private const val EVALUATION_DURATION = "eval-duration"
private const val TOTAL_DURATION = "total-duration"

class ModelReplyReader @Inject constructor(private val resources: ResourceCatalog) {
    private val cleaner = ThinkingTagCleaner()

    fun read(response: ChatResponse, elapsedMs: Long): ModelCompletion {
        val generation = response.result
        val cleaned = cleaner.clean(generation?.output?.text).orEmpty()
        val text = resources.regex("common/ollama-thinking").replace(cleaned, "").trim()
        val evaluation = response.metadata.get<Duration>(EVALUATION_DURATION)?.toNanos() ?: 0L
        val tokens = response.metadata.usage.completionTokens
        val speed = if (evaluation > 0) tokens * 1e9 / evaluation else 0.0
        val duration = response.metadata.get<Duration>(TOTAL_DURATION)?.toMillis() ?: elapsedMs
        return ModelCompletion(
            text, duration, speed, generation?.metadata?.finishReason.orEmpty(),
            generation?.output?.text.orEmpty(), generation?.output?.metadata?.get("thinking") as? String ?: "",
            response.metadata.usage.promptTokens, tokens,
            response.metadata.get<Duration>("load-duration")?.toMillis() ?: 0,
            response.metadata.get<Duration>("prompt-eval-duration")?.toMillis() ?: 0,
        )
    }
}
