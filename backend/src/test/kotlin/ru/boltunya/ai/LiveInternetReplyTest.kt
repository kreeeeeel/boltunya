package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.ai.research.PublicPageReader
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_INTERNET", matches = "true")
class LiveInternetReplyTest {
    @Test
    fun `answers current weather forecast contextual city and web questions using live providers`() {
        val client = AiTestServices()
        val rule = ChatRule(model = "gemma3:4b", maxTokens = 192, creativity = 30)
        val report = buildJsonArray {
            listOf(
                "Как погода?",
                "Какая сейчас погода в Москве?",
                "А завтра?",
                "Найди последнюю стабильную версию Kotlin на официальном сайте и дай ссылку",
            ).stream().forEach { incoming ->
                val history = if (incoming == "А завтра?") {
                    listOf(ChatMessage("0", "test", "Какая погода в Москве?", false, 0))
                } else {
                    emptyList()
                }
                val reply = client.reply(rule, history + ChatMessage("1", "test", incoming, false, 0))
                assertTrue(reply.text.isNotBlank())
                assertFalse(reply.text.contains("ИИ-помощник"), reply.text)
                add(
                    buildJsonObject {
                        put("incoming", incoming)
                        put("reply", reply.text)
                        put("durationMs", reply.durationMs)
                    },
                )
                if (incoming == "Как погода?") {
                    assertTrue(reply.text.lowercase().contains("город"), reply.text)
                    assertFalse(reply.text.contains("°"), reply.text)
                } else if (incoming.contains("ссылку")) {
                    assertTrue(reply.text.contains("https://"), reply.text)
                } else {
                    assertFalse(reply.text.contains("https://"), reply.text)
                }
                if (incoming == "А завтра?") {
                    assertFalse(reply.text.contains(LocalDate.now().toString()), reply.text)
                }
                if (incoming.startsWith("Найди последнюю стабильную")) {
                    val page = PublicPageReader().read("https://kotlinlang.org/docs/releases.html")
                    val version = assertNotNull(Regex("Latest Kotlin version:\\s*(\\d+\\.\\d+\\.\\d+)").find(page))
                    assertTrue(reply.text.contains(version.groupValues[1]), reply.text)
                }
            }
        }
        val output = Path.of("build/reports/internet-replies.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, Json.encodeToString(report))
    }
}
