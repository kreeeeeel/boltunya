package ru.boltunya.ai.research

import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.InvalidResearchPlanException
import ru.boltunya.exception.impl.ResearchUnavailableException
import ru.boltunya.resources.ResourceCatalog
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val resources = ResourceCatalog()
private const val MAX_CACHE_ENTRIES = 100
private const val CACHE_SECONDS = 120
private const val MAX_QUERY_CHARACTERS = 180
private const val MAX_LOCATION_CHARACTERS = 100
private const val MAX_PAGE_READS = 2
private const val MAX_SOURCE_CHARACTERS = 1800

class InternetResearcher(
    private val weather: WeatherClient = WeatherClient(),
    private val search: SearchProvider = WebSearchClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val selector: SearchResultSelector = SearchResultSelector(),
    private val readPage: (String) -> String = PublicPageReader()::read,
    private val currencies: CurrencyRatesClient = CurrencyRatesClient(clock),
) {
    private val cache = ConcurrentHashMap<Pair<ResearchPlan, Long>, Pair<Instant, ResearchContext>>()

    fun lookup(plan: ResearchPlan): ResearchContext {
        if (plan.kind == "none") return ResearchContext()
        if (plan.kind !in setOf("weather", "search") || plan.dayOffset !in 0..6 ||
            plan.query.length > MAX_QUERY_CHARACTERS || plan.location.length > MAX_LOCATION_CHARACTERS ||
            plan.weatherPeriod !in setOf("current", "daily")
        ) {
            throw InvalidResearchPlanException()
        }
        val publicQuery = if (plan.kind == "weather") plan.location else plan.query
        if (resources.regex("common/InternetResearcher-1").containsMatchIn(publicQuery)) {
            return ResearchContext(
                "unavailable",
                resources.prompt("ru/research/private-query"),
            )
        }
        if (plan.kind == "weather" && plan.location.isBlank()) {
            return ResearchContext(
                "clarify",
                resources.prompt("ru/weather/location-required"),
            )
        }
        if (plan.kind == "search" && plan.query.isBlank()) {
            return ResearchContext("clarify", resources.prompt("ru/research/query-required"))
        }
        if (cache.size > MAX_CACHE_ENTRIES) cache.clear()
        val key = plan to search.revision()
        val value = cache.compute(key) { _, previous ->
            val now = clock.instant()
            if (previous != null && now.epochSecond - previous.first.epochSecond < CACHE_SECONDS) {
                previous
            } else {
                now to fetch(plan)
            }
        }
        if (value == null) throw ResearchUnavailableException()
        if (value.second.status == "unavailable") cache.remove(key, value)
        return value.second
    }

    private fun fetch(plan: ResearchPlan): ResearchContext = try {
        if (plan.kind == "weather") {
            weather.forecast(plan.location, plan.dayOffset, plan.weatherPeriod == "current")
        } else {
            val currency = currencies.lookup(plan.query)
            if (currency != null) {
                currency
            } else {
                val sources = selector.select(
                    plan.query,
                    enrich(selector.select(plan.query, search.search(plan.query))),
                )
                if (sources.isEmpty()) {
                    ResearchContext(
                        "unavailable",
                        resources.prompt("ru/research/no-sources"),
                    )
                } else {
                    ResearchContext(
                        "search",
                        resources.prompt("ru/research/source-time", mapOf("value0" to (clock.instant()))),
                        sources,
                    )
                }
            }
        }
    } catch (exception: IntegrationException) {
        System.err.println("Internet lookup unavailable: ${exception.javaClass.simpleName}")
        ResearchContext(
            "unavailable",
            resources.prompt("ru/research/unavailable"),
        )
    }

    private fun enrich(sources: List<ResearchSource>): List<ResearchSource> {
        if (sources.isEmpty()) return sources
        return Executors.newFixedThreadPool(MAX_PAGE_READS).use { executor ->
            val pages = sources.take(MAX_PAGE_READS).associate { source ->
                source.url to executor.submit<String> {
                    try {
                        readPage(source.url)
                    } catch (exception: IOException) {
                        System.err.println("Search page unavailable: ${exception.javaClass.simpleName}")
                        ""
                    } catch (exception: IntegrationException) {
                        System.err.println("Search page unavailable: ${exception.javaClass.simpleName}")
                        ""
                    }
                }
            }
            sources.map { source ->
                val content = pages[source.url]?.get().orEmpty()
                source.copy(
                    text = (
                        if (source.publishedAt.isNotBlank()) {
                            listOf(
                                source.text,
                                content,
                            )
                        } else {
                            listOf(content, source.text)
                        }
                        ).filter(String::isNotBlank)
                        .joinToString("\n").take(MAX_SOURCE_CHARACTERS),
                )
            }
        }
    }
}
