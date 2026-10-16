package ru.boltunya.ai.research

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.exception.IntegrationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchPlannerTest {
    @Test
    fun `world news followup changes region while retaining the supplied topic`() {
        val planner = ResearchPlanner()
        val initial = ChatMessage("1", "42", "Новости технологий в России сегодня", false, 0)
        planner.remember(listOf(initial), ResearchPlan(kind = "search", query = "новости технологий России сегодня"))
        val history = listOf(initial, ChatMessage("2", "42", "А в мире?", false, 0))
        val plan = planner.plan(history, "") { _, _ ->
            error("An explicit news region change does not need model planning")
        }
        assertTrue(plan.query.contains("мира"))
        assertTrue(plan.query.contains("технологий"))
        assertFalse(plan.query.contains("России"))
    }

    @Test
    fun `reuses adjacent verified topics and carries the forecast day to a new city`() {
        val planner = ResearchPlanner()
        val initial = ChatMessage("1", "42", "Погода в Москве завтра", false, 0)
        val forecast = ResearchPlan(kind = "weather", location = "Москва", dayOffset = 1, weatherPeriod = "daily")
        planner.remember(listOf(initial), forecast)
        val next = listOf(initial, ChatMessage("2", "42", "А в Казани?", false, 0))
        val city = planner.plan(next, "") { _, _ -> """{"kind":"weather","location":"Казань","dayOffset":0}""" }
        assertEquals(1, city.dayOffset)
        planner.remember(next, city)
        val source = next + ChatMessage("3", "42", "Дай ссылку на источник", false, 0)
        assertEquals(
            city,
            planner.plan(source, "") { _, _ ->
                error("A verified source request should reuse its plan")
            },
        )
        val unrelated =
            next + ChatMessage("3", "42", "Как дела?", false, 0) + ChatMessage("4", "42", "Дай ссылку", false, 0)
        var generated = false
        planner.plan(unrelated, "") { _, _ ->
            generated = true
            """{"kind":"none"}"""
        }
        assertTrue(generated)
    }

    @Test
    fun `keeps casual reactions and clock questions offline but plans contextual follow ups`() {
        val planner = ResearchPlanner()
        assertFalse(planner.needed(listOf(ChatMessage("1", "1", "Ахаххаха", false, 0))))
        assertFalse(planner.needed(listOf(ChatMessage("1", "1", "Сколько сейчас времени?", false, 0))))
        assertFalse(planner.needed(listOf(ChatMessage("1", "1", "Кто лучшая сестра на свете?", false, 0))))
        assertFalse(planner.needed(listOf(ChatMessage("1", "1", "Расскажи рецепт блинов", false, 0))))
        assertTrue(planner.needed(listOf(ChatMessage("1", "1", "Сколько сейчас стоит доллар?", false, 0))))
        val history = listOf(
            ChatMessage("1", "1", "Погода в Казани", false, 0),
            ChatMessage("2", "1", "А завтра?", false, 0),
        )
        assertTrue(planner.needed(history))
        assertTrue(
            planner.needed(
                listOf(
                    ChatMessage("1", "1", "Какой курс доллара к рублю?", false, 0),
                    ChatMessage("2", "1", "А евро?", false, 0),
                ),
            ),
        )
        assertFalse(planner.needed(listOf(ChatMessage("1", "1", "А евро?", false, 0))))
        assertTrue(
            planner.needed(
                listOf(
                    ChatMessage("1", "1", "Новости мира", false, 0),
                    ChatMessage("2", "1", "Подробнее, но без ссылок", false, 0),
                ),
            ),
        )
        val plan = planner.plan(history, "Без эмодзи") { messages, schema ->
            assertEquals("object", schema["type"]?.jsonPrimitive?.content)
            assertTrue(messages.last().jsonObject["content"].toString().contains("Казани"))
            assertTrue(messages.first().toString().contains("Не угадывай город"))
            """{"kind":"weather","query":"","location":"Казань","dayOffset":0}"""
        }
        assertEquals("Казань", plan.location)
        assertEquals(1, plan.dayOffset)
    }

    @Test
    fun `rejects a guessed weather city but accepts explicit inflected and translated place names`() {
        val planner = ResearchPlanner()
        fun plan(question: String, city: String): ResearchPlan = planner.plan(
            listOf(ChatMessage("1", "1", question, false, 0)),
            "",
        ) { _, _ -> """{"kind":"weather","location":"$city"}""" }
        assertEquals("", plan("Как погода?", "Москва").location)
        assertEquals("Москва", plan("Погода в Москве", "Москва").location)
        assertEquals("Kyiv", plan("Яка погода в Києві?", "Kyiv").location)
        assertEquals("", plan("Как погода?", "Киев").location)
    }

    @Test
    fun `invalid planner responses produce an explicit error`() {
        assertFailsWith<IntegrationException> {
            ResearchPlanner().plan(emptyList(), "") { _, _ -> "not json" }
        }
    }
}
