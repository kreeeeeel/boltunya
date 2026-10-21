package ru.aigram.transformer.media

import org.springframework.stereotype.Component
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.domain.media.SendMediaRequest
import ru.aigram.domain.media.StickerCatalog
import ru.aigram.domain.media.StickerItem
import ru.aigram.domain.media.StickerSet
import ru.aigram.domain.media.UploadedFile
import java.util.UUID
import ru.aigram.contract.dto.MediaAttachment as ContractMediaAttachment
import ru.aigram.contract.dto.SendMediaRequest as ContractSendMediaRequest
import ru.aigram.contract.dto.StickerCatalog as ContractStickerCatalog
import ru.aigram.contract.dto.StickerItem as ContractStickerItem
import ru.aigram.contract.dto.StickerSet as ContractStickerSet
import ru.aigram.contract.dto.UploadedFile as ContractUploadedFile

@Component
class MediaTransformer {
    fun toContract(value: MediaAttachment): ContractMediaAttachment = ContractMediaAttachment(
        fileId = value.fileId,
        mimeType = value.mimeType,
        fileName = value.fileName,
        width = value.width,
        height = value.height,
        thumbnailFileId = value.thumbnailFileId,
        propertySize = value.size,
        duration = value.duration,
        waveform = value.waveform,
        autoDownload = value.autoDownload,
        downloaded = value.downloaded,
        cacheVersion = value.cacheVersion,
        uniqueId = value.uniqueId,
    )

    fun toContract(value: UploadedFile): ContractUploadedFile = ContractUploadedFile(
        id = UUID.fromString(value.id),
        fileName = value.fileName,
        mimeType = value.mimeType,
        propertySize = value.size,
    )

    fun toContract(value: StickerItem): ContractStickerItem = ContractStickerItem(
        id = value.id,
        emoji = value.emoji,
        setId = value.setId,
        media = toContract(value.media),
        width = value.width,
        height = value.height,
    )

    fun toContract(value: StickerSet): ContractStickerSet = ContractStickerSet(
        id = value.id,
        title = value.title,
        name = value.name,
        count = value.count,
        thumbnail = value.thumbnail?.let(::toContract),
    )

    fun toContract(value: StickerCatalog): ContractStickerCatalog = ContractStickerCatalog(
        recent = value.recent.stream().map(::toContract).toList(),
        favorites = value.favorites.stream().map(::toContract).toList(),
        sets = value.sets.stream().map(::toContract).toList(),
    )

    fun toDomain(value: ContractSendMediaRequest): SendMediaRequest = SendMediaRequest(
        uploadIds = value.uploadIds.stream().map(UUID::toString).toList(),
        kind = OutgoingMediaKind.valueOf(value.kind.name),
        caption = value.caption.orEmpty(),
    )
}
