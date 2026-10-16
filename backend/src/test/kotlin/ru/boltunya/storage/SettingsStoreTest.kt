package ru.boltunya.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ChatRule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `persists named styles and resolves their instructions without modifying chat rules`() {
        val store = SettingsStore(directory)
        val preset = ru.boltunya.domain.WritingStyle("friends", "Друзья", "🙂", "Коротко и без официоза")
        store.update { it.copy(customStyles = listOf(preset), ignoreAssistantTasks = false) }
        val rule =
            ChatRule(style = "custom", styleId = preset.id, instructions = "Не обещай встреч", useDefaultModel = false)
        store.saveRule("42", rule)
        val restored = SettingsStore(directory)
        assertEquals(rule, restored.rule("42"))
        assertEquals("Коротко и без официоза\nНе обещай встреч", restored.resolve(rule).instructions)
        assertFalse(restored.get().ignoreAssistantTasks)
        restored.update { it.copy(customStyles = emptyList()) }
        assertEquals(rule, restored.resolve(rule))
    }

    @Test
    fun `persists contact settings and always pauses on restart`() {
        val store = SettingsStore(directory)
        store.saveRule("42", ChatRule(enabled = true, contextMessages = 44, style = "warm"))
        store.update { it.copy(paused = false, globalPrompt = "Без обещаний") }
        assertFalse(store.get().paused)
        val restored = SettingsStore(directory)
        assertTrue(restored.get().paused)
        assertEquals("Без обещаний", restored.get().globalPrompt)
        assertEquals(44, restored.rule("42").contextMessages)
        assertEquals("warm", restored.rule("42").style)
        assertTrue(restored.rule("42").enabled)
        assertFalse(restored.rule("unknown").enabled)
    }

    @Test
    fun `legacy rules allow internet and per chat opt out survives restart`() {
        Files.writeString(directory.resolve("settings.json"), """{"rules":{"42":{"enabled":true}}}""")
        val store = SettingsStore(directory)
        assertTrue(store.rule("42").internetAccess)
        store.saveRule("42", store.rule("42").copy(internetAccess = false))
        assertFalse(SettingsStore(directory).rule("42").internetAccess)
    }

    @Test
    fun `global model changes follow inherited rules and preserve explicit overrides after restart`() {
        val store = SettingsStore(directory)
        store.saveRule("inherited", ChatRule(model = "qwen3:1.7b", style = "warm"))
        store.saveRule("explicit", ChatRule(model = "qwen3:4b", useDefaultModel = false))
        store.update { it.copy(defaultModel = "deepseek-r1:8b") }
        assertEquals("deepseek-r1:8b", store.rule("inherited").model)
        assertEquals("deepseek-r1:8b", store.rule("new").model)
        assertEquals("warm", store.rule("inherited").style)
        assertEquals("qwen3:4b", store.rule("explicit").model)
        assertEquals("deepseek-r1:8b", SettingsStore(directory).rule("inherited").model)
        assertEquals("qwen3:4b", SettingsStore(directory).rule("explicit").model)
        assertEquals("deepseek-r1:8b", store.resolve(ChatRule(model = "qwen3:1.7b")).model)
    }

    @Test
    fun `persists desktop options and resumes an opted in assistant on every launch`() {
        val store = SettingsStore(directory)
        store.update {
            it.copy(
                replyDelaySeconds = 8,
                replyIntervalSeconds = 12,
                showTyping = false,
                closeToTray = false,
                launchAtLogin = true,
                resumeOnStartup = true,
            )
        }
        val regular = SettingsStore(directory)
        assertFalse(regular.get().paused)
        assertEquals(8, regular.get().replyDelaySeconds)
        assertEquals(12, regular.get().replyIntervalSeconds)
        assertFalse(regular.get().showTyping)
        assertFalse(regular.get().closeToTray)
        assertTrue(regular.get().launchAtLogin)
        assertFalse(SettingsStore(directory).get().paused)
        store.update { it.copy(resumeOnStartup = false) }
        assertTrue(SettingsStore(directory).get().paused)
    }
}
