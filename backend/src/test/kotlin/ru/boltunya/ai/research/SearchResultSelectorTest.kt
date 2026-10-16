package ru.boltunya.ai.research

import org.junit.jupiter.api.Test
import ru.boltunya.exception.impl.SearchRateLimitedException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchResultSelectorTest {
    @Test
    fun `filters unrelated pages and tracking duplicates while keeping different relevant sites`() {
        val kotlin =
            ResearchSource(
                "Kotlin releases",
                "https://kotlinlang.org/docs/releases.html?utm_source=ad",
                "Kotlin release details",
            )
        val candidates = listOf(
            ResearchSource("Cooking pancakes", "https://food.example/pancakes", "Easy breakfast"),
            kotlin,
            kotlin.copy(url = "https://kotlinlang.org/docs/releases.html#latest"),
            kotlin.copy(url = "https://kotlinlang.org/docs/whatsnew.html"),
            kotlin.copy(url = "https://kotlinlang.org/third"),
            kotlin.copy(url = "https://bing.com/search?q=kotlin"),
            ResearchSource("Kotlin on GitHub", "https://github.com/JetBrains/kotlin/releases", "Kotlin release"),
        )
        val result = SearchResultSelector().select("Latest Kotlin stable release", candidates)
        assertEquals(3, result.size)
        assertTrue(
            result.none {
                it.url.contains("food.example") || it.url.contains("bing.com") ||
                    it.url.contains("utm_")
            },
        )
        assertEquals(2, result.count { it.url.contains("kotlinlang.org") })
        assertEquals(
            1,
            SearchResultSelector().select(
                "курс доллара",
                listOf(
                    ResearchSource("USD exchange rates", "https://example.com/usd", "USD RUB exchange rate"),
                ),
            ).size,
        )
    }

    @Test
    fun `backs off a limited MCP provider and immediately retries after a key change`() {
        var attempts = 0
        var revision = 0L
        val primary = object : SearchProvider {
            override fun search(query: String): List<ResearchSource> {
                attempts++
                if (revision == 0L) throw SearchRateLimitedException()
                return listOf(ResearchSource("Kotlin official", "https://kotlinlang.org/", "Kotlin"))
            }
            override fun revision(): Long = revision
        }
        val backup = SearchProvider { listOf(ResearchSource("Kotlin backup", "https://example.com/kotlin", "Kotlin")) }
        val search =
            FallbackSearchProvider(primary, backup, SearchResultSelector(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        assertEquals("Kotlin backup", search.search("Kotlin").single().title)
        search.search("Kotlin")
        assertEquals(1, attempts)
        revision = 1
        assertEquals("Kotlin official", search.search("Kotlin").single().title)
        assertEquals(2, attempts)
    }
}
