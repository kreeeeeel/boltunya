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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveReplyQualityTest {
    @Test
    fun `answers casual dialogue without assistant boilerplate or loops`() {
        val clock = Clock.fixed(Instant.parse("2026-10-15T18:30:00Z"), ZoneId.of("Europe/Moscow"))
        val client = AiTestServices(prompts = PromptBuilder(clock))
        val model = System.getenv("TIHO_QUALITY_MODEL") ?: "gemma3:4b"
        org.junit.jupiter.api.Assumptions.assumeTrue(
            model in client.installed(),
            "Selected live model must be installed explicitly",
        )
        val rule = ChatRule(model = model, maxTokens = 128, creativity = 40, internetAccess = false)
        val cases = listOf(
            "Кто лучшая сестра на свете?",
            "Сколько сейчас времени?",
            "Ахаххаха",
            "А можешь вопросы позадавать?",
            "Как зовут мою сестру?",
            "Чем git merge отличается от rebase? Коротко",
        )
        val report = buildJsonArray {
            cases.forEach { incoming ->
                val facts = if (incoming == "Как зовут мою сестру?") {
                    listOf(ChatMessage("0", "test", "Мою сестру зовут Даша", false, 0))
                } else {
                    emptyList()
                }
                val history = facts + listOf(
                    ChatMessage("1", "test", "Воу, я напишу тебе что-то интересное", true, 0),
                    ChatMessage("2", "test", incoming, false, 0),
                )
                val result = client.reply(rule, history)
                assertTrue(result.text.isNotBlank())
                assertFalse(result.text.lowercase().contains("ии-помощник"), result.text)
                assertFalse(result.text.lowercase().contains("могу помочь с чем-то другим"), result.text)
                assertFalse(result.text.lowercase().contains("напишу тебе что-то интересное"), result.text)
                assertFalse(
                    result.text.codePoints().anyMatch {
                        Character.UnicodeScript.of(it) ==
                            Character.UnicodeScript.HAN
                    },
                    result.text,
                )
                when (incoming) {
                    "Кто лучшая сестра на свете?" -> {
                        assertFalse(result.text.lowercase().contains("у меня нет"), result.text)
                        assertFalse(result.text.lowercase().contains("воспоминания"), result.text)
                    }
                    "Сколько сейчас времени?" -> assertTrue(result.text.contains("21:30"), result.text)
                    "А можешь вопросы позадавать?" -> assertTrue(result.text.contains('?'), result.text)
                    "Как зовут мою сестру?" -> {
                        assertTrue(
                            listOf("даша", "дашу").any {
                                result.text.contains(it, ignoreCase = true)
                            },
                            result.text,
                        )
                    }
                }
                add(
                    buildJsonObject {
                        put("model", model)
                        put("incoming", incoming)
                        put("reply", result.text)
                        put("durationMs", result.durationMs)
                    },
                )
            }
        }
        val path = Path.of("build/reports/ai-quality.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, Json.encodeToString(report))
    }
}
