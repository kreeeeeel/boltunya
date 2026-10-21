package ru.aigram.automation.activity

import org.springframework.ai.chat.model.ChatResponse
import ru.aigram.domain.ai.model.ModelCompletion
import ru.aigram.domain.events.ActivityAttempt

class AttemptTrace(private val request: ActivityAttempt, private val retainContent: Boolean = true) {
    private val response = StringBuilder()
    private val thinking = StringBuilder()
    private var completed: ModelCompletion? = null
    private var endedAt = 0L

    @Synchronized
    fun append(chunk: ChatResponse) {
        if (!retainContent) {
            return
        }
        response.append(chunk.result?.output?.text.orEmpty())
        thinking.append(chunk.result?.output?.metadata?.get("thinking") as? String ?: "")
        val cumulativeReasoning = chunk.result?.output?.metadata?.get("reasoningContent") as? String
        if (!cumulativeReasoning.isNullOrEmpty()) {
            thinking.setLength(0)
            thinking.append(cumulativeReasoning)
        }
    }

    @Synchronized
    fun finish(result: ModelCompletion) {
        completed = if (retainContent) {
            result
        } else {
            result.copy(text = "", rawText = "", thinking = "")
        }
        endedAt = System.currentTimeMillis()
    }

    @Synchronized
    fun stop(date: Long) {
        if (endedAt == 0L) {
            endedAt = date
        }
    }

    @Synchronized
    fun thinkingPreview(): String = thinking.substring(maxOf(0, thinking.length - 360))

    @Synchronized
    fun usage(): Pair<Int, Int> = (completed?.inputTokens ?: 0) to (completed?.outputTokens ?: 0)

    @Synchronized
    fun snapshot(): ActivityAttempt {
        val result = completed
        return request.copy(
            response = result?.rawText ?: response.toString(),
            thinking = result?.thinking ?: thinking.toString(),
            durationMs =
            result?.durationMs ?: (
                (
                    if (endedAt > 0) {
                        endedAt
                    } else {
                        System.currentTimeMillis()
                    }
                    ) - request.startedAt
                ),
            inputTokens = result?.inputTokens ?: 0,
            outputTokens = result?.outputTokens ?: 0,
            tokensPerSecond = result?.tokensPerSecond ?: 0.0,
            loadDurationMs = result?.loadDurationMs ?: 0,
            promptDurationMs = result?.promptDurationMs ?: 0,
            finishReason = result?.finishReason.orEmpty(),
            complete = result != null,
        )
    }

    @Synchronized
    fun characters(): Long = request.messages.stream().mapToLong {
        it.text.length.toLong()
    }.sum() + request.options.length + response.length +
        thinking.length
}
