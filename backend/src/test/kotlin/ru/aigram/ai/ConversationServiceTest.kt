package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.aigram.ai.research.ResearchPlanner
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.EmptyModelReplyException
import ru.aigram.exception.impl.InvalidModelReplyException
import ru.aigram.memory.MemoryContext
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationServiceTest {
    @Test
    fun `factual memory does not activate a language regex or an unsolicited retry`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            val text = if (calls.incrementAndGet() == 1) "привет, всё отлично" else "all good, how about you?"
            val response = "{\"message\":{\"content\":" + Json.encodeToString(text) + "}}"
            val bytes = response.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val reply = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "synthetic", "hey, how is it going?", false, 0)),
                memory = MemoryContext(ownerSummary = "Обычно пишет на русском"),
            )
            assertEquals("привет, всё отлично", reply.text)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `rounded verified currency values are regenerated and repeated inaccuracies are blocked`() {
        listOf(false, true).stream().forEach { repeatInaccuracy ->
            LiveResearchFixtures().use { fixtures ->
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                val replies = AtomicInteger()
                server.createContext("/api/chat") { exchange ->
                    val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
                    val content = if (body.containsKey("format")) {
                        """{"kind":"search","query":"официальный курс евро к рублю"}"""
                    } else if (replies.incrementAndGet() == 1 || repeatInaccuracy) {
                        "евро по ЦБ 93,77 рубля на 16.10.2026"
                    } else {
                        "евро по ЦБ 93,7693 рубля на 16.10.2026"
                    }
                    val response = "{\"message\":{\"content\":" + Json.encodeToString(content) + "}}"
                    val bytes = response.toByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                server.start()
                try {
                    val services = AiTestServices(
                        "http://127.0.0.1:${server.address.port}",
                        prompts = PromptBuilder(clock = fixtures.clock),
                        researcher = fixtures.researcher(),
                        planner = ResearchPlanner(clock = fixtures.clock),
                    )
                    val history = listOf(
                        ChatMessage("1", "synthetic", "Какой официальный курс евро к рублю?", false, 0),
                    )
                    if (repeatInaccuracy) {
                        assertFailsWith<InvalidModelReplyException> { services.reply(ChatRule(), history) }
                    } else {
                        assertTrue(services.reply(ChatRule(), history).text.contains("93,7693"))
                    }
                    assertEquals(2, replies.get())
                } finally {
                    server.stop(0)
                }
            }
        }
    }

    @Test
    fun `reasoning budget retries a truncated DeepSeek response without returning private thoughts`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val budgets = mutableListOf<Int>()
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            budgets.add(body.getValue("options").jsonObject.getValue("num_predict").jsonPrimitive.content.toInt())
            assertEquals("true", body.getValue("think").jsonPrimitive.content)
            val response = if (budgets.size == 1) {
                """{
                  "message":{"content":"","thinking":"private reasoning"},
                  "done_reason":"length","prompt_eval_count":10,"eval_count":128
                }"""
            } else {
                """{"message":{"content":"<think>private reasoning</think>Нормально, а ты?"},"eval_count":10,"eval_duration":100000000}"""
            }
            val bytes = response.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(model = "deepseek-r1:8b", maxTokens = 128, internetAccess = false),
                listOf(ChatMessage("1", "test", "Как дела?", false, 0)),
            )
            assertEquals(listOf(2176, 4608), budgets)
            assertEquals("Нормально, а ты?", result.text)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `repeated reasoning without an answer fails explicitly after one bounded retry`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            calls.incrementAndGet()
            val bytes = """{
              "message":{"content":"","thinking":"private reasoning"},
              "done_reason":"length","prompt_eval_count":10,"eval_count":128
            }"""
                .toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertFailsWith<EmptyModelReplyException> {
                AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                    ChatRule(model = "deepseek-r1:8b", internetAccess = false),
                    listOf(ChatMessage("1", "test", "Как дела?", false, 0)),
                )
            }
            assertEquals(2, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sends bounded history with thinking disabled and measures response speed`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/api/chat") { exchange ->
            requestBody = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
            val bytes = """{"message":{"content":"Привет!"},"eval_count":10,"eval_duration":100000000}"""
                .toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
            val result = client.reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "42", "Как дела?", false, 0)),
            )
            val body = Json.parseToJsonElement(requestBody).jsonObject
            assertEquals("false", body["think"]?.jsonPrimitive?.content)
            assertEquals("20m", body["keep_alive"]?.jsonPrimitive?.content)
            val incoming = body["messages"]?.jsonArray?.last()?.jsonObject?.get(
                "content",
            )?.jsonPrimitive?.content.orEmpty()
            assertTrue(incoming.contains("Собеседник"))
            assertTrue(incoming.endsWith("Как дела?"))
            assertEquals("Привет!", result.text)
            assertEquals(100.0, result.tokensPerSecond)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `repeated legitimate phrases are returned without similarity rejection or an extra generation`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            calls.incrementAndGet()
            val bytes = """{"message":{"content":"да, договорились"},"done_reason":"stop"}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
            val result = client.reply(
                ChatRule(internetAccess = false),
                listOf(
                    ChatMessage("1", "42", "да, договорились", true, 0, automated = true),
                    ChatMessage("2", "42", "Так завтра в то же время?", false, 0),
                ),
            )
            assertEquals("да, договорились", result.text)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unavailable engine produces an explicit integration error`() {
        val client = AiTestServices("http://127.0.0.1:1")
        assertFailsWith<IntegrationException> { client.installed() }
    }

    @Test
    fun `thinking only Qwen uses a separate reasoning channel and never returns it as final text`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            assertEquals("true", body.getValue("think").jsonPrimitive.content)
            assertEquals("4224", body.getValue("options").jsonObject.getValue("num_predict").jsonPrimitive.content)
            val bytes =
                """{
                  "message":{
                    "thinking":"Мне нужно написать ответ от лица владельца Telegram",
                    "content":"500 г творога, яйцо и немного муки. Слепи сырники и обжарь."
                  },"done_reason":"stop"
                }""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(model = "qwen3:4b", internetAccess = false),
                listOf(ChatMessage("1", "synthetic", "напиши рецепт сырников", false, 0)),
            )
            assertTrue(result.text.startsWith("500 г творога"))
            assertFalse(result.text.contains("владельца"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `final text is not classified by its wording`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        val answer = "Мне нужно написать ответ коллеге, потом отправлю файл."
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            calls.incrementAndGet()
            val bytes = ("{\"message\":{\"content\":" + Json.encodeToString(answer) + "}}").toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val reply = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "42", "Когда отправишь файл?", false, 0)),
            )
            assertEquals(answer, reply.text)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `truncated nonempty final text gets a larger completion budget instead of being sent unfinished`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val budgets = mutableListOf<Int>()
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            budgets.add(body.getValue("options").jsonObject.getValue("num_predict").jsonPrimitive.content.toInt())
            val response = if (budgets.size == 1) {
                """{"message":{"content":"Смешай творог с"},"done_reason":"length","prompt_eval_count":10,"eval_count":32}"""
            } else {
                """{"message":{"content":"Смешай творог с яйцом и мукой, слепи сырники и обжарь."},"done_reason":"stop","prompt_eval_count":10,"eval_count":40}"""
            }
            val bytes = response.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(maxTokens = 32, internetAccess = false),
                listOf(ChatMessage("1", "synthetic", "напиши рецепт сырников", false, 0)),
            )
            assertEquals(listOf(32, 512), budgets)
            assertTrue(result.text.endsWith("обжарь."))
        } finally {
            server.stop(0)
        }
    }
}
