package ru.aigram.transformer.settings

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.SettingsUpdate
import ru.aigram.contract.dto.WritingStyle
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryCollectionScope
import ru.aigram.domain.settings.AppSettings
import ru.aigram.transformer.chat.ChatRuleTransformer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTransformerTest {
    private val transformer = SettingsTransformer(ChatRuleTransformer())

    @Test
    fun `partial updates preserve absent values instead of replacing saved settings with defaults`() {
        val mapped = transformer.toDomain(SettingsUpdate(globalPrompt = "Synthetic owner instruction"))

        assertEquals("Synthetic owner instruction", mapped.globalPrompt)
        assertNull(mapped.paused)
        assertNull(mapped.defaultModel)
        assertNull(mapped.defaultModelProfileId)
        assertNull(mapped.launchAtLogin)
        assertNull(mapped.memoryCollectionScope)
        assertNull(mapped.customStyles)
    }

    @Test
    fun `settings output includes saved queue scope and per chat model choices`() {
        val value = AppSettings(
            paused = false,
            maxParallelTasks = 3,
            memoryCollectionScope = MemoryCollectionScope.ALL_ALLOWED,
            rules = mapOf("-1009007199254740993" to ChatRule(enabled = true, model = "qwen3:1.7b")),
        )

        val mapped = transformer.toContract(value)

        assertFalse(mapped.paused)
        assertEquals(3, mapped.maxParallelTasks)
        assertEquals("all-allowed", mapped.memoryCollectionScope?.value)
        assertEquals("qwen3:1.7b", mapped.rules[value.rules.keys.single()]?.model)
        assertTrue(mapped.rules[value.rules.keys.single()]?.enabled == true)
    }

    @Test
    fun `custom writing style inputs retain names emoji and instructions`() {
        val style = WritingStyle("custom-kind", "Kind", "🙂", "Be warm and brief")
        val mapped = transformer.toDomain(SettingsUpdate(customStyles = listOf(style)))

        assertEquals("custom-kind", mapped.customStyles?.single()?.id)
        assertEquals("Kind", mapped.customStyles?.single()?.name)
        assertEquals("🙂", mapped.customStyles?.single()?.emoji)
        assertEquals("Be warm and brief", mapped.customStyles?.single()?.instructions)
    }
}
