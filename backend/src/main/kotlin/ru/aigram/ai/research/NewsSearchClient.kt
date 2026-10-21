package ru.aigram.ai.research

import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ru.aigram.domain.ai.research.ResearchSource
import ru.aigram.resources.ResourceCatalog
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.Executors

private const val NEWS_CACHE_SECONDS = 60L
private const val MAX_NEWS_AGE_SECONDS = 36 * 60 * 60L
private const val FUTURE_TOLERANCE_SECONDS = 300L
private const val MAX_FEED_BYTES = 512 * 1024
private const val FEED_TIMEOUT_MS = 7000
private const val MAX_FEED_WORKERS = 2
private const val MAX_NEWS_RESULTS = 3
private const val MAX_NEWS_PER_HOST = 2

class NewsSearchClient(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val resources: ResourceCatalog = ResourceCatalog(),
    private val urls: ResearchUrl = ResearchUrl(),
    private val feeds: List<String> = Json.decodeFromString(resources.text("search/news-feeds.json")),
    private val intents: ResearchTerms = ResearchTerms(resources),
    private val words: ResearchWords = ResearchWords(),
) {
    private val general = Json.decodeFromString<List<String>>(resources.text("search/news-general-terms.json")).toSet()
    private var refreshedAt = Instant.MIN
    private var cached: List<ResearchSource> = emptyList()

    @Synchronized
    fun search(query: String): List<ResearchSource>? {
        if (!intents.contains("news", query)) {
            return null
        }
        val now = clock.instant()
        if (now.epochSecond - refreshedAt.epochSecond >= NEWS_CACHE_SECONDS) {
            cached = Executors.newFixedThreadPool(MAX_FEED_WORKERS).use { executor ->
                feeds.map { feed -> executor.submit<List<ResearchSource>> { read(feed) } }.flatMap { it.get() }
            }
            refreshedAt = now
        }
        val terms = tokens(query).filterNot { token -> general.any { token.startsWith(it) } }
        val world = intents.contains("news-world", query)
        val local = intents.contains("news-russia", query)
        val today = intents.contains("today", query)
        val hosts = mutableMapOf<String, Int>()
        val range = (now.epochSecond - MAX_NEWS_AGE_SECONDS)..(now.epochSecond + FUTURE_TOLERANCE_SECONDS)
        return cached.filter { source ->
            val date = Instant.parse(source.publishedAt)
            date.epochSecond in range &&
                (!today || date.atZone(clock.zone).toLocalDate() == LocalDate.now(clock))
        }.filter { source ->
            val international = international(source)
            (!world || international) && (!local || !international) &&
                (
                    terms.isEmpty() ||
                        terms.any { term ->
                            tokens(source.title + " " + source.text).any { it.startsWith(term.take(5)) }
                        }
                    )
        }.distinctBy { it.url }.sortedByDescending { it.publishedAt }.filter {
            val host = urls.host(it.url)
            val count = hosts.getOrDefault(host, 0)
            hosts[host] = count + 1
            count < MAX_NEWS_PER_HOST
        }.take(MAX_NEWS_RESULTS)
    }

    private fun tokens(text: String): List<String> =
        words.tokens(text).filter { it.length >= 2 && it.any(Char::isLetter) }

    fun international(source: ResearchSource): Boolean =
        source.url.contains("/world/") || source.url.contains("mezhdunarodnaya-panorama") ||
            intents.contains("news-world-source", source.text)

    private fun read(feed: String): List<ResearchSource> = try {
        val document = Jsoup.connect(feed).timeout(FEED_TIMEOUT_MS).maxBodySize(MAX_FEED_BYTES)
            .ignoreContentType(true).parser(Parser.xmlParser()).get()
        document.select("channel > item").mapNotNull { item ->
            val published = item.selectFirst("pubDate")?.text().orEmpty()
            if (published.isBlank()) {
                return@mapNotNull null
            }
            val date = try {
                ZonedDateTime.parse(published, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            } catch (_: DateTimeParseException) {
                return@mapNotNull null
            }
            val title = item.selectFirst("title")?.text().orEmpty()
            val url = urls.canonical(item.selectFirst("link")?.text().orEmpty())
            if (title.isBlank() || url.isBlank()) {
                return@mapNotNull null
            }
            val description = Jsoup.parse(item.selectFirst("description")?.text().orEmpty()).text()
            val categories = item.select("category").joinToString("; ") { it.text() }
            ResearchSource(
                title,
                url,
                listOf(title, description, categories).filter(String::isNotBlank).joinToString(". ").take(1800),
                date.toString(),
            )
        }
    } catch (exception: IOException) {
        System.err.println("News feed unavailable: ${exception.javaClass.simpleName}")
        emptyList()
    }
}
