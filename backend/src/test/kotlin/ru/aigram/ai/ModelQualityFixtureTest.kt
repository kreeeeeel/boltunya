package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.aigram.resources.ResourceCatalog
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelQualityFixtureTest {
    private val cases = Json.parseToJsonElement(ResourceCatalog().text("testing/model-quality.json")).jsonArray
        .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

    @Test
    fun `style checks distinguish complete pronouns from parts of other Russian words`() {
        assertTrue(passes("style-rough", "Ебана какая, ты до сих пор не отправил файл?"))
        assertTrue(passes("style-rough", "Какого хуя ты до сих пор не отправил файл?"))
        assertTrue(passes("style-rough", "Пиздец, да отправь ты уже этот файл."))
        assertTrue(passes("style-business", "Отправьте файл сейчас, чтобы не затягивать задачу."))
        assertTrue(passes("style-concise", "Надо отправить файл сейчас."))
    }

    @Test
    fun `style checks reject claiming the senders unfinished action as the owners action`() {
        assertFalse(passes("style-business", "Я до сих пор не успел отправить файл."))
        assertFalse(passes("style-concise", "Ну вот уже отправил."))
        assertFalse(passes("style-aggressive", "Я отправляю мгновенно. Не отсылаю."))
    }

    private fun passes(id: String, text: String): Boolean =
        ReplyExpectation().checks(cases.getValue(id).getValue("expected").jsonObject, text).all { it }
}
