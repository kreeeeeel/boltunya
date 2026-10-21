package ru.aigram.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCatalogTest {
    @Test
    fun `catalog preserves distinct model context capacities instead of the runtime limit`() {
        val catalog = ModelCatalog().models.associateBy { it.id }
        assertEquals(131072, catalog.getValue("deepseek-r1:8b").contextTokens)
        assertTrue(catalog.getValue("deepseek-r1:8b").thinking)
        assertTrue(catalog.getValue("deepseek-r1:14b").thinking)
        assertEquals(32768, catalog.getValue("gemma3:1b").contextTokens)
        assertEquals(262144, catalog.getValue("qwen3.5:4b").contextTokens)
        assertTrue(catalog.values.map { it.contextTokens }.distinct().size > 1)
    }

    @Test
    fun `catalog distinguishes text-only Gemma from vision variants and offers small downloadable vision models`() {
        val catalog = ModelCatalog()
        assertFalse(catalog.info("gemma3:1b")?.supportsVision ?: true)
        listOf("gemma3:4b", "gemma3:12b", "gemma3:27b", "qwen3.5:4b").stream().forEach {
            assertTrue(catalog.info(it)?.supportsVision == true)
        }
        listOf("qwen3-vl:2b", "qwen3-vl:4b", "qwen3-vl:8b").stream().forEach {
            val info = catalog.models.single { model -> model.id == it }
            assertTrue(info.supportsVision)
            assertEquals(262144, info.contextTokens)
            assertFalse(info.installed)
        }
    }

    @Test
    fun `installed custom models remain selectable without inventing specifications or allowing arbitrary downloads`() {
        val catalog = ModelCatalog()
        assertFalse(catalog.selectable("custom:local"))
        val installed = catalog.installed(setOf("custom:local", "gemma3:latest"))
        val custom = installed.single { it.id == "custom:local" }
        assertTrue(custom.installed)
        assertFalse(custom.supportsVision)
        assertEquals(0, custom.contextTokens)
        assertEquals(0, custom.memoryGb)
        assertEquals("—", custom.size)
        assertTrue(catalog.selectable(custom.id))
        assertTrue(catalog.supportsAutomation(custom.id))
        assertFalse(catalog.contains(custom.id))
        assertTrue(installed.single { it.id == "gemma3:latest" }.supportsVision)
        assertFalse(catalog.supportsAutomation("qwen3:0.6b"))
        catalog.installed(emptySet())
        assertFalse(catalog.selectable(custom.id))
    }
}
