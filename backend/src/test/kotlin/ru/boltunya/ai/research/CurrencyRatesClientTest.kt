package ru.boltunya.ai.research

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import ru.boltunya.exception.impl.CurrencyRatesUnavailableException
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyRatesClientTest {
    @Test
    fun `reads dated official rates handles nominal units cross rates and refuses stale data`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var date = "16.10.2026"
        var query = ""
        server.createContext("/rates") { exchange ->
            query = exchange.requestURI.rawQuery
            val response = """
                <ValCurs Date="$date">
                <Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>83,7600</Value></Valute>
                <Valute><CharCode>EUR</CharCode><Nominal>1</Nominal><Value>93,7693</Value></Valute>
                <Valute><CharCode>KZT</CharCode><Nominal>100</Nominal><Value>16,7300</Value></Valute>
                </ValCurs>
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
            val client = CurrencyRatesClient(clock, "http://127.0.0.1:${server.address.port}/rates")
            val usd = client.lookup("официальный курс доллара к рублю сегодня") ?: error("Missing rate")
            assertEquals("currency", usd.status)
            assertTrue(usd.facts.contains("доллар — 83.76 ₽"))
            assertEquals("2026-10-16", usd.sources.single().publishedAt)
            assertTrue(query.contains("16%2F10%2F2026"))
            assertTrue(client.lookup("курс евро к доллару")?.facts.orEmpty().contains("евро — 1.1195 $"))
            assertTrue(client.lookup("курс тенге к рублю")?.facts.orEmpty().contains("тенге — 0.1673 ₽"))
            assertTrue(client.lookup("курсы доллара и евро")?.facts.orEmpty().contains("евро — 93.7693 ₽"))
            assertNull(client.lookup("прогноз курса доллара"))
            assertNull(client.lookup("Новости Kotlin"))
            date = "01.01.2020"
            assertFailsWith<CurrencyRatesUnavailableException> { client.lookup("курс доллара") }
        } finally {
            server.stop(0)
        }
    }
}
