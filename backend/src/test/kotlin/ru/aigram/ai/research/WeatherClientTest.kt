package ru.aigram.ai.research

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeatherClientTest {
    @Test
    fun `uses city coordinates and preserves dates units current conditions and tomorrow forecast`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/geo") { exchange ->
            calls.incrementAndGet()
            val bytes = """
                {"results":[{"name":"Москва","country":"Россия","latitude":55.75,"longitude":37.61}]}
            """.trimIndent().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/forecast") { exchange ->
            assertTrue(exchange.requestURI.query.contains("latitude=55.75"))
            assertTrue(exchange.requestURI.query.contains("wind_speed_unit=ms"))
            val bytes = """{
                "timezone":"Europe/Moscow",
                "current":{"time":"2026-10-15T22:00","temperature_2m":9.3,"apparent_temperature":7.1,"wind_speed_10m":2.5},
                "daily":{"time":["2026-10-15","2026-10-16"],"weather_code":[3,61],
                "temperature_2m_min":[5.2,6.4],"temperature_2m_max":[12.1,13.8],"precipitation_probability_max":[10,85]}
            }""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val weather = WeatherClient("$base/geo", "$base/forecast")
            val researcher = InternetResearcher(weather = weather)
            val today = researcher.lookup(ResearchPlan("weather", location = "Москва"))
            assertTrue(today.facts.contains("9.3 °C"))
            assertTrue(today.facts.contains("2.5 м/с"))
            assertFalse(today.facts.contains("12.1 °C"))
            assertEquals(today, researcher.lookup(ResearchPlan("weather", location = "Москва")))
            assertEquals(1, calls.get())
            val tomorrow = weather.forecast("Москва", 1)
            assertTrue(tomorrow.facts.contains("2026-10-16"))
            assertTrue(tomorrow.facts.contains("дождь"))
            assertTrue(tomorrow.facts.contains("13.8 °C"))
            assertFalse(tomorrow.facts.contains("9.3 °C"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `does not request external services without a city or with contact data`() {
        val researcher = InternetResearcher()
        assertEquals("clarify", researcher.lookup(ResearchPlan("weather")).status)
        assertEquals("unavailable", researcher.lookup(ResearchPlan("search", query = "someone@example.com")).status)
        assertEquals("unavailable", researcher.lookup(ResearchPlan("search", query = "+7 (999) 123-45-67")).status)
    }

    @Test
    fun `reports provider failure without inventing weather and retries after recovery`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val researcher = InternetResearcher(weather = WeatherClient(base, base))
            assertEquals("unavailable", researcher.lookup(ResearchPlan("weather", location = "Москва")).status)
            assertEquals("unavailable", researcher.lookup(ResearchPlan("weather", location = "Москва")).status)
            assertEquals(2, calls.get())
        } finally {
            server.stop(0)
        }
    }
}
