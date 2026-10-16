package ru.boltunya.ai.research

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ru.boltunya.domain.ChatMessage
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.ResearchPlanningFailedException
import ru.boltunya.resources.ResourceCatalog
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val resources = ResourceCatalog()
private const val LOCATION_STEM_LENGTH = 4
private const val RESEARCH_TOPIC_SECONDS = 600L
private const val MAX_RESEARCH_TOPICS = 100
private const val MAX_QUERY_CHARACTERS = 180

class ResearchPlanner(private val clock: Clock = Clock.systemDefaultZone()) {
    private val recent = ConcurrentHashMap<String, RecentResearchPlan>()

    fun remember(history: List<ChatMessage>, plan: ResearchPlan) {
        val latest = history.lastOrNull { !it.outgoing } ?: return
        if (recent.size >= MAX_RESEARCH_TOPICS) recent.clear()
        recent[latest.chatId] = RecentResearchPlan(latest.id, plan, clock.instant())
    }

    private fun previous(history: List<ChatMessage>): ResearchPlan? {
        val incoming = history.filterNot { it.outgoing }.takeLast(2)
        if (incoming.size != 2) return null
        val saved = recent[incoming.last().chatId] ?: return null
        if (saved.messageId != incoming.first().id ||
            clock.instant().epochSecond - saved.recordedAt.epochSecond > RESEARCH_TOPIC_SECONDS
        ) {
            return null
        }
        val elapsedDays =
            LocalDate.now(clock).toEpochDay() - saved.recordedAt.atZone(clock.zone).toLocalDate().toEpochDay()
        return if (saved.plan.kind ==
            "weather"
        ) {
            saved.plan.copy(dayOffset = (saved.plan.dayOffset - elapsedDays.toInt()).coerceAtLeast(0))
        } else {
            saved.plan
        }
    }
    private val locations = Json.decodeFromString<Map<String, List<String>>>(
        resources.text("search/weather-locations.json"),
    )
        .flatMap { (canonical, variants) -> variants.map { it to canonical } }.toMap()
    private val schema = Json.parseToJsonElement(
        resources.text("schemas/research-plan.json"),
    ).jsonObject

    fun needed(history: List<ChatMessage>): Boolean {
        val text = history.lastOrNull { !it.outgoing && it.text.isNotBlank() }?.text.orEmpty().trim()
        if (text.isBlank()) return false
        if (resources.regex("intent/small-talk").matches(text)) return false
        if (resources.regex("intent/web-search").containsMatchIn(text)) return true
        return resources.regex("intent/research-followup").matches(text) && history.filter { !it.outgoing }
            .dropLast(1).takeLast(3).any { resources.regex("intent/web-search").containsMatchIn(it.text) }
    }

    fun plan(
        history: List<ChatMessage>,
        instructions: String,
        generate: (JsonArray, JsonObject) -> String,
    ): ResearchPlan {
        val latest = history.lastOrNull { !it.outgoing }?.text.orEmpty().lowercase()
        val previous = previous(history)
        if (previous != null && resources.regex("intent/research-reuse").matches(latest.trim())) return previous
        if (previous?.kind == "search" && resources.regex("intent/news").containsMatchIn(previous.query) &&
            resources.regex("intent/research-location-followup").matches(latest.trim())
        ) {
            val world = resources.regex("intent/news-world").containsMatchIn(latest)
            val local = resources.regex("intent/news-russia").containsMatchIn(latest)
            if (world || local) {
                val topic = previous.query.replace(resources.regex("intent/news-russia"), "")
                    .replace(
                        resources.regex("intent/news-world"),
                        "",
                    ).replace(resources.regex("intent/news"), "").trim()
                return previous.copy(
                    query = resources.prompt(
                        if (world) "ru/research/news-world-query" else "ru/research/news-russia-query",
                        mapOf("topic" to topic),
                    ).trim().take(MAX_QUERY_CHARACTERS),
                )
            }
        }
        val messages = buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "system")
                    put(
                        "content",
                        resources.prompt(
                            "ru/research/planner",
                            mapOf(
                                "value0" to (
                                    LocalDate.now(
                                        clock,
                                    )
                                    ),
                                "value1" to (instructions.take(8000)),
                            ),
                        ),
                    )
                },
            )
            val dialogue = buildJsonArray {
                history.takeLast(8).stream().filter { it.kind == "text" }.forEach { message ->
                    add(
                        buildJsonObject {
                            put("sender", if (message.outgoing) "owner" else "contact")
                            put("text", message.text.take(800))
                        },
                    )
                }
            }
            add(
                buildJsonObject {
                    put("role", "user")
                    put(
                        "content",
                        resources.prompt(
                            "ru/research/planner-request",
                            mapOf(
                                "history" to dialogue.toString(),
                                "latest" to history.lastOrNull { !it.outgoing }?.text.orEmpty().take(800),
                            ),
                        ),
                    )
                },
            )
        }
        val response = generate(messages, schema)
        val plan = try {
            Json.decodeFromString<ResearchPlan>(response)
        } catch (exception: SerializationException) {
            throw ResearchPlanningFailedException(exception)
        }
        if (plan.kind != "weather") return plan
        val day = when {
            resources.regex("intent/day-after").containsMatchIn(latest) -> 2
            resources.regex("intent/tomorrow").containsMatchIn(latest) -> 1
            resources.regex("intent/today").containsMatchIn(latest) -> 0
            previous?.kind == "weather" && resources.regex(
                "intent/research-location-followup",
            ).matches(latest.trim()) -> previous.dayOffset
            else -> plan.dayOffset
        }
        val period = if (day > 0 || resources.regex("intent/daily-forecast").containsMatchIn(latest)) {
            "daily"
        } else {
            plan.weatherPeriod
        }
        return plan.copy(
            query = "",
            location = verifiedLocation(plan.location, history, instructions),
            dayOffset = day,
            weatherPeriod = period,
        )
    }

    private fun verifiedLocation(value: String, history: List<ChatMessage>, instructions: String): String {
        val location = value.substringBefore(',').trim().replace(resources.regex("common/weather-location-prefix"), "")
        val context = history.takeLast(8).filterNot { it.automated }.joinToString(" ") { it.text } + " " + instructions
        val supplied = locationTokens(context)
        val proposed = locationTokens(location)
        return location.takeIf { proposed.isNotEmpty() && proposed.all { it in supplied } }.orEmpty()
    }

    private fun locationTokens(text: String): Set<String> = resources.regex("common/research-token").findAll(text)
        .map { it.value.lowercase(Locale.ROOT) }.map { locations[it] ?: it.take(LOCATION_STEM_LENGTH) }.toSet()
}
