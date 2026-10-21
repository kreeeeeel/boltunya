package ru.aigram.ai.model

import jakarta.inject.Inject
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.converter.ThinkingTagCleaner
import org.springframework.stereotype.Component
import java.time.Duration

private const val EVALUATION_DURATION = "eval-duration"
private const val TOTAL_DURATION = "total-duration"
private const val THINKING = "thinking"
private const val REASONING_CONTENT = "reasoningContent"
private val THINKING_TAGS = listOf("<think>", "<thinking>", "<reasoning>")

@Component
class ModelReplyReader @Inject constructor() {
    private val cleaner = ThinkingTagCleaner()

    fun read(response: ChatResponse, elapsedMs: Long): ModelCompletion {
        val generation = response.result
        val cleaned = cleaner.clean(generation?.output?.text).orEmpty()
        // The SDK removes complete legacy tags. An unfinished tag has no final answer after it.
        val unfinished = THINKING_TAGS.stream().mapToInt { cleaned.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }.min().orElse(cleaned.length)
        val text = cleaned.take(unfinished).trim()
        val evaluation = response.metadata.get<Duration>(EVALUATION_DURATION)?.toNanos() ?: 0L
        val tokens = response.metadata.usage.completionTokens
        val speed = if (evaluation > 0) tokens * 1e9 / evaluation else 0.0
        val duration = response.metadata.get<Duration>(TOTAL_DURATION)?.toMillis() ?: elapsedMs
        val thinking = (generation?.output?.metadata?.get(THINKING) as? String)
            .orEmpty().ifEmpty {
                (generation?.metadata?.get<Any>(THINKING) as? String).orEmpty()
            }.ifEmpty {
                (generation?.output?.metadata?.get(REASONING_CONTENT) as? String).orEmpty()
            }
        return ModelCompletion(
            text, duration, speed, generation?.metadata?.finishReason.orEmpty(),
            generation?.output?.text.orEmpty(), thinking,
            response.metadata.usage.promptTokens, tokens,
            response.metadata.get<Duration>("load-duration")?.toMillis() ?: 0,
            response.metadata.get<Duration>("prompt-eval-duration")?.toMillis() ?: 0,
        )
    }
}
