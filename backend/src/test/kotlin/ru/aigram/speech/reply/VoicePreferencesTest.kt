package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePreferencesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `old chat settings inherit every global voice preference after reload`() {
        Files.writeString(
            directory.resolve("settings.json"),
            """{"voiceInputEnabled":true,"voiceReplyMode":"mirror","voiceReplyProbability":73,
                |"voiceSynthesisModel":"piper-en-amy","voiceSpeechRate":1.4,"rules":{"42":{"enabled":true}}}
            """.trimMargin(),
        )
        val settings = SettingsStore(directory)
        val rule = settings.rule("42")
        val voice = settings.get().voicePreferences(rule)

        assertEquals(VoiceSettingsMode.INHERIT, rule.voiceSettingsMode)
        assertTrue(voice.inputEnabled)
        assertEquals(VoiceReplyMode.MIRROR, voice.replyMode)
        assertEquals(73, voice.replyProbability)
        assertEquals("whisper-tiny", voice.recognitionModel)
        assertEquals("piper-en-amy", voice.synthesisModel)
        assertEquals(1.4, voice.speechRate)
        assertEquals(SpeechComputeDevice.CPU, voice.conversionDevice)
    }

    @Test
    fun `RVC device stays global even for chat overrides and persists without changing speech preferences`() {
        val settings = SettingsStore(directory)
        val rule = ChatRule(voiceSettingsMode = VoiceSettingsMode.OVERRIDE, voiceSpeechRate = 1.3)
        settings.saveRule("42", rule)
        val effectiveRule = settings.rule("42")
        val voice = settings.get().voicePreferences(effectiveRule)

        settings.update { it.copy(voiceConversionDevice = SpeechComputeDevice.CUDA) }
        settings.update { it.copy(showTyping = false) }
        val restored = SettingsStore(directory)
        val restoredRule = restored.rule("42")

        assertEquals(effectiveRule, settings.rule("42"))
        assertEquals(effectiveRule, restoredRule)
        assertEquals(
            voice.copy(conversionDevice = SpeechComputeDevice.CUDA),
            restored.get().voicePreferences(restoredRule),
        )
        assertEquals(SpeechComputeDevice.CUDA, restored.get().voicePreferences(restoredRule).conversionDevice)
        assertEquals(SpeechComputeDevice.CUDA, restored.get().voicePreferences(ChatRule()).conversionDevice)
        assertEquals(1.3, restored.get().voicePreferences(restoredRule).speechRate)
    }

    @Test
    fun `one chat override is independent of other chats and all subsequent global voice changes`() {
        val global = AppSettings(voiceInputEnabled = true, voiceReplyMode = VoiceReplyMode.ALWAYS)
        val rule = ChatRule(
            voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
            voiceInputEnabled = false,
            voiceReplyMode = VoiceReplyMode.RANDOM,
            voiceReplyProbability = 23,
            voiceRecognitionModel = "whisper-tiny",
            voiceSynthesisModel = "piper-en-amy",
            voiceSpeechRate = 1.3,
        )
        val before = global.voicePreferences(rule)
        val changed = global.copy(
            voiceInputEnabled = false,
            voiceReplyMode = VoiceReplyMode.TEXT,
            voiceReplyProbability = 100,
            voiceRecognitionModel = "future-recognizer",
            voiceSynthesisModel = "piper-ru-dmitri",
            voiceSpeechRate = 0.5,
        )

        assertEquals(before, changed.voicePreferences(rule))
        assertFalse(before.inputEnabled)
        assertEquals(VoiceReplyMode.RANDOM, before.replyMode)
        assertEquals(23, before.replyProbability)
        assertEquals("whisper-tiny", before.recognitionModel)
        assertEquals("piper-en-amy", before.synthesisModel)
        assertEquals(1.3, before.speechRate)
        assertEquals(VoiceReplyMode.ALWAYS, global.voicePreferences(ChatRule()).replyMode)
        assertEquals(VoiceReplyMode.TEXT, changed.voicePreferences(ChatRule()).replyMode)
        assertEquals(
            changed.voicePreferences(ChatRule()),
            changed.voicePreferences(
                rule.copy(
                    voiceSettingsMode = VoiceSettingsMode.INHERIT,
                ),
            ),
        )
    }

    @Test
    fun `chat preference persistence retains unrelated chat and application settings`() {
        val settings = SettingsStore(directory)
        val rule = ChatRule(
            enabled = true,
            instructions = "Synthetic chat instructions",
            voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
            voiceInputEnabled = true,
            voiceReplyMode = VoiceReplyMode.MIRROR,
            voiceSynthesisModel = "piper-en-amy",
            voiceSpeechRate = 1.6,
        )
        settings.saveRule("42", rule)
        settings.saveRule("43", ChatRule(instructions = "Another chat"))
        settings.update { it.copy(showTyping = false) }
        val restored = SettingsStore(directory)

        assertEquals(rule, restored.get().rules["42"])
        assertEquals("Another chat", restored.rule("43").instructions)
        assertFalse(restored.get().showTyping)
        assertEquals("piper-ru-irina", restored.get().voiceSynthesisModel)
        assertEquals(1.6, restored.get().voicePreferences(restored.rule("42")).speechRate)
    }
}
