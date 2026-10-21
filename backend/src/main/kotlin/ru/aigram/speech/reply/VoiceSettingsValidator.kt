package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.domain.settings.SettingsUpdate
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.InvalidSpeechAssetException
import ru.aigram.speech.SpeechAssetCatalog

@Component
class VoiceSettingsValidator(private val catalog: SpeechAssetCatalog) {
    fun validate(update: SettingsUpdate) {
        val assets = catalog.list()
        if (update.voiceRecognitionModel != null &&
            assets.stream().noneMatch {
                it.id == update.voiceRecognitionModel && it.kind == SpeechAssetKind.RECOGNITION
            }
        ) {
            throw InvalidSpeechAssetException()
        }

        if (update.voiceSynthesisModel != null &&
            assets.stream().noneMatch { it.id == update.voiceSynthesisModel && it.kind == SpeechAssetKind.SYNTHESIS }
        ) {
            throw InvalidSpeechAssetException()
        }
    }
}
