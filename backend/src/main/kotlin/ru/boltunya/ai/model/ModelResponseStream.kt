package ru.boltunya.ai.model

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import ru.boltunya.automation.activity.AttemptTrace
import ru.boltunya.exception.impl.EmptyModelReplyException
import ru.boltunya.exception.impl.EngineUnavailableException
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

private val MODEL_INACTIVITY_TIMEOUT = Duration.ofMinutes(10)

/** Active local inference may run as long as needed; only a stalled stream times out. */
class ModelResponseStream(private val inactivityTimeout: Duration = MODEL_INACTIVITY_TIMEOUT) {
    fun read(
        chunks: Flux<ChatResponse>,
        attempt: AttemptTrace? = null,
        idleTimeout: Duration = inactivityTimeout,
        totalTimeout: Duration = Duration.ZERO,
    ): ChatResponse {
        val text = StringBuilder()
        val thinking = StringBuilder()
        val reasoning = AtomicReference("")
        val refusal = StringBuilder()
        val last = AtomicReference<ChatResponse>()
        val generation = AtomicReference<ChatGenerationMetadata>()
        val deadline = if (totalTimeout.isZero) Long.MAX_VALUE else System.nanoTime() + totalTimeout.toNanos()
        val timed = if (totalTimeout.isZero) {
            chunks
        } else {
            chunks.timeout(
                reactor.core.publisher.Mono.delay(totalTimeout),
            ) { reactor.core.publisher.Mono.delay(Duration.ofNanos(maxOf(1, deadline - System.nanoTime()))) }
        }
        timed.timeout(idleTimeout).doOnNext { chunk ->
            last.set(chunk)
            if (chunk.result?.metadata?.finishReason.orEmpty().isNotBlank()) {
                generation.set(chunk.result?.metadata)
            }
            text.append(chunk.result?.output?.text.orEmpty())
            thinking.append(chunk.result?.output?.metadata?.get("thinking") as? String ?: "")
            val cumulativeReasoning = chunk.result?.output?.metadata?.get("reasoningContent") as? String
            if (cumulativeReasoning != null) reasoning.set(cumulativeReasoning)
            refusal.append(chunk.result?.output?.metadata?.get("refusal") as? String ?: "")
            attempt?.append(chunk)
        }.onErrorMap(TimeoutException::class.java) { EngineUnavailableException(it) }.blockLast()
        val response = last.get() ?: throw EmptyModelReplyException()
        return ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder().content(text.toString())
                        .properties(
                            mapOf(
                                "thinking" to (thinking.toString() + reasoning.get()),
                                "refusal" to refusal.toString(),
                            ),
                        ).build(),
                    generation.get() ?: response.result?.metadata ?: ChatGenerationMetadata.NULL,
                ),
            ),
            response.metadata,
        )
    }
}
