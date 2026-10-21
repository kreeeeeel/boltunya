package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityEntry
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelActivityTraceTest {
    @Test
    fun `untraced drafts and memory also aggregate streamed thinking separately from final text`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            val body = String(exchange.requestBody.readAllBytes())
            assertTrue(body.contains("\"stream\":true"))
            exchange.responseHeaders.set("Content-Type", "application/x-ndjson")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                listOf(
                    """{"message":{"role":"assistant","content":"","thinking":"Отдельное рассуждение"},"done":false}""",
                    """{"message":{"role":"assistant","content":"Готовый ответ"},"done":true,"done_reason":"stop"}""",
                ).stream().forEach {
                    output.write((it + "\n").toByteArray())
                    output.flush()
                }
            }
        }
        server.start()
        try {
            val ai = AiTestServices("http://127.0.0.1:${server.address.port}")
            val result = ai.completion.generate(ChatRule(), listOf(UserMessage("Привет")))
            assertEquals("Готовый ответ", result.text)
            assertEquals("Отдельное рассуждение", result.thinking)
            assertNull(ai.tracing.current())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `SDK streaming captures prompt thinking usage and final response independently`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            val body = String(exchange.requestBody.readAllBytes())
            assertTrue(body.contains("\"stream\":true"))
            exchange.responseHeaders.set("Content-Type", "application/x-ndjson")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                listOf(
                    """{"model":"deepseek-r1:1.5b","message":{"role":"assistant","content":"","thinking":"Нужно ответить коротко. "},"done":false}""",
                    """{"model":"deepseek-r1:1.5b","message":{"role":"assistant","content":"Привет!","thinking":"Без лишних слов."},"done":false}""",
                    """{"model":"deepseek-r1:1.5b","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":42,"eval_count":8,"eval_duration":1000000000,"total_duration":1500000000,"load_duration":100000000,"prompt_eval_duration":200000000}""",
                ).stream().forEach {
                    output.write((it + "\n").toByteArray())
                    output.flush()
                }
            }
        }
        server.start()
        try {
            val ai = AiTestServices("http://127.0.0.1:${server.address.port}")
            val trace = ReplyTrace(ActivityEntry("test", "42", "Test", "generating", "", 0), "Привет")
            val messages = listOf(SystemMessage("Короткий ответ"), UserMessage("Привет"))
            val result = ai.tracing.capture(trace) {
                ai.completion.generate(ChatRule(model = "deepseek-r1:1.5b"), messages)
            }
            assertEquals("Привет!", result.text)
            assertEquals("Нужно ответить коротко. Без лишних слов.", result.thinking)
            val attempt = trace.details().attempts.single()
            assertEquals(listOf("system", "user"), attempt.messages.map { it.role })
            assertEquals("Короткий ответ", attempt.messages.first().text)
            assertEquals(42, attempt.inputTokens)
            assertEquals(8, attempt.outputTokens)
            assertEquals(1500L, attempt.durationMs)
            assertEquals(8.0, attempt.tokensPerSecond)
            assertEquals(100L, attempt.loadDurationMs)
            assertEquals(200L, attempt.promptDurationMs)
            assertEquals("stop", attempt.finishReason)
            assertEquals("Привет!", attempt.response)
            assertTrue(attempt.complete)
            assertNull(ai.tracing.current())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `cancelling inference preserves chunks and frees worker`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val chunkSent = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            exchange.responseHeaders.set("Content-Type", "application/x-ndjson")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write(
                    (
                        """{"model":"deepseek-r1:1.5b","message":{"role":"assistant","content":"",""" +
                            """"thinking":"Начало рассуждения"},"done":false}""" +
                            "\n"
                        ).toByteArray(),
                )
                output.flush()
                chunkSent.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        server.start()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val ai = AiTestServices("http://127.0.0.1:${server.address.port}")
            val trace = ReplyTrace(ActivityEntry("test", "42", "Test", "generating", "", 0), "Привет")
            val future = worker.submit {
                ai.tracing.capture(trace) {
                    ai.completion.generate(ChatRule(model = "deepseek-r1:1.5b"), listOf(UserMessage("Привет")))
                }
            }
            assertTrue(chunkSent.await(3, TimeUnit.SECONDS))
            assertTrue(
                java.util.stream.Stream.generate {
                    Thread.sleep(10)
                    trace.details().attempts.firstOrNull()?.thinking?.isNotBlank() == true
                }.limit(100).anyMatch { it },
            )
            future.cancel(true)
            val scopeCleared = worker.submit<Boolean> { ai.tracing.current() == null }.get(2, TimeUnit.SECONDS)
            assertTrue(scopeCleared)
            assertEquals("Начало рассуждения", trace.details().attempts.single().thinking)
            assertTrue(trace.details().attempts.single().response.isEmpty())
            assertTrue(!trace.details().attempts.single().complete)
        } finally {
            release.countDown()
            server.stop(0)
            worker.shutdownNow()
        }
    }
}
