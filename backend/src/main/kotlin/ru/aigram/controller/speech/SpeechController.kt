package ru.aigram.controller.speech

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.media.MediaContentService
import ru.aigram.component.speech.SpeechPreviewComponent
import ru.aigram.component.speech.SpeechReplyComponent
import ru.aigram.contract.api.SpeechApi
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.PreviewJob
import ru.aigram.contract.dto.SpeechModelImportRequest
import ru.aigram.contract.dto.SpeechPreviewRequest
import ru.aigram.contract.dto.SpeechState
import ru.aigram.contract.dto.VoiceDraftRequest
import ru.aigram.contract.dto.VoiceSendRequest
import ru.aigram.speech.SpeechAssetCatalog
import ru.aigram.speech.SpeechModelImportService
import ru.aigram.telegram.TelegramGateway
import ru.aigram.transformer.models.toContract
import ru.aigram.transformer.speech.toContract

@RestController
class SpeechController(
    private val catalog: SpeechAssetCatalog,
    private val replies: SpeechReplyComponent,
    private val previews: SpeechPreviewComponent,
    private val imports: SpeechModelImportService,
    private val telegram: TelegramGateway,
    private val media: MediaContentService,
    private val request: HttpServletRequest,
) : SpeechApi {
    override fun previewSpeech(speechPreviewRequest: SpeechPreviewRequest): ResponseEntity<PreviewJob> =
        ResponseEntity.ok(
            previews.enqueue(
                speechPreviewRequest.text,
                speechPreviewRequest.modelId,
                speechPreviewRequest.speed.toDouble(),
            ).toContract(),
        )

    override fun importSpeechModel(speechModelImportRequest: SpeechModelImportRequest): ResponseEntity<SpeechState> =
        ResponseEntity.ok(
            imports.importModel(
                telegram.status().userId,
                speechModelImportRequest.uploadId,
                speechModelImportRequest.title,
                speechModelImportRequest.language.orEmpty(),
                speechModelImportRequest.license.orEmpty(),
            ).toContract(),
        )

    override fun speechState(): ResponseEntity<SpeechState> = ResponseEntity.ok(catalog.state().toContract())

    override fun installSpeechAsset(assetId: String): ResponseEntity<Ok> {
        catalog.install(assetId)
        return ResponseEntity.ok(Ok(true))
    }

    override fun cancelSpeechAsset(assetId: String): ResponseEntity<Ok> {
        catalog.cancel(assetId)
        return ResponseEntity.ok(Ok(true))
    }

    override fun enqueueVoiceDraft(voiceDraftRequest: VoiceDraftRequest): ResponseEntity<PreviewJob> =
        ResponseEntity.ok(replies.enqueue(voiceDraftRequest.chatId, voiceDraftRequest.text).toContract())

    override fun speechAudio(voiceId: String): ResponseEntity<Resource> = media.respond(replies.audio(voiceId), request)

    override fun sendVoiceDraft(chatId: String, voiceSendRequest: VoiceSendRequest): ResponseEntity<Ok> {
        replies.send(chatId, voiceSendRequest.voiceId, voiceSendRequest.replyToId, voiceSendRequest.topicId)
        return ResponseEntity.ok(Ok(true))
    }
}
