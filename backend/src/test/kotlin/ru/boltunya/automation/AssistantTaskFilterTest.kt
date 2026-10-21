package ru.boltunya.automation

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantTaskFilterTest {
    @Test
    fun `recipe request from the reported conversation is ignored when the option is enabled`() {
        assertTrue(filter.ignores("напиши рецепт сырников"))
    }
    private val filter = AssistantTaskFilter()

    @Test
    fun `skips explicit assistant jobs and preserves personal conversation`() {
        listOf("дай мне рецепт блинов", "Напиши код на Kotlin", "реши уравнение", "write an essay", "generate code")
            .stream().forEach { assertTrue(filter.ignores(it), it) }
        listOf(
            "Как дела?",
            "У тебя остался рецепт тех блинов?",
            "Сколько сейчас времени?",
            "Напиши мне вечером",
            "Как погода?",
        )
            .stream().forEach { assertFalse(filter.ignores(it), it) }
    }

    @Test
    fun `uses multilingual literal words and keeps word boundaries`() {
        listOf("жаз код", "рецептін бер", "розв’яжи рівняння", "розв'яжи рівняння", "дай рецептик")
            .stream().forEach { assertTrue(filter.ignores(it), it) }
        listOf("writer code", "give postcode", "Я написал рецепт", "Кодовое имя Напиши")
            .stream().forEach { assertFalse(filter.ignores(it), it) }
    }
}
