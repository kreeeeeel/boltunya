package ru.boltunya.ai

import com.sun.net.httpserver.HttpServer
import ru.boltunya.ai.research.CurrencyRatesClient
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.ResearchSource
import ru.boltunya.ai.research.SearchProvider
import ru.boltunya.ai.research.WeatherClient
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Deterministic external facts; only model inference uses the live local engine. */
class LiveResearchFixtures : AutoCloseable {
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("Europe/Moscow"))
    private val server = startServer()

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        mapOf(
            "/geocoding" to """{"results":[{"name":"Москва","country":"Россия","latitude":55.75,"longitude":37.62}]}""",
            "/weather" to
                """{"timezone":"Europe/Moscow","current":{"time":"2026-10-16T15:00","temperature_2m":12,"apparent_temperature":10,"weather_code":3,"wind_speed_10m":2},"daily":{"time":["2026-10-16","2026-10-17"],"weather_code":[3,61],"temperature_2m_min":[8,7],"temperature_2m_max":[14,11],"precipitation_probability_max":[10,80]}}""",
            "/rates" to
                """<ValCurs Date="16.10.2026"><Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>83,7600</Value></Valute><Valute><CharCode>EUR</CharCode><Nominal>1</Nominal><Value>93,7693</Value></Valute></ValCurs>""",
        ).entries.stream().forEach { (path, body) ->
            server.createContext(path) { exchange ->
                val bytes = body.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        return server
    }

    fun researcher(): InternetResearcher {
        val base = "http://127.0.0.1:${server.address.port}"
        return InternetResearcher(
            weather = WeatherClient("$base/geocoding", "$base/weather"),
            currencies = CurrencyRatesClient(clock, "$base/rates"),
            clock = clock,
            readPage = { "" },
            search = object : SearchProvider {
                override fun search(query: String): List<ResearchSource> = listOf(
                    ResearchSource(
                        "Новости лаборатории Север: выставка роботов открылась 16.10.2026",
                        "https://example.org/science/sever-robots",
                        "Лаборатория Север открыла выставку 16.10.2026. На выставке показали 42 робота.",
                        "2026-10-16",
                    ),
                )
            },
        )
    }

    override fun close() = server.stop(0)
}
