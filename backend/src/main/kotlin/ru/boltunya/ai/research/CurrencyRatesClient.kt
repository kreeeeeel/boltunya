package ru.boltunya.ai.research

import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ru.boltunya.exception.impl.CurrencyRatesUnavailableException
import ru.boltunya.resources.ResourceCatalog
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val CBR_ENDPOINT = "https://www.cbr.ru/scripts/XML_daily.asp"
private const val CBR_SOURCE = "https://www.cbr.ru/currency_base/daily/"
private const val TIMEOUT_MS = 7000
private const val MAX_BODY_BYTES = 256 * 1024
private const val MAX_RATE_AGE_DAYS = 14L
private const val RATE_SCALE = 4
private val QUERY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val RESPONSE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class CurrencyRatesClient(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val endpoint: String = CBR_ENDPOINT,
    private val resources: ResourceCatalog = ResourceCatalog(),
    private val intents: ResearchTerms = ResearchTerms(resources),
    private val words: ResearchWords = ResearchWords(),
) {
    private val aliases = Json.decodeFromString<Map<String, List<String>>>(resources.text("search/currencies.json"))
    private val names = Json.decodeFromString<Map<String, String>>(resources.text("search/currency-names.json"))
    private val symbols = Json.decodeFromString<Map<String, String>>(resources.text("search/currency-symbols.json"))

    fun lookup(query: String): ResearchContext? {
        if (!intents.contains("currency-rate", query) ||
            intents.contains("news", query)
        ) {
            return null
        }
        if (intents.contains("currency-market", query)) return null
        val currencies = words.tokens(query)
            .mapNotNull { token ->
                aliases.entries.firstOrNull { entry ->
                    entry.value.any { token.startsWith(it) }
                }?.key
            }.distinct().toList()
        if (currencies.isEmpty() && !intents.contains("currency-general", query)) return null
        val requested = currencies.ifEmpty { listOf("USD", "EUR") }
        val pairs = if (requested.size > 1 && !intents.contains("currency-list", query)) {
            listOf(requested[0] to requested[1])
        } else {
            requested.filterNot { it == "RUB" }.map { it to "RUB" }
        }
        if (pairs.isEmpty()) return null
        try {
            val today = LocalDate.now(clock)
            val response = Jsoup.connect(endpoint).data("date_req", today.format(QUERY_DATE))
                .timeout(
                    TIMEOUT_MS,
                ).maxBodySize(MAX_BODY_BYTES).ignoreContentType(true).parser(Parser.xmlParser()).get()
            val root = response.selectFirst("ValCurs") ?: throw CurrencyRatesUnavailableException()
            val date = LocalDate.parse(root.attr("Date"), RESPONSE_DATE)
            if (date.isAfter(today) ||
                date.isBefore(today.minusDays(MAX_RATE_AGE_DAYS))
            ) {
                throw CurrencyRatesUnavailableException()
            }
            val rates = response.select("Valute").associate { row ->
                val nominal =
                    row.selectFirst("Nominal")?.text()?.toBigDecimalOrNull()
                        ?: throw CurrencyRatesUnavailableException()
                val value =
                    row.selectFirst("Value")?.text()?.replace(',', '.')?.toBigDecimalOrNull()
                        ?: throw CurrencyRatesUnavailableException()
                if (nominal <= BigDecimal.ZERO || value <= BigDecimal.ZERO) throw CurrencyRatesUnavailableException()
                row.selectFirst("CharCode")?.text().orEmpty() to value.divide(nominal, 10, RoundingMode.HALF_UP)
            } + ("RUB" to BigDecimal.ONE)
            if (pairs.any { it.first !in rates || it.second !in rates }) return null
            val quotes = pairs.map { (base, target) ->
                val rate = rates.getValue(base).divide(rates.getValue(target), RATE_SCALE, RoundingMode.HALF_UP)
                val amount = rate.stripTrailingZeros().toPlainString()
                amount to resources.prompt(
                    "ru/research/currency-quote",
                    mapOf(
                        "base" to names.getValue(base),
                        "rate" to amount,
                        "target" to symbols.getValue(target),
                    ),
                )
            }
            val facts = resources.prompt(
                "ru/research/currency",
                mapOf("date" to date, "quotes" to quotes.joinToString("; ") { (_, text) -> text }),
            )
            return ResearchContext(
                "currency",
                facts,
                listOf(ResearchSource("Банк России", CBR_SOURCE, facts, date.toString())),
                requiredNumbers = quotes.map { (amount, _) -> amount },
            )
        } catch (exception: CurrencyRatesUnavailableException) {
            throw exception
        } catch (exception: IOException) {
            throw CurrencyRatesUnavailableException(exception)
        } catch (exception: DateTimeParseException) {
            throw CurrencyRatesUnavailableException(exception)
        }
    }
}
