package ru.aigram.controller.media

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.media.MediaContentService
import ru.aigram.component.media.UploadComponent
import ru.aigram.contract.api.MediaApi
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.StickerCatalog
import ru.aigram.contract.dto.StickerItem
import ru.aigram.contract.dto.UploadedFile
import ru.aigram.telegram.TelegramGateway
import ru.aigram.transformer.media.toContract

@RestController
class MediaController(
    private val content: MediaContentService,
    private val telegram: TelegramGateway,
    private val uploads: UploadComponent,
    private val request: HttpServletRequest,
) : MediaApi {
    override fun deleteUpload(id: String): ResponseEntity<Ok> {
        uploads.delete(id)
        return ResponseEntity.ok(Ok(true))
    }

    override fun downloadMedia(fileId: Int): ResponseEntity<Ok> {
        uploads.download(fileId)
        return ResponseEntity.ok(Ok(true))
    }

    override fun media(fileId: Int): ResponseEntity<Resource> = content.respond(fileId, request)

    override fun stickerSet(id: String): ResponseEntity<List<StickerItem>> =
        ResponseEntity.ok(telegram.stickerSet(id).stream().map { it.toContract() }.toList())

    override fun stickers(): ResponseEntity<StickerCatalog> = ResponseEntity.ok(telegram.stickers().toContract())

    override fun uploadFile(xFileName: String, body: ByteArray): ResponseEntity<UploadedFile> = ResponseEntity.ok(
        uploads.create(xFileName, request.contentType.orEmpty(), body, request.contentLengthLong).toContract(),
    )
}
