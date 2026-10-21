package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import ru.aigram.ai.model.ModelReplyReader
import ru.aigram.ai.model.ModelResponseStream
import ru.aigram.automation.activity.AttemptTrace
import ru.aigram.domain.events.ActivityAttempt
import ru.aigram.exception.impl.EngineUnavailableException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelResponseStreamTest {
    @Test
    fun `cumulative provider reasoning survives an empty final snapshot and remains outside streamed content`() {
        val trace = AttemptTrace(ActivityAttempt(1, "reply", "synthetic", emptyList(), "{}", 0))
        val chunks = listOf(
            AssistantMessage.builder().content("").properties(mapOf("reasoningContent" to "First ")).build(),
            AssistantMessage.builder().content("Готовый ответ")
                .properties(mapOf("reasoningContent" to "First second")).build(),
            AssistantMessage.builder().content("").properties(mapOf("reasoningContent" to "")).build(),
        ).map { ChatResponse(listOf(Generation(it))) }
        val response = ModelResponseStream().read(Flux.fromIterable(chunks), trace)
        val result = ModelReplyReader().read(response, 100)
        assertEquals("Готовый ответ", result.text)
        assertEquals("First second", result.thinking)
        assertEquals("Готовый ответ", trace.snapshot().response)
        assertEquals("First second", trace.snapshot().thinking)
    }

    @Test
    fun `generation metadata reasoning chunks are retained without fabricating an answer`() {
        val chunks = listOf("First ", "second").map { fragment ->
            ChatResponse(
                listOf(
                    Generation(
                        AssistantMessage.builder().content("").build(),
                        ChatGenerationMetadata.builder().metadata("thinking", fragment).build(),
                    ),
                ),
            )
        }
        val result = ModelReplyReader().read(ModelResponseStream().read(Flux.fromIterable(chunks)), 100)
        assertEquals("", result.text)
        assertEquals("First second", result.thinking)
    }

    @Test
    fun `usage only final chunk retains finish reason reasoning and refusal metadata`() {
        val first = ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder().content("Answer").properties(
                        mapOf(
                            "reasoningContent" to "Think",
                            "refusal" to "declined",
                        ),
                    ).build(),
                    ChatGenerationMetadata.builder().finishReason("STOP").build(),
                ),
            ),
        )
        val usage = ChatResponse(emptyList(), ChatResponseMetadata.builder().usage(DefaultUsage(7, 3)).build())
        val response = ModelResponseStream().read(Flux.just(first, usage))
        assertEquals("Answer", response.result?.output?.text)
        assertEquals("STOP", response.result?.metadata?.finishReason)
        assertEquals("Think", response.result?.output?.metadata?.get("thinking"))
        assertEquals("declined", response.result?.output?.metadata?.get("refusal"))
        assertEquals(7, response.metadata.usage.promptTokens)
    }

    @Test
    fun `explicit total deadline stops a stream even when it continues emitting chunks`() {
        val chunks = Flux.interval(Duration.ofMillis(20)).map {
            ChatResponse(listOf(Generation(AssistantMessage("a"))))
        }
        assertFailsWith<EngineUnavailableException> {
            ModelResponseStream().read(
                chunks,
                idleTimeout = Duration.ofSeconds(2),
                totalTimeout = Duration.ofMillis(100),
            )
        }
    }

    @Test
    fun `thinking chunks reset inactivity so total generation may exceed the idle timeout`() {
        val chunks = Flux.interval(Duration.ofMillis(100)).take(20).map { index ->
            val message = AssistantMessage.builder()
                .content(
                    if (index == 19L) {
                        "Готовый ответ"
                    } else {
                        ""
                    },
                )
                .properties(
                    mapOf(
                        "thinking" to if (index < 19L) {
                            "мысль "
                        } else {
                            ""
                        },
                    ),
                )
                .build()
            ChatResponse(listOf(Generation(message)))
        }
        val response = ModelResponseStream(Duration.ofSeconds(1)).read(chunks)
        assertEquals("Готовый ответ", response.result?.output?.text)
        assertEquals("мысль ".repeat(19), response.result?.output?.metadata?.get("thinking"))
    }

    @Test
    fun `an inactive model fails explicitly instead of occupying the queue forever`() {
        val exception = assertFailsWith<EngineUnavailableException> {
            ModelResponseStream(Duration.ofMillis(50)).read(Flux.never())
        }
        assertTrue(exception.cause is TimeoutException)
    }

    @Test
    fun `interrupt cancels an untraced stalled stream and releases the worker`() {
        val subscribed = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val future = worker.submit {
                ModelResponseStream().read(
                    Flux.never<ChatResponse>().doOnSubscribe { subscribed.countDown() }
                        .doOnCancel { cancelled.countDown() },
                )
            }
            assertTrue(subscribed.await(3, TimeUnit.SECONDS))
            future.cancel(true)
            assertTrue(cancelled.await(3, TimeUnit.SECONDS))
            assertEquals("available", worker.submit<String> { "available" }.get(3, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
        }
    }
}
