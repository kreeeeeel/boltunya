package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.VoiceReplyMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceReplyPolicyTest {
    private val policy = VoiceReplyPolicy()

    @Test
    fun `speech is opt in and voice on voice does not include music or text`() {
        assertFalse(AppSettings().voiceInputEnabled)
        assertFalse(policy.wantsVoice(AppSettings(), "voice", 0))
        val mirror = AppSettings(voiceReplyMode = VoiceReplyMode.MIRROR)
        assertTrue(policy.wantsVoice(mirror, "voice", 99))
        assertFalse(policy.wantsVoice(mirror, "audio", 0))
        assertFalse(policy.wantsVoice(mirror, "text", 0))
        assertTrue(policy.wantsVoice(AppSettings(voiceReplyMode = VoiceReplyMode.ALWAYS), "text", 99))
    }

    @Test
    fun `probability boundaries are exact without overriding explicit text or voice choice`() {
        val random = AppSettings(voiceReplyMode = VoiceReplyMode.RANDOM, voiceReplyProbability = 50)
        assertTrue(policy.wantsVoice(random, "text", 49))
        assertFalse(policy.wantsVoice(random, "voice", 50))
        assertFalse(policy.wantsVoice(random.copy(voiceReplyProbability = 0), "voice", 0))
        assertTrue(policy.wantsVoice(random.copy(voiceReplyProbability = 100), "text", 99))
    }
}
