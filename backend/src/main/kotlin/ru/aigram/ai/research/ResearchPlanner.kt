package ru.aigram.ai.research

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.ai.MessageContextFormatter
import ru.aigram.domain.ChatMessage
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.ResearchPlanningFailedException
import ru.aigram.resources.ResourceCatalog
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val resources = ResourceCatalog()
private const val LOCATION_STEM_LENGTH = 4
private const val RESEARCH_TOPIC_SECONDS = 600L
private const val MAX_RESEARCH_TOPICS = 100
private const val MAX_QUERY_CHARACTERS = 180
private const val MAX_RESEARCH_MESSAGE_CHARACTERS = 800

class ResearchPlanner(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val authors: MessageContextFormatter = MessageContextFormatter(),
    private val terms: ResearchTerms = ResearchTerms(resources),
    private val words: ResearchWords = ResearchWords(),
) {
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
        if (terms.only("small-talk", text)) return false
        if (terms.contains("web-search", text)) return true
        return (
            terms.only("research-followup", text) || terms.only("research-reuse", text) ||
                terms.locationFollowup(text)
            ) && history.filter { !it.outgoing }
            .dropLast(1).takeLast(3).any { terms.contains("web-search", it.text) }
    }

    fun plan(
        history: List<ChatMessage>,
        instructions: String,
        generate: (List<Message>, JsonObject) -> String,
    ): ResearchPlan {
        val latest = history.lastOrNull { !it.outgoing }?.text.orEmpty().lowercase()
        val previous = previous(history)
        if (previous != null && terms.only("research-reuse", latest)) return previous
        if (previous?.kind == "search" && terms.contains("news", previous.query) &&
            terms.locationFollowup(latest)
        ) {
            val world = terms.contains("news-world", latest)
            val local = terms.contains("news-russia", latest)
            if (world || local) {
                val topic = words.tokens(previous.query).filterNot { token ->
                    terms.contains("news-russia", token) || terms.contains("news-world", token) ||
                        terms.contains("news", token) || token in setOf("в", "у", "in", "на")
                }.joinToString(" ")
                return previous.copy(
                    query = resources.prompt(
                        if (world) "ru/research/news-world-query" else "ru/research/news-russia-query",
                        mapOf("topic" to topic),
                    ).trim().take(MAX_QUERY_CHARACTERS),
                )
            }
        }
        val dialogue = buildJsonArray {
            history.takeLast(8).stream().filter { it.kind == "text" }.forEach { message ->
                add(authors.data(message, MAX_RESEARCH_MESSAGE_CHARACTERS))
            }
        }
        val messages = listOf(
            SystemMessage(
                resources.prompt(
                    "ru/research/planner",
                    mapOf(
                        "value0" to LocalDate.now(clock),
                        "value1" to instructions.take(8000),
                    ),
                ),
            ),
            UserMessage(
                resources.prompt(
                    "ru/research/planner-request",
                    mapOf(
                        "history" to dialogue.toString(),
                        "latest" to history.lastOrNull { !it.outgoing }?.text.orEmpty().take(800),
                    ),
                ),
            ),
        )
        val response = generate(messages, schema)
        val plan = try {
            Json.decodeFromString<ResearchPlan>(response)
        } catch (exception: SerializationException) {
            throw ResearchPlanningFailedException(exception)
        }
        if (plan.kind != "weather") return plan
        val day = when {
            terms.contains("day-after", latest) -> 2
            terms.contains("tomorrow", latest) -> 1
            terms.contains("today", latest) -> 0
            previous?.kind == "weather" && terms.locationFollowup(latest) -> previous.dayOffset
            else -> plan.dayOffset
        }
        val period = if (day > 0 || terms.contains("daily-forecast", latest)) {
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
        val tokens = words.tokens(value.substringBefore(','))
        val location = if (tokens.firstOrNull() in setOf("в", "in", "у", "на")) {
            value.substringBefore(',').trim().substringAfter(' ').trim()
        } else {
            value.substringBefore(',').trim()
        }
        val context = history.takeLast(8).filterNot { it.automated }.joinToString(" ") { it.text } + " " + instructions
        val supplied = locationTokens(context)
        val proposed = locationTokens(location)
        return location.takeIf { proposed.isNotEmpty() && proposed.all { it in supplied } }.orEmpty()
    }

    private fun locationTokens(text: String): Set<String> = words.tokens(text)
        .map { it.lowercase(Locale.ROOT) }.map { locations[it] ?: it.take(LOCATION_STEM_LENGTH) }.toSet()
}
