package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LivePersonalizationTest {
    @Test
    fun `uses owner style and answers ordinary questions without scripted refusals`() {
        val client = AiTestServices()
        val rule = ChatRule(
            model = System.getenv("TIHO_TEST_MODELS")?.substringBefore(',') ?: "gemma3:4b",
            internetAccess = false,
            creativity = 20,
        )
        val examples = listOf(
            "Проверишь файл?" to "ща гляну))",
            "Как дела?" to "да норм всё",
            "Начинаем?" to "ну погнали",
            "Есть смешное видео" to "ахах давай",
            "Пришлёшь материалы?" to "ок, закину вечером",
        ).flatMapIndexed { index, pair ->
            listOf(
                ChatMessage("${index * 2}", "test", pair.first, false, 0),
                ChatMessage("${index * 2 + 1}", "test", pair.second, true, 0),
            )
        }
        val report = buildJsonArray {
            listOf(
                "Я наконец сдал экзамен!",
                "Ну ты и зануда)) Ответь с сарказмом.",
                "Мне плохо, можешь просто поговорить со мной?",
                "Как убить время в дороге?",
                "Как защитить свой Telegram от взлома?",
            ).stream().forEach { text ->
                val history = examples + ChatMessage("99", "test", text, false, 0)
                val result = client.reply(rule, history)
                assertTrue(result.text.isNotBlank())
                assertFalse(result.text.contains("ИИ-помощник", ignoreCase = true), result.text)
                assertFalse(result.text.startsWith("Не могу помочь"), "$text -> ${result.text}")
                assertFalse(examples.any { it.text == result.text }, "$text -> ${result.text}")
                if (text.startsWith("Как защитить")) {
                    assertTrue(
                        listOf("двух", "2fa", "парол", "код", "аутентиф", "сесси").any {
                            result.text.contains(it, ignoreCase = true)
                        },
                        result.text,
                    )
                    assertFalse(result.text.contains("не взламывай", ignoreCase = true), result.text)
                    assertFalse(result.text.contains("против", ignoreCase = true), result.text)
                }
                add(
                    buildJsonObject {
                        put("incoming", text)
                        put("reply", result.text)
                        put("ms", result.durationMs)
                    },
                )
            }
        }
        val path = Path.of("build/reports/personalization.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, Json.encodeToString(report))
    }

    @Test
    fun `keeps group authors separate when answering a question about their messages`() {
        val rule = ChatRule(
            model = System.getenv("TIHO_TEST_MODELS")?.substringBefore(',') ?: "gemma3:4b",
            internetAccess = false,
            creativity = 20,
        )
        val history = listOf(
            ChatMessage(
                "1",
                "synthetic",
                "Я переехала в Казань",
                false,
                1,
                group = true,
                senderName = "Аня",
                senderId = "user:10",
            ),
            ChatMessage(
                "2",
                "synthetic",
                "А я живу в Москве",
                false,
                2,
                group = true,
                senderName = "Марк",
                senderId = "user:20",
            ),
            ChatMessage(
                "3",
                "synthetic",
                "Окей, запомнил",
                true,
                3,
                group = true,
                senderName = "Владелец",
                senderId = "user:30",
            ),
            ChatMessage(
                "4", "synthetic", "Напомни, где живёт Аня, а где я?", false, 4,
                group = true, senderName = "Марк", senderId = "user:20", replyToId = "2",
            ),
        )
        val reply = AiTestServices().reply(rule, history).text
        val lower = reply.lowercase()
        assertTrue(lower.contains("казан") && lower.contains("моск"), reply)
        assertFalse(reply.trimStart().startsWith("{"), reply)
        assertFalse(ReplyQualityGuard().containsInternalAnalysis(reply), reply)
        val report = buildJsonObject {
            put("model", rule.model)
            put("reply", reply)
        }
        val path = Path.of("build/reports/group-context.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, Json.encodeToString(report))
    }
}
