package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.domain.memory.ParticipantIdentity
import ru.aigram.telegram.TelegramOutgoingEntities
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LIVE_CONVERSATION_MODEL = "deepseek-r1:8b"

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveConversationOutputTest {
    @Test
    fun `installed reasoning model returns final conversation text and valid optional formatting`() {
        val ai = AiTestServices(System.getenv("TIHO_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434")
        assumeTrue(LIVE_CONVERSATION_MODEL in ai.installed(), "Live model must already be installed")
        val rule = ChatRule(
            model = LIVE_CONVERSATION_MODEL,
            useDefaultModel = false,
            maxTokens = 256,
            creativity = 10,
            style = "concise",
            internetAccess = false,
            useMemory = false,
        )
        val decoder = ConversationOutputDecoder()
        val native = TelegramOutgoingEntities()
        val report = mutableListOf<JsonObject>()
        try {
            Stream.of("casual-context", "requested-formatting", "irrelevant-participants").forEach { scenario ->
                val formatted = scenario == "requested-formatting"
                val unrelated = scenario == "irrelevant-participants"
                val started = System.nanoTime()
                var entityCount = 0
                var generated = false
                var valid = false
                try {
                    val memory = if (unrelated) {
                        MemoryContext(
                            participants = listOf(
                                ParticipantIdentity("user:300", "Никандра Синтетическая", "synthetic_nikandra"),
                                ParticipantIdentity("user:400", "Квирино Тестовый", "synthetic_quirino"),
                            ),
                        )
                    } else {
                        MemoryContext()
                    }
                    val result = ai.conversation.reply(
                        rule.copy(useMemory = unrelated),
                        history(formatted, unrelated),
                        memory = memory,
                        formatReplies = formatted,
                    )
                    generated = true
                    entityCount = result.entities.size
                    assertTrue(result.text.isNotBlank(), "Live conversation returned no final text")
                    assertEquals(result.text, decoder.decode(result.text), "Service JSON escaped the output decoder")
                    assertFalse(
                        result.text.contains("<think", ignoreCase = true),
                        "Raw thinking tag reached final text",
                    )
                    assertFalse(
                        result.text.contains("</think", ignoreCase = true),
                        "Raw thinking tag reached final text",
                    )
                    native.format(result.text, result.entities)
                    if (unrelated) {
                        Stream.of("никандр", "квирин", "synthetic_nikandra", "synthetic_quirino").forEach { name ->
                            assertFalse(
                                result.text.contains(name, ignoreCase = true),
                                "Unrelated synthetic participant reached the answer",
                            )
                        }
                    }
                    valid = true
                } finally {
                    report.add(
                        buildJsonObject {
                            put("case", scenario)
                            put("model", LIVE_CONVERSATION_MODEL)
                            put("formatReplies", formatted)
                            put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                            put("entityCount", entityCount)
                            put("generated", generated)
                            put("valid", valid)
                        },
                    )
                }
            }
        } finally {
            val path = Path.of("build/reports/live-conversation-output.json")
            Files.createDirectories(path.parent)
            Files.writeString(path, Json.encodeToString(buildJsonArray { report.stream().forEach { add(it) } }))
        }
    }

    private fun history(formatted: Boolean, unrelated: Boolean): List<ChatMessage> {
        val date = Instant.parse("2026-10-21T12:00:00Z").epochSecond
        val question = if (formatted) {
            "Покажи короткий пример кода print('ok') и спрячь слово секрет в спойлере."
        } else {
            "Как встреча?"
        }
        val previous = if (unrelated) {
            listOf(
                ChatMessage(
                    "old-1",
                    "synthetic-conversation",
                    "Никандра Синтетическая прислала прошлогодний отчёт",
                    false,
                    date - 2,
                    senderId = "user:300",
                    senderName = "Никандра Синтетическая",
                    senderUsername = "synthetic_nikandra",
                ),
                ChatMessage(
                    "old-2",
                    "synthetic-conversation",
                    "Квирино Тестовый давно уехал в отпуск",
                    true,
                    date - 1,
                    senderId = "user:100",
                    senderName = "Synthetic Owner",
                    senderUsername = "synthetic_owner",
                ),
            )
        } else {
            emptyList()
        }
        return previous + listOf(
            ChatMessage(
                "1",
                "synthetic-conversation",
                "Во сколько встречаемся?",
                false,
                date,
                senderId = "user:200",
                senderName = "Synthetic Friend",
                senderUsername = "synthetic_friend",
            ),
            ChatMessage(
                "2",
                "synthetic-conversation",
                "встреча в 18:30",
                true,
                date + 1,
                senderId = "user:100",
                senderName = "Synthetic Owner",
                senderUsername = "synthetic_owner",
            ),
            ChatMessage(
                "3",
                "synthetic-conversation",
                question,
                false,
                date + 2,
                senderId = "user:200",
                senderName = "Synthetic Friend",
                senderUsername = "synthetic_friend",
            ),
        )
    }
}
