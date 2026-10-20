package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import ru.boltunya.ai.model.ModelResponseStream
import ru.boltunya.exception.impl.EngineUnavailableException
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
    fun `thinking chunks reset inactivity so total generation may exceed the idle timeout`() {
        val chunks = Flux.interval(Duration.ofMillis(100)).take(20).map { index ->
            val message = AssistantMessage.builder()
                .content(if (index == 19L) "Готовый ответ" else "")
                .properties(mapOf("thinking" to if (index < 19L) "мысль " else ""))
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
