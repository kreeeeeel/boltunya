package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.VoiceReplyMode
import java.util.concurrent.ThreadLocalRandom

@Component
class VoiceReplyPolicy {
    fun wantsVoice(
        settings: AppSettings,
        inputKind: String,
        draw: Int = ThreadLocalRandom.current().nextInt(100),
    ): Boolean = when (settings.voiceReplyMode) {
        VoiceReplyMode.TEXT -> false
        VoiceReplyMode.ALWAYS -> true
        VoiceReplyMode.RANDOM -> draw < settings.voiceReplyProbability
        VoiceReplyMode.MIRROR -> inputKind == "voice"
    }
}
