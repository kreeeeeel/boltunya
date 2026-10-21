package ru.aigram.component.speech

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.PreviewJob
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.speech.SpeechAssetCatalog
import ru.aigram.speech.reply.VoiceReplyService
import ru.aigram.telegram.TelegramGateway

private const val MAX_PREVIEW_CHARACTERS = 4096

@Component
class SpeechPreviewComponent(
    private val replies: ReplyCoordinator,
    private val telegram: TelegramGateway,
    private val catalog: SpeechAssetCatalog,
    private val voices: VoiceReplyService,
    private val resources: ResourceCatalog,
) {
    fun enqueue(text: String, modelId: String, speed: Double): PreviewJob {
        if (text.isBlank() || text.length > MAX_PREVIEW_CHARACTERS || !speed.isFinite() || speed !in 0.5..2.0) {
            throw InvalidSpeechRequestException()
        }
        val status = telegram.status()
        catalog.model(modelId, SpeechAssetKind.SYNTHESIS)
        return replies.enqueueToolDraft(resources.text("speech/preview-title.txt"), modelId, text) {
            voices.sample(text, modelId, speed, status.userId)
        }
    }
}
