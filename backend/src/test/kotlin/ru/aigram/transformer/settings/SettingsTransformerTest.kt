package ru.aigram.transformer.settings

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.SettingsUpdate
import ru.aigram.contract.dto.WritingStyle
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryCollectionScope
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.ReplyProcessingMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTransformerTest {
    @Test
    fun `partial updates preserve absent values instead of replacing saved settings with defaults`() {
        val mapped = SettingsUpdate(globalPrompt = "Synthetic owner instruction").toDomain()

        assertEquals("Synthetic owner instruction", mapped.globalPrompt)
        assertNull(mapped.paused)
        assertNull(mapped.defaultModel)
        assertNull(mapped.defaultModelProfileId)
        assertNull(mapped.launchAtLogin)
        assertNull(mapped.memoryCollectionScope)
        assertNull(mapped.customStyles)
        assertNull(mapped.formatReplies)
        assertNull(mapped.replyProcessingMode)
    }

    @Test
    fun `settings output includes saved queue scope and per chat model choices`() {
        val value = AppSettings(
            paused = false,
            maxParallelTasks = 3,
            formatReplies = true,
            replyProcessingMode = ReplyProcessingMode.EACH,
            memoryCollectionScope = MemoryCollectionScope.ALL_ALLOWED,
            rules = mapOf("-1009007199254740993" to ChatRule(enabled = true, model = "qwen3:1.7b")),
        )

        val mapped = value.toContract()

        assertFalse(mapped.paused)
        assertEquals(3, mapped.maxParallelTasks)
        assertTrue(mapped.formatReplies == true)
        assertEquals("each", mapped.replyProcessingMode?.value)
        assertEquals("all-allowed", mapped.memoryCollectionScope?.value)
        assertEquals("qwen3:1.7b", mapped.rules[value.rules.keys.single()]?.model)
        assertTrue(mapped.rules[value.rules.keys.single()]?.enabled == true)
    }

    @Test
    fun `custom writing style inputs retain names emoji and instructions`() {
        val style = WritingStyle("custom-kind", "Kind", "🙂", "Be warm and brief")
        val mapped = SettingsUpdate(customStyles = listOf(style)).toDomain()

        assertEquals("custom-kind", mapped.customStyles?.single()?.id)
        assertEquals("Kind", mapped.customStyles?.single()?.name)
        assertEquals("🙂", mapped.customStyles?.single()?.emoji)
        assertEquals("Be warm and brief", mapped.customStyles?.single()?.instructions)
    }
}
