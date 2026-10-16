package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.boltunya.domain.CacheStatistics

class TelegramCacheStorage @Inject constructor(
    private val client: StorageClient,
    private val media: TelegramMediaStore,
    private val prefetch: MediaPrefetcher,
) {
    fun statistics(): CacheStatistics = map(client.execute(TdApi.GetStorageStatistics(0)))

    fun clear(): CacheStatistics {
        prefetch.cancelPending()
        val current = client.execute(TdApi.GetStorageStatistics(0))
        val types = current.byChat.orEmpty().flatMap {
            it.byFileType.orEmpty().toList()
        }.map { it.fileType }.distinctBy { it.constructor }.toTypedArray()
        if (types.isEmpty()) return map(current)
        val result = client.execute(TdApi.OptimizeStorage(0, 0, 0, 0, types, longArrayOf(), longArrayOf(), false, 0))
        media.invalidate()
        return map(result)
    }

    internal fun map(statistics: TdApi.StorageStatistics): CacheStatistics {
        val types = statistics.byChat.orEmpty().flatMap { it.byFileType.orEmpty().toList() }
        val photo = types.filter {
            it.fileType is TdApi.FileTypePhoto || it.fileType is TdApi.FileTypeProfilePhoto ||
                it.fileType is TdApi.FileTypeThumbnail ||
                it.fileType is TdApi.FileTypeSticker ||
                it.fileType is TdApi.FileTypeWallpaper
        }.sumOf { it.size }
        val video = types.filter {
            it.fileType is TdApi.FileTypeVideo || it.fileType is TdApi.FileTypeVideoNote ||
                it.fileType is TdApi.FileTypeAnimation
        }.sumOf { it.size }
        val audio = types.filter {
            it.fileType is TdApi.FileTypeAudio || it.fileType is TdApi.FileTypeVoiceNote
        }.sumOf { it.size }
        return CacheStatistics(
            statistics.size,
            statistics.count.toLong(),
            photo,
            video,
            audio,
            (
                statistics.size - photo -
                    video -
                    audio
                ).coerceAtLeast(0),
        )
    }
}
