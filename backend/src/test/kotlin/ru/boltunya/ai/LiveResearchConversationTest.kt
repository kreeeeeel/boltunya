package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.NewsSearchClient
import ru.boltunya.ai.research.RecordedSearchProvider
import ru.boltunya.ai.research.ResearchMarkdown
import ru.boltunya.ai.research.ResearchTerms
import ru.boltunya.ai.research.ResearchUrl
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.resources.ResourceCatalog
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_INTERNET", matches = "true")
class LiveResearchConversationTest {
    @Test
    fun `answers ordinary weather news currency and contextual source requests with live data`() {
        val search = RecordedSearchProvider()
        val client = AiTestServices(
            researcher = InternetResearcher(search = search, clock = Clock.systemDefaultZone()),
        )
        val rule = ChatRule(model = "gemma3:4b", maxTokens = 256, creativity = 10)
        val resources = ResourceCatalog()
        val urls = ResearchUrl()
        val report = mutableListOf<JsonObject>()
        val failures = mutableListOf<String>()
        val rates = Jsoup.connect("https://www.cbr.ru/scripts/XML_daily.asp")
            .data("date_req", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .timeout(7000).ignoreContentType(true).parser(Parser.xmlParser()).get().select("Valute")
            .associate {
                it.selectFirst("CharCode")?.text() to
                    it.selectFirst("Value")?.text().orEmpty().replace(',', '.')
            }
        try {
            listOf(
                listOf("Как погода?", "Какая погода сейчас в Москве?", "А завтра?", "А в Казани?"),
                listOf("Какой сейчас курс доллара к рублю?", "А евро?", "Дай ссылку на источник"),
                listOf(
                    "Какие новости сегодня в России?",
                    "А в мире?",
                    "Дай ссылку на источник",
                    "Подробнее, но без ссылок",
                ),
            ).stream().forEach { questions ->
                search.clear()
                val history = mutableListOf<ChatMessage>()
                questions.stream().forEach { incoming ->
                    history.add(ChatMessage(history.size.toString(), "live-test", incoming, false, 0))
                    val reply = client.reply(rule, history).text
                    history.add(ChatMessage(history.size.toString(), "live-test", reply, true, 0, automated = true))
                    val links = ResearchMarkdown().links(reply)
                    val requested = incoming == "Дай ссылку на источник"
                    val currency = questions.first().contains("курс")
                    val news = questions.first().contains("новости")
                    if (incoming.contains("курс доллара") || incoming == "А евро?") {
                        val code = if (incoming == "А евро?") "EUR" else "USD"
                        val expected = rates.getValue(
                            code,
                        ).toBigDecimal().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                        val actual = reply.replace(',', '.')
                        val precise = rates.getValue(code).toBigDecimal().stripTrailingZeros().toPlainString()
                        if (!actual.contains(expected) && !actual.contains(precise)) {
                            failures.add("Wrong $code rate: $reply, expected $expected")
                        }
                    }
                    if (news &&
                        (search.query.isBlank() || search.sources.isEmpty())
                    ) {
                        failures.add("Missing fresh news lookup: $incoming")
                    }
                    if (news &&
                        search.sources.any {
                            it.publishedAt.isBlank() ||
                                Instant.parse(it.publishedAt).isBefore(Instant.now().minusSeconds(129600))
                        }
                    ) {
                        failures.add("Undated or stale news: $incoming")
                    }
                    if (!requested && links.isNotEmpty()) failures.add("Unexpected link: $incoming")
                    if (incoming == "А в мире?" &&
                        (
                            !ResearchTerms().contains("news-world", search.query) ||
                                ResearchTerms().contains("news-russia", search.query)
                            )
                    ) {
                        failures.add("Lost news region: ${search.query}")
                    }
                    if (incoming == "А в мире?" &&
                        search.sources.any {
                            !NewsSearchClient().international(it)
                        }
                    ) {
                        failures.add("Non-world source after region change")
                    }
                    if (incoming == "А завтра?" || incoming == "А в Казани?") {
                        val tomorrow = LocalDate.now().plusDays(1)
                        if (!reply.lowercase().contains("завтра") && !reply.contains(tomorrow.toString()) &&
                            !reply.contains(tomorrow.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        ) {
                            failures.add("Lost forecast date: $incoming -> $reply")
                        }
                    }
                    if (requested && links.isEmpty()) failures.add("Missing requested source: $incoming")
                    if (links.any { link ->
                            !(
                                currency &&
                                    urls.canonical(link) == "https://www.cbr.ru/currency_base/daily/"
                                ) &&
                                search.sources.none { urls.canonical(it.url) == urls.canonical(link) }
                        }
                    ) {
                        failures.add("Unverified source: $incoming")
                    }
                    report.add(
                        buildJsonObject {
                            put("incoming", incoming)
                            put("reply", reply)
                            put("query", search.query)
                            put("sources", Json.encodeToJsonElement(search.sources))
                        },
                    )
                }
            }
        } finally {
            val path = Path.of("build/reports/live-research-conversations.json")
            Files.createDirectories(path.parent)
            Files.writeString(path, Json.encodeToString(buildJsonArray { report.stream().forEach { add(it) } }))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
