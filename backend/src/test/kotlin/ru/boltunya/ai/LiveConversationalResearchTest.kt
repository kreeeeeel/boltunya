package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.RecordedSearchProvider
import ru.boltunya.ai.research.ResearchMarkdown
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_INTERNET", matches = "true")
class LiveConversationalResearchTest {
    @Test
    fun `retells fresh facts in the owners casual voice without changing currency values or dates`() {
        val search = RecordedSearchProvider()
        val client = AiTestServices(
            baseUrl = System.getenv("TIHO_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434",
            researcher = InternetResearcher(search = search, clock = Clock.systemDefaultZone()),
        )
        val rule = ChatRule(model = "gemma3:4b", maxTokens = 192, creativity = 10)
        val style = listOf("да норм", "ага, договорились))", "ну давай завтра", "ща гляну", "ок, тогда вечером")
            .mapIndexed { index, text -> ChatMessage("style-$index", "style", text, true, 0) }
        val today = LocalDate.now()
        val rates = Jsoup.connect("https://www.cbr.ru/scripts/XML_daily.asp")
            .data("date_req", today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .timeout(7000).ignoreContentType(true).parser(Parser.xmlParser()).get().select("Valute")
            .associate {
                it.selectFirst("CharCode")?.text() to
                    it.selectFirst("Value")?.text().orEmpty().replace(',', '.')
            }
        val resources = ResourceCatalog()
        val failures = mutableListOf<String>()
        val history = mutableListOf<ChatMessage>()
        val output = buildJsonArray {
            listOf(
                "Какая погода в Москве сейчас?",
                "Какой сейчас курс доллара к рублю?",
                "А евро?",
                "Какие новости сегодня в мире?",
            )
                .stream().forEach { incoming ->
                    history.add(ChatMessage(history.size.toString(), "live-style", incoming, false, 0))
                    val reply = client.reply(
                        rule,
                        history,
                        styleHistory = style,
                        memory = MemoryContext(
                            ownerSummary = "Пишет коротко, обычно со строчной буквы, без официальных оборотов.",
                        ),
                    ).text
                    history.add(ChatMessage(history.size.toString(), "live-style", reply, true, 0, automated = true))
                    if (reply.length > 360) failures.add("Too long: $incoming -> $reply")
                    if (incoming.contains("погода") && !reply.contains("°") && !reply.contains("градус")) {
                        failures.add("Missing weather facts: $reply")
                    }
                    if (incoming.contains("новости") && (search.query.isBlank() || search.sources.isEmpty())) {
                        failures.add("Missing verified news lookup")
                    }
                    if (ResearchMarkdown().links(reply).isNotEmpty()) {
                        failures.add("Unrequested source link")
                    }
                    if (incoming.contains("доллара") || incoming == "А евро?") {
                        val expected = rates.getValue(
                            if (incoming ==
                                "А евро?"
                            ) {
                                "EUR"
                            } else {
                                "USD"
                            },
                        ).toBigDecimal().stripTrailingZeros().toPlainString()
                        if (!reply.replace(
                                ',',
                                '.',
                            ).contains(expected)
                        ) {
                            failures.add("Changed significant digits: $reply")
                        }
                        if (listOf("USD/RUB", "EUR/RUB", "RUB/USD", "RUB/EUR").any { reply.contains(it) }) {
                            failures.add("Machine notation: $reply")
                        }
                        if (!reply.contains(today.toString()) &&
                            !reply.contains(today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        ) {
                            failures.add("Lost exact date: $reply")
                        }
                    }
                    add(
                        buildJsonObject {
                            put("incoming", incoming)
                            put("reply", reply)
                        },
                    )
                }
        }
        val file = Path.of("build/reports/conversational-research.json")
        Files.createDirectories(file.parent)
        Files.writeString(file, Json.encodeToString(output))
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
