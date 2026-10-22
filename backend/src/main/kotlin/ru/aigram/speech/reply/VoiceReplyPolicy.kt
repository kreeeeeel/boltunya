package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.domain.speech.VoicePreferences
import java.util.concurrent.ThreadLocalRandom

@Component
class VoiceReplyPolicy {
    fun wantsVoice(
        settings: VoicePreferences,
        inputKind: String,
        draw: Int = ThreadLocalRandom.current().nextInt(100),
    ): Boolean = when (settings.replyMode) {
        VoiceReplyMode.TEXT -> false
        VoiceReplyMode.ALWAYS -> true
        VoiceReplyMode.RANDOM -> draw < settings.replyProbability
        VoiceReplyMode.MIRROR -> inputKind == "voice"
    }
}
