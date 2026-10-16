package ru.boltunya.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {
    @Test
    fun `catalog preserves distinct model context capacities instead of the runtime limit`() {
        val catalog = ModelCatalog().models.associateBy { it.id }
        assertEquals(131072, catalog.getValue("deepseek-r1:8b").contextTokens)
        assertEquals(32768, catalog.getValue("gemma3:1b").contextTokens)
        assertEquals(262144, catalog.getValue("qwen3.5:4b").contextTokens)
        assertTrue(catalog.values.map { it.contextTokens }.distinct().size > 1)
    }
}
