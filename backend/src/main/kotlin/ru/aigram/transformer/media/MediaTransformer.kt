package ru.aigram.transformer.media

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

fun MediaAttachment.toContract(): ContractMediaAttachment = ContractMediaAttachment(
    fileId = fileId,
    mimeType = mimeType,
    fileName = fileName,
    width = width,
    height = height,
    thumbnailFileId = thumbnailFileId,
    propertySize = size,
    duration = duration,
    waveform = waveform,
    autoDownload = autoDownload,
    downloaded = downloaded,
    cacheVersion = cacheVersion,
    uniqueId = uniqueId,
)

fun UploadedFile.toContract(): ContractUploadedFile = ContractUploadedFile(
    id = UUID.fromString(id),
    fileName = fileName,
    mimeType = mimeType,
    propertySize = size,
)

fun StickerItem.toContract(): ContractStickerItem = ContractStickerItem(
    id = id,
    emoji = emoji,
    setId = setId,
    media = media.toContract(),
    width = width,
    height = height,
)

fun StickerSet.toContract(): ContractStickerSet = ContractStickerSet(
    id = id,
    title = title,
    name = name,
    count = count,
    thumbnail = thumbnail?.toContract(),
)

fun StickerCatalog.toContract(): ContractStickerCatalog = ContractStickerCatalog(
    recent = recent.stream().map { it.toContract() }.toList(),
    favorites = favorites.stream().map { it.toContract() }.toList(),
    sets = sets.stream().map { it.toContract() }.toList(),
)

fun ContractSendMediaRequest.toDomain(): SendMediaRequest = SendMediaRequest(
    uploadIds = uploadIds.stream().map(UUID::toString).toList(),
    kind = OutgoingMediaKind.valueOf(kind.name),
    caption = caption,
)
