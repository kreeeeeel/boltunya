package ru.boltunya.ai.research

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.InvalidWeatherResponseException
import ru.boltunya.exception.impl.WeatherCoordinatesMissingException
import ru.boltunya.exception.impl.WeatherDateUnavailableException
import ru.boltunya.exception.impl.WeatherForecastMissingException
import ru.boltunya.exception.impl.WeatherServiceUnavailableException
import ru.boltunya.resources.ResourceCatalog
import java.io.IOException

private val resources = ResourceCatalog()

class WeatherClient(
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com/v1/search",
    private val forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
) {
    fun forecast(location: String, dayOffset: Int, currentConditions: Boolean = true): ResearchContext {
        val geocoding = get(
            geocodingUrl,
            mapOf(
                "name" to location,
                "count" to "1",
                "language" to "ru",
                "format" to "json",
            ),
        )
        val city = geocoding["results"]?.jsonArray?.firstOrNull()?.jsonObject
        if (city == null) {
            return ResearchContext("clarify", resources.prompt("ru/weather/city-unknown"))
        }
        val latitude = city["latitude"]?.jsonPrimitive?.doubleOrNull
        val longitude = city["longitude"]?.jsonPrimitive?.doubleOrNull
        if (latitude == null ||
            longitude == null
        ) {
            throw WeatherCoordinatesMissingException()
        }
        val response = get(
            forecastUrl,
            mapOf(
                "latitude" to latitude.toString(),
                "longitude" to longitude.toString(),
                "current" to "temperature_2m,apparent_temperature,weather_code,wind_speed_10m",
                "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
                "timezone" to "auto",
                "forecast_days" to "7",
                "wind_speed_unit" to "ms",
            ),
        )
        val daily = response["daily"]?.jsonObject
        if (daily == null) throw WeatherForecastMissingException()
        fun dailyValue(name: String): String =
            daily[name]?.jsonArray?.getOrNull(dayOffset)?.jsonPrimitive?.contentOrNull
                ?: throw WeatherDateUnavailableException()
        val name = city["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val country = city["country"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val current = response["current"]?.jsonObject
        val conditions = conditions(dailyValue("weather_code").toIntOrNull())
        val present = if (dayOffset == 0 && currentConditions && current != null) {
            resources.prompt(
                "ru/weather/current-temperature",
                mapOf(
                    "value0" to (current["time"]?.jsonPrimitive?.contentOrNull),
                    "value1" to (current["temperature_2m"]),
                ),
            ) + " " +
                resources.prompt(
                    "ru/weather/current-feels-like",
                    mapOf(
                        "value0" to (current["apparent_temperature"]),
                        "value1" to (current["wind_speed_10m"]),
                    ),
                ) + " " +
                conditions(current["weather_code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()) + "."
        } else {
            ""
        }
        val minimum = dailyValue("temperature_2m_min")
        val maximum = dailyValue("temperature_2m_max")
        val period = when (dayOffset) {
            1 -> resources.prompt("ru/weather/tomorrow")
            2 -> resources.prompt("ru/weather/day-after-tomorrow")
            else -> resources.prompt("ru/weather/period", mapOf("value0" to (dailyValue("time"))))
        }
        val forecast = if (present.isNotBlank()) {
            present
        } else {
            resources.prompt(
                "ru/weather/forecast",
                mapOf(
                    "period" to (period),
                    "value1" to (dailyValue("time")),
                    "conditions" to (conditions),
                    "minimum" to (minimum),
                    "maximum" to (maximum),
                    "value5" to (dailyValue("precipitation_probability_max")),
                ),
            )
        }
        val facts = resources.prompt(
            "ru/weather/facts",
            mapOf(
                "name" to (name),
                "country" to (country),
                "value2" to (response["timezone"]?.jsonPrimitive?.contentOrNull),
                "forecast" to (forecast),
            ),
        )
        return ResearchContext(
            "weather",
            facts,
            listOf(
                ResearchSource("Open-Meteo", "https://open-meteo.com/", resources.prompt("ru/weather/source-title")),
            ),
        )
    }

    private fun conditions(code: Int?): String {
        val condition = WeatherCondition.entries.firstOrNull { code in it.codes } ?: WeatherCondition.UNKNOWN
        return resources.prompt("ru/weather/" + condition.resourceKey)
    }

    private fun get(url: String, parameters: Map<String, String>): JsonObject = try {
        val response = Jsoup.connect(url).data(parameters).userAgent("Boltun/1.1").timeout(7000)
            .maxBodySize(256 * 1024).ignoreContentType(true).execute()
        Json.parseToJsonElement(response.body()).jsonObject
    } catch (exception: IOException) {
        throw WeatherServiceUnavailableException(exception)
    } catch (exception: SerializationException) {
        throw InvalidWeatherResponseException(exception)
    }
}
