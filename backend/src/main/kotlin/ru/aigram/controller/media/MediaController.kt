package ru.aigram.controller.media

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.media.MediaContentService
import ru.aigram.component.media.MediaSendingComponent
import ru.aigram.component.media.UploadComponent
import ru.aigram.contract.api.MediaApi
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.StickerCatalog
import ru.aigram.contract.dto.StickerItem
import ru.aigram.contract.dto.UploadedFile
import ru.aigram.transformer.media.MediaTransformer

@RestController
class MediaController(
    private val content: MediaContentService,
    private val sending: MediaSendingComponent,
    private val uploads: UploadComponent,
    private val transformer: MediaTransformer,
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
        ResponseEntity.ok(sending.stickerSet(id).stream().map(transformer::toContract).toList())

    override fun stickers(): ResponseEntity<StickerCatalog> =
        ResponseEntity.ok(transformer.toContract(sending.catalog()))

    override fun uploadFile(xFileName: String, body: ByteArray): ResponseEntity<UploadedFile> = ResponseEntity.ok(
        transformer.toContract(
            uploads.create(xFileName, request.contentType.orEmpty(), body, request.contentLengthLong),
        ),
    )
}
