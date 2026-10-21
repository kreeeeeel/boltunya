package ru.aigram.ai.research

import kotlinx.serialization.json.Json
import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

private const val MAX_RESULTS = 3L
private const val MAX_RESULTS_PER_HOST = 2
private const val STEM_LENGTH = 6
private const val MIN_TOKEN_LENGTH = 2
private const val MAX_SOURCE_TEXT = 5000
private const val NEWS_MAX_AGE_SECONDS = 36 * 60 * 60L
private const val NEWS_FUTURE_TOLERANCE_SECONDS = 300L

class SearchResultSelector(
    private val resources: ResourceCatalog = ResourceCatalog(),
    private val urls: ResearchUrl = ResearchUrl(),
    private val clock: Clock = Clock.systemUTC(),
    private val intents: ResearchTerms = ResearchTerms(resources),
    private val words: ResearchWords = ResearchWords(),
) {
    private val newsGeneral = Json.decodeFromString<List<String>>(
        resources.text("search/news-general-terms.json"),
    ).map(::stem)
    private val stopwords = Json.decodeFromString<List<String>>(resources.text("languages.json"))
        .flatMap { language ->
            Json.decodeFromString<List<String>>(resources.text("search/stopwords/$language.json"))
        }.map(::stem).toSet()
    private val aliases = Json.decodeFromString<Map<String, List<String>>>(resources.text("search/aliases.json"))
        .flatMap { (canonical, terms) -> (terms + canonical).map { stem(it) to canonical } }.toMap()

    fun select(query: String, candidates: List<ResearchSource>): List<ResearchSource> {
        val news = intents.contains("news", query)
        val terms = tokens(query).filterNot { token -> news && newsGeneral.any { token.startsWith(it) } }.toSet()
        val hosts = mutableMapOf<String, Int>()
        return candidates.map { it.copy(url = urls.canonical(it.url)) }
            .filter {
                it.url.isNotEmpty() && it.title.isNotBlank() && it.text.isNotBlank()
            }.distinctBy { it.url }.stream()
            .filter { !isSearchPage(it.url) }
            .filter { !news || recent(it.publishedAt) }
            .filter { terms.isEmpty() || tokens(it.title + " " + it.text).intersect(terms).isNotEmpty() }
            .sorted(compareByDescending { score(it, terms) })
            .filter {
                val host = urls.host(it.url)
                val count = hosts.getOrDefault(host, 0)
                hosts[host] = count + 1
                count < MAX_RESULTS_PER_HOST
            }.limit(MAX_RESULTS).toList()
    }

    private fun recent(value: String): Boolean {
        val date = try {
            if (value.length ==
                10
            ) {
                LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
            } else {
                Instant.parse(value)
            }
        } catch (_: DateTimeParseException) {
            return false
        }
        val age = clock.instant().epochSecond - date.epochSecond
        return age in -NEWS_FUTURE_TOLERANCE_SECONDS..NEWS_MAX_AGE_SECONDS
    }

    private fun score(source: ResearchSource, terms: Set<String>): Double {
        val title = tokens(source.title).intersect(terms).size
        val body = tokens(source.text).intersect(terms).size
        val coverage = if (terms.isEmpty()) 0.0 else body.toDouble() / terms.size
        val article = if (URI.create(source.url).path.trim('/').isEmpty()) 0.0 else 0.5
        return title * 3 + body + coverage * 4 + article
    }

    private fun tokens(text: String): Set<String> = words.tokens(text.take(MAX_SOURCE_TEXT))
        .map { aliases[stem(it)] ?: stem(it) }
        .filter { it.length >= MIN_TOKEN_LENGTH && it.any(Char::isLetter) && it !in stopwords }.toSet()

    private fun stem(value: String): String = value.lowercase(Locale.ROOT).take(STEM_LENGTH)

    private fun isSearchPage(url: String): Boolean {
        val host = urls.host(url)
        return host == "duckduckgo.com" || host == "bing.com" || host.startsWith("google.")
    }
}
