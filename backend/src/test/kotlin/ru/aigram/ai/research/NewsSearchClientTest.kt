package ru.aigram.ai.research

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsSearchClientTest {
    @Test
    fun `keeps recent dated news matches regions and rejects stale unknown or future dates`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var reads = 0
        server.createContext("/feed") { exchange ->
            reads++
            val response = """<rss><channel>
                <item><title>Открыли выставку</title><link>https://rbc.ru/society/exhibition</link><description>Выставка в Москве</description><pubDate>Fri, 16 Oct 2026 11:00:00 GMT</pubDate><category>Общество</category></item>
                <item><title>Новости международного форума</title><link>https://tass.ru/mezhdunarodnaya-panorama/forum</link><description>Начался форум</description><pubDate>Fri, 16 Oct 2026 10:00:00 GMT</pubDate><category>В мире</category></item>
                <item><title>Старое событие</title><link>https://rbc.ru/old</link><pubDate>Wed, 14 Oct 2026 11:00:00 GMT</pubDate></item>
                <item><title>Будущее событие</title><link>https://rbc.ru/future</link><pubDate>Sat, 17 Oct 2026 11:00:00 GMT</pubDate></item>
                <item><title>Без даты</title><link>https://rbc.ru/undated</link></item>
                <item><title>В России запатентуют игровых персонажей</title><link>https://rbc.ru/technology/patents</link><description>Решение объявили на международной конференции в Москве</description><pubDate>Fri, 16 Oct 2026 11:30:00 GMT</pubDate><category>Технологии и медиа</category></item>
            </channel></rss>
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
            val news = NewsSearchClient(clock, feeds = listOf("http://127.0.0.1:${server.address.port}/feed"))
            assertNull(news.search("Kotlin stable release"))
            val all = news.search("последние новости сегодня").orEmpty()
            assertEquals(3, all.size)
            assertEquals(2, news.search("новости России сегодня")?.size)
            assertEquals(
                "В России запатентуют игровых персонажей",
                news.search("новости России сегодня")?.first()?.title,
            )
            assertEquals("Новости международного форума", news.search("новости мира")?.single()?.title)
            assertTrue(news.search("новости выставки")?.single()?.text.orEmpty().contains("Выставка в Москве"))
            assertEquals(1, reads)
            assertEquals(3, SearchResultSelector(clock = clock).select("новости сегодня", all).size)
            assertTrue(
                SearchResultSelector(
                    clock = clock,
                ).select(
                    "новости",
                    listOf(ResearchSource("Новости", "https://rbc.ru/unknown", "Событие без даты")),
                ).isEmpty(),
            )
        } finally {
            server.stop(0)
        }
    }
}
