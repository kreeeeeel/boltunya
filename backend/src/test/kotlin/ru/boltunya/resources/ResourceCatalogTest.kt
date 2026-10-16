package ru.boltunya.resources

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceCatalogTest {
    @Test
    fun `loads multilingual patterns without code changes and never renders user templates recursively`() {
        val resources = ResourceCatalog()
        val pattern = resources.regex("intent/assistant-instruction")
        listOf("напиши", "write", "жаз", "згенеруй").stream().forEach { assertTrue(pattern.containsMatchIn(it)) }
        val text = resources.render(
            "prompts/ru/weather/period.txt",
            mapOf(
                "value0" to "{{secret}}",
                "secret" to "hidden",
            ),
        )
        assertEquals("на {{secret}}", text)
    }
}
